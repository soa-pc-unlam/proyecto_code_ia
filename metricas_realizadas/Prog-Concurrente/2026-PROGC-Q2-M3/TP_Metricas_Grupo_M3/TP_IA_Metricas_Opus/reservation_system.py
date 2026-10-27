"""Cola compartida de solicitudes, workers consumidores y contadores globales."""

from __future__ import annotations

import queue
import threading
import time
from collections import Counter
from dataclasses import dataclass
from typing import Callable

from cinema import Cine
from models import ResultadoReserva, SolicitudReserva

# Valor sentinel: el worker que lo obtiene de la cola termina su ejecución.
_FIN = object()


@dataclass(frozen=True)
class EstadoSistema:
    pendientes: int
    en_proceso: int
    generadas: int
    procesadas: int
    exitosas: int
    rechazadas: int
    workers_activos: int


class LoteSolicitudes:
    """Agrupa las solicitudes de una simulación para saber cuándo terminaron y medirlas.

    Lo actualizan los productores (al generar) y los workers (al procesar).
    """

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.completado = threading.Event()
        self._generacion_cerrada = False
        self.generadas = 0
        self.procesadas = 0
        self.exitosas = 0
        self.rechazadas = 0
        self.suma_procesamiento = 0.0
        self.suma_espera = 0.0
        self.t_ultimo_fin = 0.0
        self.exitos_por_asiento: Counter[tuple[str, str]] = Counter()

    def registrar_generada(self) -> None:
        with self._lock:
            self.generadas += 1

    def registrar_procesada(self, solicitud: SolicitudReserva) -> None:
        with self._lock:
            self.procesadas += 1
            if solicitud.resultado is ResultadoReserva.EXITOSA:
                self.exitosas += 1
                self.exitos_por_asiento[(solicitud.funcion_id, solicitud.asiento)] += 1
            else:
                self.rechazadas += 1
            self.suma_procesamiento += solicitud.tiempo_procesamiento
            self.suma_espera += solicitud.tiempo_en_cola
            self.t_ultimo_fin = max(self.t_ultimo_fin, solicitud.t_fin or 0.0)
            terminado = self._terminado()
        if terminado:
            self.completado.set()

    def cerrar_generacion(self) -> None:
        """Indica que ningún productor agregará más solicitudes a este lote."""
        with self._lock:
            self._generacion_cerrada = True
            terminado = self._terminado()
        if terminado:
            self.completado.set()

    def _terminado(self) -> bool:
        return self._generacion_cerrada and self.procesadas == self.generadas


class Worker(threading.Thread):
    """Consumidor: toma solicitudes de la cola compartida y las procesa una por una."""

    def __init__(
        self,
        nombre: str,
        cola: queue.Queue,
        procesar: Callable[[SolicitudReserva, str], None],
    ) -> None:
        # daemon sólo como red de seguridad; el cierre normal usa sentinels y join().
        super().__init__(name=nombre, daemon=True)
        self._cola = cola
        self._procesar = procesar

    def run(self) -> None:
        while True:
            # get() bloquea sin consumir CPU y sin retener ningún lock de asiento.
            solicitud = self._cola.get()
            try:
                if solicitud is _FIN:
                    return
                self._procesar(solicitud, self.name)
            finally:
                self._cola.task_done()


