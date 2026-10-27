"""Interfaz gráfica Tkinter. Sólo el hilo principal modifica los widgets."""

from __future__ import annotations

import queue
import tkinter as tk
from tkinter import messagebox, ttk

from cinema import Cine
from models import EstadoAsiento, Funcion, Pelicula, ResultadoReserva, SolicitudReserva
from reservation_system import SistemaReservas
from simulation import CANTIDAD_MINIMA_CONFLICTO, ControladorSimulaciones, ResumenSimulacion

COLOR_DISPONIBLE = "#43a047"
COLOR_RESERVADO = "#e53935"
COLOR_SELECCIONADO = "#1e88e5"
COLOR_SELECCIONADO_RESERVADO = "#8e0000"

INTERVALO_REFRESCO_MS = 100
MAX_EVENTOS_POR_CICLO = 500
MAX_FILAS_HISTORIAL = 1000

ESCENARIO_DISTRIBUIDO = "distribuida"
ESCENARIO_CONTENCION = "contencion"
ESCENARIOS = (ESCENARIO_DISTRIBUIDO, ESCENARIO_CONTENCION)
RETARDO_INICIO_AUTOMATICO_MS = 1000


class AplicacionCine:
    def __init__(
        self,
        root: tk.Tk,
        cine: Cine,
        sistema: SistemaReservas,
        simulador: ControladorSimulaciones,
    ) -> None:
        self._root = root
        self._cine = cine
        self._sistema = sistema
        self._simulador = simulador

        self._funcion_actual = cine.peliculas[0].funciones[0]
        self._asiento_seleccionado: str | None = None
        self._botones_asientos: dict[str, tk.Button] = {}
        self._apariencia_asientos: dict[str, tuple[str, str]] = {}
        self._botones_simulacion: list[ttk.Button] = []
        self._simulacion_visible: bool | None = None
        self._after_id: str | None = None
        self._recursos_liberados = False
        self._salir_al_terminar = False

        root.title("Sistema concurrente de reservas de cine")
        root.minsize(1150, 760)
        root.columnconfigure(1, weight=1)
        root.rowconfigure(1, weight=1)

        self._construir_seleccion()
        panel_derecho = ttk.Frame(root)
        panel_derecho.grid(row=0, column=1, sticky="nsew", padx=(5, 10), pady=10)
        self._construir_estado(panel_derecho)
        self._construir_simulacion(panel_derecho)
        self._construir_resultados(panel_derecho)
        self._construir_historial()

        self._cargar_funciones(cine.peliculas[0])
        root.protocol("WM_DELETE_WINDOW", self.cerrar)
        self._refrescar()

    # ------------------------------------------------------------------ construcción

    def _construir_seleccion(self) -> None:
        marco = ttk.LabelFrame(self._root, text="Reserva manual", padding=10)
        marco.grid(row=0, column=0, sticky="nsew", padx=(10, 5), pady=10)
        marco.columnconfigure(1, weight=1)

        ttk.Label(marco, text="Película:").grid(row=0, column=0, sticky="w", pady=2)
        self._combo_pelicula = ttk.Combobox(
            marco, state="readonly", width=24, values=[p.titulo for p in self._cine.peliculas]
        )
        self._combo_pelicula.current(0)
        self._combo_pelicula.grid(row=0, column=1, sticky="ew", pady=2)
        self._combo_pelicula.bind("<<ComboboxSelected>>", self._al_cambiar_pelicula)

        ttk.Label(marco, text="Función:").grid(row=1, column=0, sticky="w", pady=2)
        self._combo_funcion = ttk.Combobox(marco, state="readonly", width=24)
        self._combo_funcion.grid(row=1, column=1, sticky="ew", pady=2)
        self._combo_funcion.bind("<<ComboboxSelected>>", self._al_cambiar_funcion)

        ttk.Label(marco, text="Usuario:").grid(row=2, column=0, sticky="w", pady=2)
        self._var_usuario = tk.StringVar(value="Usuario manual")
        ttk.Entry(marco, textvariable=self._var_usuario).grid(row=2, column=1, sticky="ew", pady=2)

        mapa = ttk.Frame(marco, padding=(0, 12))
        mapa.grid(row=3, column=0, columnspan=2)
        funcion = self._funcion_actual
        tk.Label(mapa, text="PANTALLA", bg="#424242", fg="white").grid(
            row=0, column=0, columnspan=funcion.asientos_por_fila, sticky="ew", pady=(0, 10)
        )
        for codigo in funcion.asientos:
            fila = funcion.filas.index(codigo[0]) + 1
            columna = int(codigo[1:]) - 1
            boton = tk.Button(
                mapa,
                text=codigo,
                width=4,
                fg="white",
                activeforeground="white",
                font=("Segoe UI", 9, "bold"),
                command=lambda c=codigo: self._seleccionar_asiento(c),
            )
            boton.grid(row=fila, column=columna, padx=2, pady=2)
            self._botones_asientos[codigo] = boton

        leyenda = ttk.Frame(marco)
        leyenda.grid(row=4, column=0, columnspan=2, pady=(0, 8))
        for texto, color in (
            ("Disponible", COLOR_DISPONIBLE),
            ("Reservado", COLOR_RESERVADO),
            ("Seleccionado", COLOR_SELECCIONADO),
        ):
            tk.Label(leyenda, bg=color, width=2).pack(side="left", padx=(8, 3))
            ttk.Label(leyenda, text=texto).pack(side="left")

        self._var_seleccion = tk.StringVar(value="Asiento seleccionado: ninguno")
        ttk.Label(marco, textvariable=self._var_seleccion).grid(row=5, column=0, columnspan=2, sticky="w")
        self._boton_reservar = ttk.Button(marco, text="Reservar", command=self._reservar)
        self._boton_reservar.grid(row=6, column=0, columnspan=2, sticky="ew", pady=6)
        self._var_mensaje = tk.StringVar()
        ttk.Label(marco, textvariable=self._var_mensaje, wraplength=280, foreground="#555555").grid(
            row=7, column=0, columnspan=2, sticky="w"
        )

    def _construir_estado(self, padre: ttk.Frame) -> None:
        marco = ttk.LabelFrame(padre, text="Estado del sistema", padding=10)
        marco.pack(fill="x", pady=(0, 5))
        campos = (
            ("pendientes", "Solicitudes pendientes en cola"),
            ("en_proceso", "Solicitudes en proceso"),
            ("generadas", "Solicitudes generadas"),
            ("procesadas", "Solicitudes procesadas"),
            ("exitosas", "Reservas exitosas"),
            ("rechazadas", "Solicitudes rechazadas"),
            ("workers", "Workers activos"),
        )
        self._vars_estado: dict[str, tk.StringVar] = {}
        for i, (clave, texto) in enumerate(campos):
            fila, columna = divmod(i, 2)
            ttk.Label(marco, text=f"{texto}:").grid(row=fila, column=columna * 2, sticky="w", padx=(0, 6))
            variable = tk.StringVar(value="0")
            ttk.Label(marco, textvariable=variable, width=8, font=("Segoe UI", 10, "bold")).grid(
                row=fila, column=columna * 2 + 1, sticky="w", padx=(0, 24)
            )
            self._vars_estado[clave] = variable

    def _construir_simulacion(self, padre: ttk.Frame) -> None:
        marco = ttk.LabelFrame(padre, text="Simulación de concurrencia", padding=10)
        marco.pack(fill="x", pady=5)
        campos = (
            ("usuarios", "Usuarios productores", 20, 1, 500),
            ("por_usuario", "Solicitudes por usuario", 10, 1, 1000),
            ("workers", "Workers", self._sistema.cantidad_workers, 1, 64),
            ("demora", "Demora por reserva (ms)", round(self._sistema.demora_procesamiento * 1000), 0, 1000),
            ("disputa", "Asientos en disputa", 1, 1, 30),
            ("semilla", "Semilla aleatoria", 42, 0, 1_000_000),
        )
        self._parametros: dict[str, tuple[tk.StringVar, str, int, int]] = {}
        for i, (clave, texto, inicial, minimo, maximo) in enumerate(campos):
            fila, columna = divmod(i, 2)
            ttk.Label(marco, text=f"{texto}:").grid(row=fila, column=columna * 2, sticky="w", pady=2)
            variable = tk.StringVar(value=str(inicial))
            ttk.Spinbox(marco, from_=minimo, to=maximo, textvariable=variable, width=9).grid(
                row=fila, column=columna * 2 + 1, sticky="w", padx=(6, 24), pady=2
            )
            self._parametros[clave] = (variable, texto, minimo, maximo)

        self._var_escenario = tk.StringVar(value=ESCENARIO_DISTRIBUIDO)
        escenarios = ttk.Frame(marco)
        escenarios.grid(row=3, column=0, columnspan=4, sticky="w", pady=(6, 0))
        ttk.Label(escenarios, text="Escenario:").pack(side="left")
        ttk.Radiobutton(
            escenarios, text="1 - Carga distribuida", variable=self._var_escenario, value=ESCENARIO_DISTRIBUIDO
        ).pack(side="left", padx=6)
        ttk.Radiobutton(
            escenarios,
            text="2 - Alta contención (función seleccionada)",
            variable=self._var_escenario,
            value=ESCENARIO_CONTENCION,
        ).pack(side="left", padx=6)

        self._var_reiniciar = tk.BooleanVar(value=True)
        ttk.Checkbutton(
            marco, text="Reiniciar todos los asientos antes de simular", variable=self._var_reiniciar
        ).grid(row=4, column=0, columnspan=4, sticky="w", pady=4)

        botones = ttk.Frame(marco)
        botones.grid(row=5, column=0, columnspan=4, sticky="w", pady=(4, 0))
        for texto, comando in (
            ("Iniciar simulación", self._iniciar_simulacion),
            (f"Prueba de conflicto ({CANTIDAD_MINIMA_CONFLICTO}+ usuarios -> A1)", self._iniciar_prueba_conflicto),
            ("Reiniciar función", self._reiniciar_funcion),
            ("Aplicar workers", self._aplicar_workers),
        ):
            boton = ttk.Button(botones, text=texto, command=comando)
            boton.pack(side="left", padx=(0, 6))
            self._botones_simulacion.append(boton)

        self._var_estado_simulacion = tk.StringVar()
        ttk.Label(marco, textvariable=self._var_estado_simulacion, foreground="#1565c0").grid(
            row=6, column=0, columnspan=4, sticky="w", pady=(6, 0)
        )

    def _construir_resultados(self, padre: ttk.Frame) -> None:
        marco = ttk.LabelFrame(padre, text="Resultados de la última simulación", padding=6)
        marco.pack(fill="both", expand=True, pady=(5, 0))
        self._texto_resultados = tk.Text(marco, height=13, font=("Consolas", 9), state="disabled", wrap="word")
        self._texto_resultados.pack(fill="both", expand=True)

    def _construir_historial(self) -> None:
        marco = ttk.LabelFrame(
            self._root, text=f"Historial de solicitudes procesadas (últimas {MAX_FILAS_HISTORIAL})", padding=6
        )
        marco.grid(row=1, column=0, columnspan=2, sticky="nsew", padx=10, pady=(0, 10))
        marco.columnconfigure(0, weight=1)
        marco.rowconfigure(0, weight=1)

        columnas = (
            ("id", "ID", 70),
            ("usuario", "Usuario", 130),
            ("pelicula", "Película", 130),
            ("funcion", "Función", 80),
            ("asiento", "Asiento", 70),
            ("resultado", "Resultado", 320),
            ("worker", "Worker", 90),
        )
        self._tabla = ttk.Treeview(marco, columns=[c[0] for c in columnas], show="headings", height=9)
        for clave, titulo, ancho in columnas:
            self._tabla.heading(clave, text=titulo)
            self._tabla.column(clave, width=ancho, anchor="w")
        self._tabla.tag_configure("exitosa", foreground="#2e7d32")
        self._tabla.tag_configure("rechazada", foreground="#c62828")
        barra = ttk.Scrollbar(marco, orient="vertical", command=self._tabla.yview)
        self._tabla.configure(yscrollcommand=barra.set)
        self._tabla.grid(row=0, column=0, sticky="nsew")
        barra.grid(row=0, column=1, sticky="ns")

    # ------------------------------------------------------------------ reserva manual

    def _pelicula_actual(self) -> Pelicula:
        return self._cine.peliculas[self._combo_pelicula.current()]

    def _cargar_funciones(self, pelicula: Pelicula) -> None:
        self._combo_funcion.configure(values=[f.horario for f in pelicula.funciones])
        self._combo_funcion.current(0)
        self._cambiar_funcion(pelicula.funciones[0])

    def _al_cambiar_pelicula(self, _evento: tk.Event) -> None:
        self._cargar_funciones(self._pelicula_actual())

    def _al_cambiar_funcion(self, _evento: tk.Event) -> None:
        self._cambiar_funcion(self._pelicula_actual().funciones[self._combo_funcion.current()])

    def _cambiar_funcion(self, funcion: Funcion) -> None:
        self._funcion_actual = funcion
        self._seleccionar_asiento(None)

    def _seleccionar_asiento(self, codigo: str | None) -> None:
        self._asiento_seleccionado = codigo
        self._var_seleccion.set(f"Asiento seleccionado: {codigo or 'ninguno'}")
        self._actualizar_mapa_asientos()

    def _reservar(self) -> None:
        if self._asiento_seleccionado is None:
            self._var_mensaje.set("Seleccione un asiento en el mapa.")
            return
        usuario = self._var_usuario.get().strip() or "Usuario manual"
        solicitud = self._sistema.enviar(usuario, self._funcion_actual.id, self._asiento_seleccionado)
        if solicitud is None:
            self._var_mensaje.set("El sistema ya no acepta solicitudes.")
            return
        self._var_mensaje.set(
            f"Solicitud #{solicitud.id} encolada: {solicitud.asiento} en "
            f"{self._funcion_actual.descripcion}. Pendiente de procesamiento."
        )

    # ------------------------------------------------------------------ simulación

    def _leer_parametros(self) -> dict[str, int] | None:
        valores: dict[str, int] = {}
        for clave, (variable, texto, minimo, maximo) in self._parametros.items():
            try:
                valor = int(variable.get())
            except ValueError:
                valor = None
            if valor is None or not minimo <= valor <= maximo:
                messagebox.showerror("Parámetro inválido", f"'{texto}' debe ser un entero entre {minimo} y {maximo}.")
                return None
            valores[clave] = valor
        return valores

    def _sistema_libre(self) -> bool:
        if self._simulador.en_curso():
            messagebox.showwarning("Simulación en curso", "Espere a que termine la simulación actual.")
            return False
        if not self._sistema.inactivo():
            messagebox.showwarning(
                "Solicitudes pendientes", "Hay solicitudes pendientes o en proceso. Espere a que terminen."
            )
            return False
        return True

    def _preparar_ejecucion(self) -> dict[str, int] | None:
        parametros = self._leer_parametros()
        if parametros is None or not self._sistema_libre():
            return None
        self._sistema.configurar_workers(parametros["workers"])
        self._sistema.demora_procesamiento = parametros["demora"] / 1000
        return parametros

    def _iniciar_simulacion(self) -> None:
        parametros = self._preparar_ejecucion()
        if parametros is None:
            return
        if self._var_escenario.get() == ESCENARIO_DISTRIBUIDO:
            self._simulador.iniciar_carga_distribuida(
                parametros["usuarios"], parametros["por_usuario"], parametros["semilla"], self._var_reiniciar.get()
            )
        else:
            self._simulador.iniciar_alta_contencion(
                self._funcion_actual.id,
                parametros["usuarios"],
                parametros["por_usuario"],
                parametros["disputa"],
                parametros["semilla"],
                self._var_reiniciar.get(),
            )

    def programar_simulacion(
        self,
        escenario: str,
        usuarios: int,
        por_usuario: int,
        asientos_en_disputa: int,
        semilla: int,
        salir_al_terminar: bool,
    ) -> None:
        """Inicia un escenario sin intervención manual, para mediciones repetibles."""
        for clave, valor in (
            ("usuarios", usuarios),
            ("por_usuario", por_usuario),
            ("disputa", asientos_en_disputa),
            ("semilla", semilla),
        ):
            self._parametros[clave][0].set(str(valor))
        self._var_escenario.set(escenario)
        self._salir_al_terminar = salir_al_terminar
        self._root.after(RETARDO_INICIO_AUTOMATICO_MS, self._iniciar_simulacion)

    def _iniciar_prueba_conflicto(self) -> None:
        parametros = self._preparar_ejecucion()
        if parametros is None:
            return
        self._simulador.iniciar_prueba_conflicto(self._funcion_actual.id, parametros["usuarios"])

    def _reiniciar_funcion(self) -> None:
        if self._sistema_libre():
            self._cine.reiniciar_funcion(self._funcion_actual.id)
            self._var_mensaje.set(f"Se reiniciaron los asientos de {self._funcion_actual.descripcion}.")

    def _aplicar_workers(self) -> None:
        parametros = self._leer_parametros()
        if parametros is not None and self._sistema_libre():
            self._sistema.configurar_workers(parametros["workers"])

    # ------------------------------------------------------------------ refresco periódico

    def _refrescar(self) -> None:
        """Consume los resultados que publican los workers. Corre en el hilo de Tkinter vía after()."""
        nuevas_filas = 0
        for _ in range(MAX_EVENTOS_POR_CICLO):
            try:
                evento = self._sistema.resultados.get_nowait()
            except queue.Empty:
                break
            if isinstance(evento, SolicitudReserva):
                self._agregar_al_historial(evento)
                nuevas_filas += 1
            elif isinstance(evento, ResumenSimulacion):
                self._mostrar_resumen(evento)
        if nuevas_filas:
            filas = self._tabla.get_children()
            if len(filas) > MAX_FILAS_HISTORIAL:
                self._tabla.delete(*filas[MAX_FILAS_HISTORIAL:])

        self._actualizar_estado()
        self._actualizar_mapa_asientos()
        self._actualizar_controles()
        self._after_id = self._root.after(INTERVALO_REFRESCO_MS, self._refrescar)

    def _agregar_al_historial(self, solicitud: SolicitudReserva) -> None:
        exitosa = solicitud.resultado is ResultadoReserva.EXITOSA
        self._tabla.insert(
            "",
            0,
            values=(
                f"#{solicitud.id}",
                solicitud.usuario,
                solicitud.pelicula,
                solicitud.horario,
                solicitud.asiento,
                solicitud.resultado.value if solicitud.resultado else "",
                solicitud.worker,
            ),
            tags=("exitosa" if exitosa else "rechazada",),
        )

    def _mostrar_resumen(self, resumen: ResumenSimulacion) -> None:
        self._texto_resultados.configure(state="normal")
        self._texto_resultados.delete("1.0", "end")
        self._texto_resultados.insert("1.0", resumen.texto())
        self._texto_resultados.configure(state="disabled")
        if resumen.es_prueba_conflicto:
            mensaje = (
                f"Reservas exitosas: {resumen.exitosas}\n"
                f"Reservas rechazadas: {resumen.rechazadas}\n\n"
                f"Resultado: {'APROBADA' if resumen.aprobada else 'FALLIDA'}"
            )
            if resumen.aprobada:
                messagebox.showinfo("Prueba de conflicto", mensaje)
            else:
                messagebox.showerror("Prueba de conflicto", mensaje)
        if self._salir_al_terminar:
            self._root.after(500, self.cerrar)

    def _actualizar_estado(self) -> None:
        estado = self._sistema.estadisticas()
        self._vars_estado["pendientes"].set(str(estado.pendientes))
        self._vars_estado["en_proceso"].set(str(estado.en_proceso))
        self._vars_estado["generadas"].set(str(estado.generadas))
        self._vars_estado["procesadas"].set(str(estado.procesadas))
        self._vars_estado["exitosas"].set(str(estado.exitosas))
        self._vars_estado["rechazadas"].set(str(estado.rechazadas))
        self._vars_estado["workers"].set(str(estado.workers_activos))

    def _actualizar_mapa_asientos(self) -> None:
        asientos = self._funcion_actual.asientos
        for codigo, boton in self._botones_asientos.items():
            # Lectura sin lock: es sólo una instantánea para dibujar. Tomar el lock podría
            # congelar la interfaz mientras un worker está confirmando ese asiento.
            reservado = asientos[codigo].estado is EstadoAsiento.RESERVADO
            if codigo == self._asiento_seleccionado:
                apariencia = (COLOR_SELECCIONADO_RESERVADO if reservado else COLOR_SELECCIONADO, "sunken")
            else:
                apariencia = (COLOR_RESERVADO if reservado else COLOR_DISPONIBLE, "raised")
            if self._apariencia_asientos.get(codigo) != apariencia:
                color, relieve = apariencia
                boton.configure(bg=color, activebackground=color, relief=relieve)
                self._apariencia_asientos[codigo] = apariencia

    def _actualizar_controles(self) -> None:
        nombre = self._simulador.nombre_actual
        en_curso = nombre is not None
        if en_curso == self._simulacion_visible:
            return
        self._simulacion_visible = en_curso
        estado = "disabled" if en_curso else "normal"
        for boton in self._botones_simulacion:
            boton.configure(state=estado)
        # Durante la prueba de conflicto se bloquea la reserva manual para no alterar su resultado.
        self._boton_reservar.configure(state="disabled" if nombre == "Prueba de conflicto" else "normal")
        self._var_estado_simulacion.set(f"En curso: {nombre}..." if en_curso else "Sin simulación en curso.")

    # ------------------------------------------------------------------ cierre

    def liberar_recursos(self) -> None:
        """Cierre ordenado: no aceptar más solicitudes, detener productores y luego workers."""
        if self._recursos_liberados:
            return
        self._recursos_liberados = True
        print("Cerrando aplicación...", flush=True)
        self._sistema.dejar_de_aceptar()
        self._simulador.detener()
        descartadas, sin_terminar = self._sistema.detener()
        print(f"Solicitudes que quedaron pendientes sin procesar: {descartadas}")
        print(f"Workers que no finalizaron a tiempo: {sin_terminar}", flush=True)

    def cerrar(self) -> None:
        if self._after_id is not None:
            self._root.after_cancel(self._after_id)
            self._after_id = None
        self.liberar_recursos()
        self._root.destroy()
