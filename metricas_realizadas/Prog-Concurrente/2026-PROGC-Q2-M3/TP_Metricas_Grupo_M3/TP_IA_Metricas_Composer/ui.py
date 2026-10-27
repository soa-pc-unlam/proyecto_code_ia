"""Interfaz gráfica Tkinter para el sistema de reservas concurrente."""

from __future__ import annotations

import queue
import tkinter as tk
from tkinter import messagebox, ttk
from typing import Callable, Optional

from cinema import Cinema
from models import EstadoAsiento, ResultadoProcesamiento, ResultadoSolicitud
from reservation_system import ReservationSystem
from simulation import (
    EscenarioSimulacion,
    ParametrosSimulacion,
    ejecutar_prueba_conflicto,
    ejecutar_simulacion,
)


class CinemaApp:
    def __init__(self, cinema: Cinema, sistema: ReservationSystem) -> None:
        self.cinema = cinema
        self.sistema = sistema
        self.root = tk.Tk()
        self.root.title("Sistema concurrente de reservas - Cine")
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

        self._asiento_seleccionado: Optional[str] = None
        self._seat_buttons: dict[str, tk.Button] = {}
        self._seat_map_key: Optional[tuple[str, str]] = None
        self._simulacion_activa = False

        self._build_ui()
        self._poll_results()
        self._refresh_stats()
        self._refresh_seat_map()

    def _build_ui(self) -> None:
        main = ttk.Frame(self.root, padding=8)
        main.grid(row=0, column=0, sticky="nsew")
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)

        sel = ttk.LabelFrame(main, text="Selección", padding=8)
        sel.grid(row=0, column=0, sticky="ew", padx=4, pady=4)

        ttk.Label(sel, text="Película:").grid(row=0, column=0, sticky="w")
        self.var_pelicula = tk.StringVar()
        pelis = [f"{p.identificador}|{p.titulo}" for p in self.cinema.listar_peliculas()]
        self.combo_pelicula = ttk.Combobox(
            sel, textvariable=self.var_pelicula, values=pelis, state="readonly", width=40
        )
        self.combo_pelicula.grid(row=0, column=1, padx=4)
        if pelis:
            self.combo_pelicula.current(0)
        self.combo_pelicula.bind("<<ComboboxSelected>>", lambda _: self._on_pelicula_change())

        ttk.Label(sel, text="Función:").grid(row=1, column=0, sticky="w")
        self.var_funcion = tk.StringVar()
        self.combo_funcion = ttk.Combobox(
            sel, textvariable=self.var_funcion, state="readonly", width=40
        )
        self.combo_funcion.grid(row=1, column=1, padx=4)
        self.combo_funcion.bind("<<ComboboxSelected>>", lambda _: self._refresh_seat_map())

        ttk.Label(sel, text="Usuario:").grid(row=2, column=0, sticky="w")
        self.var_usuario = tk.StringVar(value="UsuarioManual")
        ttk.Entry(sel, textvariable=self.var_usuario, width=42).grid(row=2, column=1, padx=4)

        ttk.Button(sel, text="Reservar asiento seleccionado", command=self._reservar_manual).grid(
            row=3, column=0, columnspan=2, pady=6
        )

        mapa_frame = ttk.LabelFrame(main, text="Mapa de asientos", padding=8)
        mapa_frame.grid(row=1, column=0, sticky="nsew", padx=4, pady=4)
        self.seats_container = ttk.Frame(mapa_frame)
        self.seats_container.grid(row=0, column=0)

        estado = ttk.LabelFrame(main, text="Estado del sistema", padding=8)
        estado.grid(row=0, column=1, rowspan=2, sticky="nsew", padx=4, pady=4)
        self.lbl_stats = ttk.Label(estado, text="", justify="left")
        self.lbl_stats.grid(row=0, column=0, sticky="nw")

        sim = ttk.LabelFrame(estado, text="Simulación", padding=6)
        sim.grid(row=1, column=0, sticky="ew", pady=8)

        self.var_prod = tk.IntVar(value=10)
        self.var_sol = tk.IntVar(value=5)
        self.var_workers = tk.IntVar(value=4)
        ttk.Label(sim, text="Productores:").grid(row=0, column=0, sticky="w")
        ttk.Spinbox(sim, from_=1, to=100, textvariable=self.var_prod, width=8).grid(
            row=0, column=1
        )
        ttk.Label(sim, text="Solicitudes/usuario:").grid(row=1, column=0, sticky="w")
        ttk.Spinbox(sim, from_=1, to=500, textvariable=self.var_sol, width=8).grid(
            row=1, column=1
        )
        ttk.Label(sim, text="Workers:").grid(row=2, column=0, sticky="w")
        ttk.Spinbox(sim, from_=1, to=32, textvariable=self.var_workers, width=8).grid(
            row=2, column=1
        )

        self.var_escenario = tk.StringVar(value=EscenarioSimulacion.DISTRIBUIDA)
        ttk.Radiobutton(
            sim,
            text="Carga distribuida",
            variable=self.var_escenario,
            value=EscenarioSimulacion.DISTRIBUIDA,
        ).grid(row=3, column=0, columnspan=2, sticky="w")
        ttk.Radiobutton(
            sim,
            text="Alta contención (mismo asiento)",
            variable=self.var_escenario,
            value=EscenarioSimulacion.ALTA_CONTENCION,
        ).grid(row=4, column=0, columnspan=2, sticky="w")

        ttk.Button(sim, text="Iniciar simulación", command=self._iniciar_simulacion).grid(
            row=5, column=0, columnspan=2, pady=4
        )
        ttk.Button(sim, text="Prueba de conflicto", command=self._prueba_conflicto).grid(
            row=6, column=0, columnspan=2, pady=4
        )

        self.lbl_sim_result = ttk.Label(sim, text="", wraplength=280, justify="left")
        self.lbl_sim_result.grid(row=7, column=0, columnspan=2)

        hist = ttk.LabelFrame(main, text="Historial (procesadas)", padding=8)
        hist.grid(row=2, column=0, columnspan=2, sticky="nsew", padx=4, pady=4)
        self.historial = tk.Listbox(hist, height=12, width=100)
        scroll = ttk.Scrollbar(hist, orient="vertical", command=self.historial.yview)
        self.historial.configure(yscrollcommand=scroll.set)
        self.historial.grid(row=0, column=0, sticky="nsew")
        scroll.grid(row=0, column=1, sticky="ns")
        hist.rowconfigure(0, weight=1)
        hist.columnconfigure(0, weight=1)

        main.rowconfigure(2, weight=1)
        main.columnconfigure(0, weight=1)

        self._on_pelicula_change()

    def _parse_pelicula(self) -> tuple[str, str] | None:
        val = self.var_pelicula.get()
        if not val or "|" not in val:
            return None
        pid, titulo = val.split("|", 1)
        return pid, titulo

    def _parse_funcion(self) -> tuple[str, str] | None:
        val = self.var_funcion.get()
        if not val or "|" not in val:
            return None
        fid, horario = val.split("|", 1)
        return fid, horario

    def _on_pelicula_change(self) -> None:
        parsed = self._parse_pelicula()
        if not parsed:
            return
        pid, _ = parsed
        peli = self.cinema.obtener_pelicula(pid)
        if not peli:
            return
        vals = [f"{fid}|{f.horario}" for fid, f in peli.funciones.items()]
        self.combo_funcion["values"] = vals
        if vals:
            self.combo_funcion.current(0)
        self._refresh_seat_map()

    def _funcion_actual_key(self) -> Optional[tuple[str, str]]:
        parsed_p = self._parse_pelicula()
        parsed_f = self._parse_funcion()
        if not parsed_p or not parsed_f:
            return None
        return parsed_p[0], parsed_f[0]

    def _color_asiento(self, estado: EstadoAsiento) -> str:
        return "#4caf50" if estado == EstadoAsiento.DISPONIBLE else "#e53935"

    def _rebuild_seat_map(self) -> None:
        seleccion_previa = self._asiento_seleccionado
        for w in self.seats_container.winfo_children():
            w.destroy()
        self._seat_buttons.clear()
        self._asiento_seleccionado = None

        key = self._funcion_actual_key()
        self._seat_map_key = key
        if key is None:
            return
        pid, fid = key
        estados = self.cinema.estado_asientos(pid, fid)
        if not estados:
            return

        filas: dict[str, list[str]] = {}
        for etiqueta in sorted(estados.keys(), key=lambda x: (x[0], int(x[1:]))):
            filas.setdefault(etiqueta[0], []).append(etiqueta)

        row = 0
        for letra in sorted(filas.keys()):
            col = 0
            for etiqueta in filas[letra]:
                estado = estados[etiqueta]
                bg = self._color_asiento(estado)
                btn = tk.Button(
                    self.seats_container,
                    text=etiqueta,
                    width=4,
                    bg=bg,
                    fg="white",
                    activebackground=bg,
                    command=lambda e=etiqueta: self._select_seat(e),
                )
                btn.grid(row=row, column=col, padx=2, pady=2)
                self._seat_buttons[etiqueta] = btn
                col += 1
            row += 1

        if seleccion_previa and seleccion_previa in self._seat_buttons:
            self._select_seat(seleccion_previa)

    def _update_seat_colors(self) -> None:
        key = self._funcion_actual_key()
        if key is None or key != self._seat_map_key or not self._seat_buttons:
            self._rebuild_seat_map()
            return
        pid, fid = key
        estados = self.cinema.estado_asientos(pid, fid)
        for etiqueta, btn in self._seat_buttons.items():
            estado = estados.get(etiqueta)
            if estado is None:
                continue
            bg = self._color_asiento(estado)
            if btn.cget("bg") != bg:
                btn.configure(bg=bg, activebackground=bg)

    def _refresh_seat_map(self) -> None:
        key = self._funcion_actual_key()
        if key != self._seat_map_key or not self._seat_buttons:
            self._rebuild_seat_map()
        else:
            self._update_seat_colors()

    def _select_seat(self, etiqueta: str) -> None:
        self._asiento_seleccionado = etiqueta
        for e, btn in self._seat_buttons.items():
            if e == etiqueta:
                btn.configure(relief=tk.SUNKEN)
            else:
                btn.configure(relief=tk.RAISED)

    def _reservar_manual(self) -> None:
        if not self._asiento_seleccionado:
            messagebox.showwarning("Reserva", "Seleccione un asiento en la grilla.")
            return
        parsed_p = self._parse_pelicula()
        parsed_f = self._parse_funcion()
        if not parsed_p or not parsed_f:
            return
        pid, ptitulo = parsed_p
        fid, fhor = parsed_f
        usuario = self.var_usuario.get().strip() or "UsuarioManual"
        sol = self.sistema.crear_solicitud(
            usuario, pid, ptitulo, fid, fhor, self._asiento_seleccionado
        )
        self.sistema.encolar(sol)
        self._refresh_stats()

    def _poll_results(self) -> None:
        hubo_resultados = False
        while True:
            try:
                proc: ResultadoProcesamiento = self.sistema.result_queue.get_nowait()
            except queue.Empty:
                break
            self._append_historial(proc)
            hubo_resultados = True
        if hubo_resultados:
            self._update_seat_colors()
        self._refresh_stats()
        self.root.after(100, self._poll_results)

    def _append_historial(self, proc: ResultadoProcesamiento) -> None:
        s = proc.solicitud
        res = s.resultado.value if isinstance(s.resultado, ResultadoSolicitud) else str(s.resultado)
        linea = (
            f"#{s.solicitud_id} | {s.usuario} | {s.pelicula_titulo} | "
            f"{s.funcion_horario} | {s.asiento} | {res}"
        )
        self.historial.insert(0, linea)

    def _refresh_stats(self) -> None:
        st = self.sistema.estadisticas()
        texto = (
            f"Pendientes en cola: {st['pendientes']}\n"
            f"Solicitudes generadas: {st['generadas']}\n"
            f"Solicitudes procesadas: {st['procesadas']}\n"
            f"Reservas exitosas: {st['exitosas']}\n"
            f"Solicitudes rechazadas: {st['rechazadas']}\n"
            f"Workers activos: {st['workers_activos']}"
        )
        self.lbl_stats.configure(text=texto)

    def _run_in_thread(self, fn: Callable[[], None]) -> None:
        import threading

        threading.Thread(target=fn, daemon=True).start()

    def _iniciar_simulacion(self) -> None:
        if self._simulacion_activa:
            messagebox.showinfo("Simulación", "Ya hay una simulación en ejecución.")
            return

        parsed_p = self._parse_pelicula()
        parsed_f = self._parse_funcion()
        if not parsed_p or not parsed_f:
            return
        pid, _ = parsed_p
        fid, _ = parsed_f

        params = ParametrosSimulacion(
            num_productores=self.var_prod.get(),
            solicitudes_por_productor=self.var_sol.get(),
            num_workers=self.var_workers.get(),
            escenario=self.var_escenario.get(),
            pelicula_id=pid,
            funcion_id=fid,
            asiento_objetivo=self._asiento_seleccionado or "A1",
        )

        def trabajo() -> None:
            self._simulacion_activa = True
            try:
                res = ejecutar_simulacion(
                    self.sistema,
                    self.cinema.listar_peliculas(),
                    params,
                )
                msg = (
                    f"Simulación finalizada\n"
                    f"Generadas: {res.solicitudes_generadas}\n"
                    f"Procesadas: {res.solicitudes_procesadas}\n"
                    f"Exitosas: {res.reservas_exitosas}\n"
                    f"Rechazadas: {res.solicitudes_rechazadas}\n"
                    f"Tiempo total: {res.tiempo_total_seg:.3f} s\n"
                    f"Tiempo prom/solicitud: {res.tiempo_promedio_seg:.6f} s\n"
                    f"Solicitudes/s: {res.solicitudes_por_segundo:.2f}"
                )
                self.root.after(0, lambda: self.lbl_sim_result.configure(text=msg))
            finally:
                self._simulacion_activa = False
                self.root.after(0, self._refresh_stats)
                self.root.after(0, self._refresh_seat_map)

        self._run_in_thread(trabajo)

    def _prueba_conflicto(self) -> None:
        if self._simulacion_activa:
            return
        parsed_p = self._parse_pelicula()
        parsed_f = self._parse_funcion()
        if not parsed_p or not parsed_f:
            return
        pid, _ = parsed_p
        fid, _ = parsed_f
        asiento = self._asiento_seleccionado or "A1"

        def trabajo() -> None:
            self._simulacion_activa = True
            try:
                res = ejecutar_prueba_conflicto(
                    self.sistema,
                    self.cinema,
                    self.cinema.listar_peliculas(),
                    pid,
                    fid,
                    asiento,
                    num_solicitudes=20,
                    num_workers=self.var_workers.get(),
                )
                msg = (
                    f"{res.detalle}\n"
                    f"Exitosas: {res.exitosas} | Rechazadas: {res.rechazadas} | Total: {res.total}"
                )
                self.root.after(0, lambda: self.lbl_sim_result.configure(text=msg))
                if not res.aprobada:
                    self.root.after(
                        0,
                        lambda: messagebox.showerror("Prueba de conflicto", res.detalle),
                    )
            finally:
                self._simulacion_activa = False
                self.root.after(0, self._refresh_seat_map)
                self.root.after(0, self._refresh_stats)

        self._run_in_thread(trabajo)

    def _on_close(self) -> None:
        self.sistema.shutdown()
        self.root.destroy()

    def run(self) -> None:
        self.root.mainloop()
