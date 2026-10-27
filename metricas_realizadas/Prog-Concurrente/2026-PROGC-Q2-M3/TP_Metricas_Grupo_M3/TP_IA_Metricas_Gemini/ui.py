import os
import tkinter as tk
from tkinter import ttk, messagebox
import datetime
import random
import time
import threading
from models import ReservationRequest, SeatState, ReservationResultType
from cinema import Cinema
from reservation_system import ReservationSystem
from simulation import Simulator

class CinemaApp(tk.Tk):
    def __init__(self, cinema: Cinema, system: ReservationSystem):
        super().__init__()
        self.pid = os.getpid()
        self.title(f"Sistema de Reservas - Cine (Concurrente) [PID: {self.pid}]")
        self.geometry("1100x700")
        
        self.cinema = cinema
        self.system = system
        self.simulator = Simulator(system, cinema)
        
        self.selected_movie = tk.StringVar()
        self.selected_function = tk.StringVar()
        
        self._build_ui()
        self._update_selections()
        
        # Iniciar bucles de actualización de la UI basados en colas/tiempo (thread-safe)
        self.after(100, self._process_results)
        self.after(500, self._update_metrics)
        self.after(500, self._refresh_seats_from_cinema)

    def _build_ui(self):
        # Layout principal
        top_frame = ttk.Frame(self)
        top_frame.pack(fill=tk.X, padx=10, pady=5)
        
        middle_frame = ttk.Frame(self)
        middle_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)
        
        bottom_frame = ttk.Frame(self)
        bottom_frame.pack(fill=tk.X, padx=10, pady=5)
        
        # --- SECCIÓN DE SELECCIÓN ---
        ctrl_frame = ttk.LabelFrame(top_frame, text="Selección")
        ctrl_frame.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)
        
        ttk.Label(ctrl_frame, text="Película:").grid(row=0, column=0, padx=5, pady=5)
        self.cb_movie = ttk.Combobox(ctrl_frame, textvariable=self.selected_movie, state="readonly")
        self.cb_movie['values'] = list(self.cinema.movies.keys())
        if self.cb_movie['values']:
            self.cb_movie.current(0)
        self.cb_movie.grid(row=0, column=1, padx=5, pady=5)
        self.cb_movie.bind("<<ComboboxSelected>>", lambda e: self._update_selections())
        
        ttk.Label(ctrl_frame, text="Función:").grid(row=1, column=0, padx=5, pady=5)
        self.cb_func = ttk.Combobox(ctrl_frame, textvariable=self.selected_function, state="readonly")
        self.cb_func.grid(row=1, column=1, padx=5, pady=5)
        self.cb_func.bind("<<ComboboxSelected>>", lambda e: self._refresh_seats_ui())
        
        ttk.Label(ctrl_frame, text="Usuario:").grid(row=2, column=0, padx=5, pady=5)
        self.entry_user = ttk.Entry(ctrl_frame)
        self.entry_user.insert(0, "Usuario Manual")
        self.entry_user.grid(row=2, column=1, padx=5, pady=5)

        # --- SECCIÓN DE SIMULACIÓN ---
        sim_frame = ttk.LabelFrame(top_frame, text="Simulación Concurrente")
        sim_frame.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)
        
        ttk.Label(sim_frame, text="Usuarios (Productores):").grid(row=0, column=0, padx=5)
        self.spin_users = ttk.Spinbox(sim_frame, from_=1, to=1000, width=5)
        self.spin_users.set(10)
        self.spin_users.grid(row=0, column=1, padx=5)
        
        ttk.Label(sim_frame, text="Solicitudes por usuario:").grid(row=1, column=0, padx=5)
        self.spin_reqs = ttk.Spinbox(sim_frame, from_=1, to=1000, width=5)
        self.spin_reqs.set(5)
        self.spin_reqs.grid(row=1, column=1, padx=5)
        
        self.btn_sim_dist = ttk.Button(sim_frame, text="Simular Carga Distribuida", command=lambda: self._start_sim("DISTRIBUTED"))
        self.btn_sim_dist.grid(row=0, column=2, padx=5, pady=2)
        
        self.btn_sim_cont = ttk.Button(sim_frame, text="Simular Alta Contención (A1)", command=lambda: self._start_sim("CONTENTION"))
        self.btn_sim_cont.grid(row=1, column=2, padx=5, pady=2)
        
        self.btn_test = ttk.Button(sim_frame, text="Prueba de Conflicto (Correctitud)", command=self._run_conflict_test)
        self.btn_test.grid(row=2, column=0, columnspan=3, pady=5)

        # --- MAPA DE ASIENTOS E HISTORIAL ---
        self.seats_frame = ttk.LabelFrame(middle_frame, text="Mapa de Asientos (Clic para reservar)")
        self.seats_frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5)
        
        history_frame = ttk.LabelFrame(middle_frame, text="Historial de Solicitudes (Resultados de Workers)")
        history_frame.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True, padx=5)
        
        columns = ("id", "user", "movie", "function", "seat", "result")
        self.tree = ttk.Treeview(history_frame, columns=columns, show="headings", height=15)
        for col in columns:
            self.tree.heading(col, text=col.capitalize())
            self.tree.column(col, width=90, anchor=tk.CENTER)
        self.tree.pack(fill=tk.BOTH, expand=True)
        
        # --- ESTADO DEL SISTEMA ---
        status_frame = ttk.LabelFrame(bottom_frame, text="Estado del Sistema")
        status_frame.pack(fill=tk.X, padx=5, pady=5)
        
        self.lbl_pending = ttk.Label(status_frame, text="Pendientes en cola: 0")
        self.lbl_pending.pack(side=tk.LEFT, padx=15)
        
        self.lbl_generated = ttk.Label(status_frame, text="Generadas: 0")
        self.lbl_generated.pack(side=tk.LEFT, padx=15)
        
        self.lbl_processed = ttk.Label(status_frame, text="Procesadas: 0")
        self.lbl_processed.pack(side=tk.LEFT, padx=15)
        
        self.lbl_success = ttk.Label(status_frame, text="Reservas Exitosas: 0", foreground="green", font=("Arial", 9, "bold"))
        self.lbl_success.pack(side=tk.LEFT, padx=15)
        
        self.lbl_rejected = ttk.Label(status_frame, text="Solicitudes Rechazadas: 0", foreground="red", font=("Arial", 9, "bold"))
        self.lbl_rejected.pack(side=tk.LEFT, padx=15)
        
        self.lbl_workers = ttk.Label(status_frame, text=f"Workers Activos: {self.system.num_workers}")
        self.lbl_workers.pack(side=tk.RIGHT, padx=15)
        
        self.lbl_pid = ttk.Label(status_frame, text=f"PID: {self.pid}", font=("Arial", 9, "bold"))
        self.lbl_pid.pack(side=tk.RIGHT, padx=15)

        self.seat_buttons = {}

    def _update_selections(self):
        movie_name = self.selected_movie.get()
        if movie_name in self.cinema.movies:
            funcs = list(self.cinema.movies[movie_name].functions.keys())
            self.cb_func['values'] = funcs
            if funcs:
                self.cb_func.current(0)
            self._refresh_seats_ui()

    def _refresh_seats_ui(self):
        for widget in self.seats_frame.winfo_children():
            widget.destroy()
        
        self.seat_buttons.clear()
        movie_name = self.selected_movie.get()
        function_time = self.selected_function.get()
        
        if not movie_name or not function_time:
            return
            
        movie = self.cinema.movies.get(movie_name)
        if not movie: return
        func = movie.functions.get(function_time)
        if not func: return
        
        # Agrupar asientos por fila (ej. A, B, C...)
        rows = {}
        for seat_id, seat in func.seats.items():
            r = seat_id[0]
            if r not in rows:
                rows[r] = []
            rows[r].append(seat)
            
        for r_idx, (r_char, seat_list) in enumerate(sorted(rows.items())):
            for c_idx, seat in enumerate(sorted(seat_list, key=lambda s: int(s.seat_id[1:]))):
                btn = tk.Button(self.seats_frame, text=seat.seat_id, width=5, height=2,
                                command=lambda s=seat.seat_id: self._manual_reservation(s))
                btn.grid(row=r_idx, column=c_idx, padx=3, pady=3)
                self.seat_buttons[seat.seat_id] = btn
                
        self._update_seat_colors(func)

    def _refresh_seats_from_cinema(self):
        # Actualización visual periódica
        movie_name = self.selected_movie.get()
        function_time = self.selected_function.get()
        
        if movie_name and function_time:
            movie = self.cinema.movies.get(movie_name)
            if movie:
                func = movie.functions.get(function_time)
                if func:
                    self._update_seat_colors(func)
        
        self.after(500, self._refresh_seats_from_cinema)

    def _update_seat_colors(self, func):
        for seat_id, btn in self.seat_buttons.items():
            seat = func.seats.get(seat_id)
            if seat:
                if seat.state == SeatState.AVAILABLE:
                    btn.configure(bg="#a8e6cf") # Verde claro
                else:
                    btn.configure(bg="#ff8b94") # Rojo salmón

    def _manual_reservation(self, seat_id):
        movie = self.selected_movie.get()
        func = self.selected_function.get()
        user = self.entry_user.get() or "Manual"
        
        req = ReservationRequest(
            request_id=random.randint(1000, 9999),
            user_name=user,
            movie_name=movie,
            function_time=func,
            seat_id=seat_id,
            created_at=datetime.datetime.now()
        )
        self.system.submit_request(req)

    def _start_sim(self, scenario):
        try:
            users = int(self.spin_users.get())
            reqs = int(self.spin_reqs.get())
        except ValueError:
            messagebox.showerror("Error", "Ingrese valores numéricos válidos")
            return
            
        # Ejecutar la simulación en un hilo secundario para no bloquear la UI
        threading.Thread(target=self._run_sim_thread, args=(users, reqs, scenario), daemon=True).start()

    def _run_sim_thread(self, users, reqs, scenario):
        self.simulator.run_simulation(users, reqs, scenario)
        time_taken = self.simulator.end_time - self.simulator.start_time
        total_reqs = users * reqs
        avg_time = time_taken / total_reqs if total_reqs > 0 else 0
        rps = total_reqs / time_taken if time_taken > 0 else 0
        
        msg = (f"Simulación Finalizada:\n\n"
               f"Solicitudes Generadas: {total_reqs}\n"
               f"Tiempo Total de Procesamiento: {time_taken:.2f} s\n"
               f"Tiempo Promedio por Solicitud: {avg_time:.4f} s\n"
               f"Solicitudes por Segundo (req/s): {rps:.2f}")
        
        # Comunicar resultado de vuelta a la UI de forma segura
        self.after(0, lambda: messagebox.showinfo("Resultados Simulación", msg))

    def _run_conflict_test(self):
        # Ejecutar en hilo secundario
        threading.Thread(target=self._run_conflict_test_thread, daemon=True).start()

    def _run_conflict_test_thread(self):
        # Capturar métricas antes de la prueba
        with self.system.metrics_lock:
            prev_success = self.system.successful_reservations
            prev_reject = self.system.rejected_reservations
            
        self.simulator.run_conflict_test()
        
        # Capturar diferencias luego de la prueba
        with self.system.metrics_lock:
            new_success = self.system.successful_reservations - prev_success
            new_reject = self.system.rejected_reservations - prev_reject
            
        # Verificar invariantes (20 peticiones concurrentes -> 1 éxito, 19 rechazos)
        if new_success == 1 and new_reject == 19:
            status = "EXITOSA"
        else:
            status = "FALLIDA"
            
        msg = f"Prueba de Conflicto {status}\n\nReservas exitosas: {new_success}\nReservas rechazadas: {new_reject}"
        self.after(0, lambda: messagebox.showinfo("Prueba de Conflicto", msg))

    def _process_results(self):
        """Consume la cola de resultados proveniente de los workers y actualiza el Treeview."""
        try:
            # Procesar un máximo de 50 resultados a la vez para mantener la UI fluida
            for _ in range(50):
                if self.system.result_queue.empty():
                    break
                result = self.system.result_queue.get_nowait()
                
                # Insertar al inicio
                self.tree.insert("", 0, values=(
                    f"#{result.request.request_id}",
                    result.request.user_name,
                    result.request.movie_name,
                    result.request.function_time,
                    result.request.seat_id,
                    result.status.value
                ))
                
                # Mantener el historial acotado a 1000 items para evitar consumo excesivo de memoria en la UI
                children = self.tree.get_children()
                if len(children) > 1000:
                    self.tree.delete(children[-1])
        except Exception:
            pass
            
        self.after(100, self._process_results)

    def _update_metrics(self):
        """Actualiza los contadores de la UI leyendo variables protegidas."""
        pending = self.system.request_queue.qsize()
        
        with self.system.metrics_lock:
            gen = self.system.total_generated
            proc = self.system.total_processed
            succ = self.system.successful_reservations
            rej = self.system.rejected_reservations
            
        self.lbl_pending.config(text=f"Pendientes: {pending}")
        self.lbl_generated.config(text=f"Generadas: {gen}")
        self.lbl_processed.config(text=f"Procesadas: {proc}")
        self.lbl_success.config(text=f"Exitosas: {succ}")
        self.lbl_rejected.config(text=f"Rechazadas: {rej}")
        
        self.after(500, self._update_metrics)

    def on_closing(self):
        """Cierre ordenado y limpieza de recursos."""
        self.simulator.stop()
        self.system.stop()
        self.destroy()
