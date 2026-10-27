import threading
import queue
import time
import datetime
import random
from models import ReservationRequest, ReservationResult, ReservationResultType, SeatState
from cinema import Cinema

class ReservationSystem:
    def __init__(self, cinema: Cinema, num_workers: int = 4):
        self.cinema = cinema
        self.request_queue = queue.Queue()
        self.result_queue = queue.Queue()
        self.num_workers = num_workers
        self.workers = []
        self.is_running = False
        
        # Métricas compartidas
        self.total_generated = 0
        self.total_processed = 0
        self.successful_reservations = 0
        self.rejected_reservations = 0
        self.metrics_lock = threading.Lock()

    def start(self):
        """Inicia los hilos de los consumidores (workers)."""
        self.is_running = True
        for i in range(self.num_workers):
            t = threading.Thread(target=self._worker_loop, name=f"Worker-{i+1}", daemon=True)
            self.workers.append(t)
            t.start()

    def stop(self):
        """Detiene de forma ordenada a todos los workers."""
        self.is_running = False
        # Insertar centinelas para destrabar a los workers que estén esperando
        for _ in range(self.num_workers):
            self.request_queue.put(None)
        
        for t in self.workers:
            t.join()

    def submit_request(self, request: ReservationRequest):
        """Agrega una solicitud a la cola."""
        with self.metrics_lock:
            self.total_generated += 1
        self.request_queue.put(request)

    def _worker_loop(self):
        """Ciclo de vida del worker (consumidor)."""
        while self.is_running:
            req = self.request_queue.get()
            
            # Valor centinela para detener el hilo
            if req is None: 
                self.request_queue.task_done()
                break
            
            # Simulamos latencia de procesamiento
            time.sleep(random.uniform(0.005, 0.015))

            seat = self.cinema.get_seat(req.movie_name, req.function_time, req.seat_id)
            result_status = ReservationResultType.REJECTED

            if seat:
                # SECCIÓN CRÍTICA: Se adquiere el lock específico del asiento solicitado
                with seat.lock:
                    if seat.state == SeatState.AVAILABLE:
                        seat.state = SeatState.RESERVED
                        result_status = ReservationResultType.SUCCESS
                    else:
                        result_status = ReservationResultType.REJECTED
            
            # Registrar resultado
            result = ReservationResult(
                request=req,
                status=result_status,
                processed_at=datetime.datetime.now()
            )
            
            # Actualizar métricas de forma segura
            with self.metrics_lock:
                self.total_processed += 1
                if result_status == ReservationResultType.SUCCESS:
                    self.successful_reservations += 1
                else:
                    self.rejected_reservations += 1
                    
            # Enviar resultado para que lo procese la UI
            self.result_queue.put(result)
            self.request_queue.task_done()
