"""Trabajadores con colas acotadas; ningún trabajador accede al estado autoritativo."""

import logging
from collections.abc import Callable
from dataclasses import dataclass
from queue import Empty, Full, Queue
from threading import Event, Thread
from time import monotonic
from typing import Protocol

from .ai import Request, Result, calculate, decide
from .domain import Direction, Ghost

logger = logging.getLogger(__name__)


@dataclass
class WorkStats:
    processed: int = 0
    errors: int = 0
    result_drops: int = 0
    ai_seconds: float = 0.0
    max_latency: float = 0.0

    def record(self, result: Result) -> None:
        self.processed += 1
        self.errors += int(not result.success)
        self.ai_seconds += result.duration
        self.max_latency = max(self.max_latency, result.duration)


class Executor(Protocol):
    sent: int
    replaced: int

    def submit(self, request: Request) -> None: ...
    def collect(self) -> list[Result]: ...
    def close(self) -> bool: ...
    def statistics(self) -> list[WorkStats]: ...
    def failures(self) -> int: ...


class Sequential:
    def __init__(self, strategy: Callable[[Request], Direction] = decide) -> None:
        self.sent = self.replaced = 0
        self.strategy = strategy
        self.results: list[Result] = []
        self.stats = WorkStats()

    def submit(self, request: Request) -> None:
        self.sent += 1
        result = calculate(request, self.strategy)
        self.stats.record(result)
        self.results.append(result)

    def collect(self) -> list[Result]:
        results, self.results = self.results, []
        return results

    def close(self) -> bool:
        return True

    def statistics(self) -> list[WorkStats]:
        return [self.stats]

    def failures(self) -> int:
        return 0


class Worker(Thread):
    def __init__(self, ghost: Ghost, stop: Event, strategy: Callable[[Request], Direction]) -> None:
        super().__init__(name=f"ghost-{ghost.value}", daemon=True)
        self.tasks: Queue[Request | None] = Queue(maxsize=1)
        self.results: Queue[Result] = Queue(maxsize=2)
        self.stop_event = stop
        self.strategy = strategy
        self.stats = WorkStats()  # Sólo este trabajador escribe; se lee después de join.

    def run(self) -> None:
        while not self.stop_event.is_set():
            request = self.tasks.get()
            if request is None or self.stop_event.is_set():
                return
            result = calculate(request, self.strategy)
            self.stats.record(result)
            if not result.success:
                logger.error(
                    "worker_error ghost=%s tick=%s error=%s",
                    result.ghost.value,
                    result.tick,
                    result.error,
                )
            try:
                self.results.put_nowait(result)
            except Full:
                self.stats.result_drops += 1


class Concurrent:
    def __init__(self, strategy: Callable[[Request], Direction] = decide) -> None:
        self.sent = self.replaced = 0
        self.stop = Event()
        self.workers = {ghost: Worker(ghost, self.stop, strategy) for ghost in Ghost}
        self.unexpected: set[str] = set()
        for worker in self.workers.values():
            worker.start()

    def submit(self, request: Request) -> None:
        if self.stop.is_set():
            raise RuntimeError("Executor cerrado")
        queue = self.workers[request.ghost].tasks
        try:
            queue.put_nowait(request)
        except Full:
            try:
                queue.get_nowait()
                self.replaced += 1
            except Empty:
                pass
            queue.put_nowait(request)  # Único productor; el consumidor sólo libera espacio.
        self.sent += 1

    def collect(self) -> list[Result]:
        results = []
        for worker in self.workers.values():
            # Drenaje acotado incluso si el productor continúa ejecutando.
            for _ in range(2):
                try:
                    results.append(worker.results.get_nowait())
                except Empty:
                    break
        self.failures()
        return results

    def failures(self) -> int:
        if not self.stop.is_set():
            self.unexpected.update(w.name for w in self.workers.values() if not w.is_alive())
        return len(self.unexpected)

    def close(self) -> bool:
        self.failures()
        self.stop.set()
        for worker in self.workers.values():
            try:
                worker.tasks.get_nowait()
                self.replaced += 1
            except Empty:
                pass
            worker.tasks.put_nowait(None)
        deadline = monotonic() + 1.0
        for worker in self.workers.values():
            worker.join(timeout=max(0.0, deadline - monotonic()))
        return all(not w.is_alive() for w in self.workers.values()) and not self.unexpected

    def statistics(self) -> list[WorkStats]:
        # No leer acumuladores de un hilo que no haya terminado.
        return [w.stats for w in self.workers.values() if not w.is_alive()]
