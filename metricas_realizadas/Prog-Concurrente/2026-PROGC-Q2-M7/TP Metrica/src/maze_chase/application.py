"""Pipeline de un tick: recoger sin bloquear, validar, aplicar y publicar."""

from dataclasses import dataclass, field
from time import perf_counter

from .ai import Request, Result, Snapshot
from .domain import Direction, Game, Ghost, State, moved
from .execution import Executor


@dataclass(frozen=True)
class Config:
    tick_hz: float = 8.0
    fps: int = 60
    power_seconds: float = 6.0
    cell_size: int = 32

    def __post_init__(self) -> None:
        if not 0 < self.tick_hz <= 240 or self.fps <= 0 or self.power_seconds <= 0:
            raise ValueError("Frecuencias y duración deben ser positivas; tick_hz <= 240")


@dataclass
class Counters:
    ticks: int = 0
    stale: int = 0
    duplicates: int = 0
    invalid: int = 0
    invariant_violations: int = 0
    missed: int = 0
    applied: int = 0
    update_seconds: list[float] = field(default_factory=list)


class Controller:
    def __init__(self, executor: Executor, config: Config | None = None) -> None:
        self.executor, self.config = executor, config or Config()
        self.game = Game()
        self.generation = 0
        self.pending: int | None = None
        self.ready: dict[Ghost, Result] = {}
        self.counts = Counters()

    def poll(self) -> None:
        for result in self.executor.collect():
            if result.tick != self.pending:
                self.counts.stale += 1
            elif result.ghost in self.ready:
                self.counts.duplicates += 1
            else:
                self.ready[result.ghost] = result

    def invalidate(self) -> None:
        self.counts.stale += len(self.ready)
        self.ready.clear()
        self.pending = None
        self.generation += 1

    def restart(self) -> None:
        self.invalidate()
        self.game = Game(state=State.RUNNING)

    def toggle(self) -> None:
        if self.game.state in (State.START, State.PAUSED):
            self.game.state = State.RUNNING
        elif self.game.state is State.RUNNING:
            self.game.state = State.PAUSED
        self.invalidate()

    def tick(self) -> None:
        start = perf_counter()
        self.poll()
        if self.game.state is not State.RUNNING:
            return
        moves: dict[Ghost, Direction] = {}
        if self.pending is not None:
            for ghost in Ghost:
                result = self.ready.get(ghost)
                if result is None:
                    self.counts.missed += 1
                elif result.success:
                    if self.game.board.walkable(moved(self.game.ghosts[ghost], result.direction)):
                        moves[ghost] = result.direction
                        self.counts.applied += 1
                    else:
                        self.counts.invalid += 1
        self.ready.clear()
        self.pending = None
        self.game.advance(moves, 1 / self.config.tick_hz, self.config.power_seconds)
        self.counts.ticks += 1
        self.counts.invariant_violations += self.game.invariant_errors()
        self.generation += 1
        if self.game.state is State.RUNNING:
            snapshot = Snapshot(
                self.generation,
                self.game.board,
                self.game.player,
                self.game.direction,
                tuple(self.game.ghosts[g] for g in Ghost),
                self.game.power_remaining,
            )
            self.pending = self.generation
            for ghost in Ghost:
                self.executor.submit(Request(ghost, snapshot))
        self.counts.update_seconds.append(perf_counter() - start)
