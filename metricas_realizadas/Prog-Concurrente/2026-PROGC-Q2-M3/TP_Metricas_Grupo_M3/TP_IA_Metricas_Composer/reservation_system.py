"""Cola productor-consumidor, workers y estadísticas del sistema."""

from __future__ import annotations

import queue
import threading
import time
from datetime import datetime
from typing import Callable, Optional

from cinema import Cinema
from models import ResultadoProcesamiento, ResultadoSolicitud, SolicitudReserva

SENTINEL = object()


class ReservationSystem:
    def __init__(
        self,
        cinema: Cinema,
        num_workers: int = 4,
        on_result: Optional[Callable[[ResultadoProcesamiento], None]] = None,
    ) -> None:
        self._cinema = cinema
        self._request_queue: queue.Queue = queue.Queue()
        self._result_queue: queue.Queue[ResultadoProcesamiento] = queue.Queue()
        self._on_result = on_result
        self._workers: list[threading.Thread] = []
        self._num_workers = num_workers
        self._shutdown = threading.Event()
        self._id_lock = threading.Lock()
        self._next_id = 1
        self._stats_lock = threading.Lock()
        self._total_generadas = 0
        self._procesadas = 0
        self._exitosas = 0
        self._rechazadas = 0
        self._workers_activos = 0

    @property
    def request_queue(self) -> queue.Queue:
        return self._request_queue

    @property
    def result_queue(self) -> queue.Queue[ResultadoProcesamiento]:
        return self._result_queue

    def crear_solicitud(
        self,
        usuario: str,
        pelicula_id: str,
        pelicula_titulo: str,
        funcion_id: str,
        funcion_horario: str,
        asiento: str,
    ) -> SolicitudReserva:
        with self._id_lock:
            sid = self._next_id
            self._next_id += 1
        return SolicitudReserva(
            solicitud_id=sid,
            usuario=usuario,
            pelicula_id=pelicula_id,
            pelicula_titulo=pelicula_titulo,
            funcion_id=funcion_id,
            funcion_horario=funcion_horario,
            asiento=asiento,
        )

    def encolar(self, solicitud: SolicitudReserva) -> None:
        if self._shutdown.is_set():
            return
        with self._stats_lock:
            self._total_generadas += 1
        self._request_queue.put(solicitud)

    def start_workers(self, num_workers: Optional[int] = None) -> None:
        if num_workers is not None:
            self._num_workers = num_workers
        self._shutdown.clear()
        self._workers = []
        for i in range(self._num_workers):
            t = threading.Thread(
                target=self._worker_loop,
                name=f"Worker-{i + 1}",
                daemon=True,
            )
            t.start()
            self._workers.append(t)
        with self._stats_lock:
            self._workers_activos = len(self._workers)

    def _worker_loop(self) -> None:
        while True:
            item = self._request_queue.get()
            try:
                if item is SENTINEL:
                    break
                solicitud: SolicitudReserva = item
                solicitud.inicio_proceso = datetime.now()
                resultado = self._cinema.procesar_reserva(solicitud)
                solicitud.resultado = resultado
                solicitud.procesada_en = datetime.now()
                proc = ResultadoProcesamiento(solicitud=solicitud)
                with self._stats_lock:
                    self._procesadas += 1
                    if resultado == ResultadoSolicitud.EXITOSA:
                        self._exitosas += 1
                    else:
                        self._rechazadas += 1
                self._result_queue.put(proc)
                if self._on_result:
                    self._on_result(proc)
            finally:
                self._request_queue.task_done()

    def shutdown(self, timeout: float = 5.0) -> None:
        if self._shutdown.is_set() and not self._workers:
            return
        self._shutdown.set()
        workers = list(self._workers)
        for _ in workers:
            self._request_queue.put(SENTINEL)
        for t in workers:
            t.join(timeout=timeout)
        with self._stats_lock:
            self._workers_activos = 0
        self._workers.clear()

    def esperar_cola_vacia(self, timeout: Optional[float] = None) -> bool:
        if timeout is None:
            self._request_queue.join()
            return True
        fin = time.monotonic() + timeout
        while time.monotonic() < fin:
            if self._request_queue.unfinished_tasks == 0:
                return True
            time.sleep(0.05)
        return self._request_queue.unfinished_tasks == 0

    def estadisticas(self) -> dict[str, int]:
        with self._stats_lock:
            return {
                "pendientes": self._request_queue.qsize(),
                "generadas": self._total_generadas,
                "procesadas": self._procesadas,
                "exitosas": self._exitosas,
                "rechazadas": self._rechazadas,
                "workers_activos": self._workers_activos,
            }

    def reiniciar_contadores_ejecucion(self) -> None:
        """Reinicia contadores de una corrida (no modifica asientos)."""
        with self._stats_lock:
            self._total_generadas = 0
            self._procesadas = 0
            self._exitosas = 0
            self._rechazadas = 0