class SistemaReservas:
    def __init__(self, cine: Cine, cantidad_workers: int = 4, demora_procesamiento: float = 0.0) -> None:
        self._cine = cine
        self.cola: queue.Queue = queue.Queue()
        # Canal de resultados hacia la interfaz: los workers nunca tocan widgets de Tkinter.
        self.resultados: queue.Queue = queue.Queue()
        self.demora_procesamiento = demora_procesamiento

        # Protege sólo los contadores y el flag de aceptación; los asientos tienen sus propios locks.
        self._lock = threading.Lock()
        self._aceptando = True
        self._siguiente_id = 1
        self._generadas = 0
        self._procesadas = 0
        self._exitosas = 0
        self._rechazadas = 0
        self._en_proceso = 0

        self._workers: list[Worker] = []
        self._cantidad_workers = 0
        self._numero_worker = 0
        self._detenido = False
        self.configurar_workers(cantidad_workers)

    @property
    def cantidad_workers(self) -> int:
        return self._cantidad_workers

    def enviar(
        self,
        usuario: str,
        funcion_id: str,
        asiento: str,
        lote: LoteSolicitudes | None = None,
    ) -> SolicitudReserva | None:
        """Crea una solicitud y la deja pendiente en la cola. Nunca modifica el asiento."""
        funcion = self._cine.obtener_funcion(funcion_id)
        if asiento not in funcion.asientos:
            raise ValueError(f"El asiento {asiento} no existe en la función {funcion_id}")

        with self._lock:
            if not self._aceptando:
                return None
            solicitud = SolicitudReserva(
                id=self._siguiente_id,
                usuario=usuario,
                pelicula=funcion.pelicula.titulo,
                funcion_id=funcion.id,
                horario=funcion.horario,
                asiento=asiento,
                lote=lote,
            )
            self._siguiente_id += 1
            self._generadas += 1

        # Se registra en el lote antes de encolarla para que nunca haya más procesadas que generadas.
        if lote is not None:
            lote.registrar_generada()
        self.cola.put(solicitud)
        return solicitud

    def _procesar(self, solicitud: SolicitudReserva, nombre_worker: str) -> None:
        # Los locks se toman de a uno y en secuencia (contadores, asiento, contadores, lote):
        # un worker nunca retiene dos locks a la vez, lo que descarta deadlocks.
        with self._lock:
            self._en_proceso += 1

        solicitud.worker = nombre_worker
        solicitud.t_inicio = time.perf_counter()
        solicitud.resultado = self._cine.reservar(
            solicitud.funcion_id,
            solicitud.asiento,
            solicitud.usuario,
            self.demora_procesamiento,
        )
        solicitud.t_fin = time.perf_counter()

        with self._lock:
            self._en_proceso -= 1
            self._procesadas += 1
            if solicitud.resultado is ResultadoReserva.EXITOSA:
                self._exitosas += 1
            else:
                self._rechazadas += 1

        self.resultados.put(solicitud)
        if solicitud.lote is not None:
            solicitud.lote.registrar_procesada(solicitud)

    def configurar_workers(self, cantidad: int) -> None:
        """Ajusta la cantidad de workers. Se invoca sólo desde el hilo principal."""
        if cantidad < 1:
            raise ValueError("Debe existir al menos un worker")
        self._workers = [w for w in self._workers if w.is_alive()]
        diferencia = cantidad - self._cantidad_workers
        for _ in range(diferencia):
            self._numero_worker += 1
            worker = Worker(f"Worker-{self._numero_worker}", self.cola, self._procesar)
            self._workers.append(worker)
            worker.start()
        # Cada sentinel detiene exactamente a un worker (el primero que lo tome).
        for _ in range(-diferencia):
            self.cola.put(_FIN)
        self._cantidad_workers = cantidad

    def workers_activos(self) -> int:
        return sum(1 for w in self._workers if w.is_alive())

    def inactivo(self) -> bool:
        """True si no hay solicitudes pendientes ni en proceso."""
        with self._lock:
            return self._generadas == self._procesadas

    def estadisticas(self) -> EstadoSistema:
        with self._lock:
            generadas = self._generadas
            procesadas = self._procesadas
            exitosas = self._exitosas
            rechazadas = self._rechazadas
            en_proceso = self._en_proceso
        return EstadoSistema(
            pendientes=generadas - procesadas - en_proceso,
            en_proceso=en_proceso,
            generadas=generadas,
            procesadas=procesadas,
            exitosas=exitosas,
            rechazadas=rechazadas,
            workers_activos=self.workers_activos(),
        )

    def dejar_de_aceptar(self) -> None:
        with self._lock:
            self._aceptando = False

    def detener(self, timeout: float = 5.0) -> tuple[int, int]:
        """Cierra el sistema. Devuelve (solicitudes pendientes descartadas, workers sin terminar).

        Debe llamarse después de detener a los productores, para que nadie encole durante el vaciado.
        """
        if self._detenido:
            return 0, 0
        self._detenido = True
        self.dejar_de_aceptar()

        descartadas = 0
        while True:
            try:
                item = self.cola.get_nowait()
            except queue.Empty:
                break
            if item is not _FIN:
                descartadas += 1
            self.cola.task_done()

        vivos = [w for w in self._workers if w.is_alive()]
        for _ in vivos:
            self.cola.put(_FIN)
        for worker in vivos:
            worker.join(timeout)
        return descartadas, sum(1 for w in vivos if w.is_alive())
