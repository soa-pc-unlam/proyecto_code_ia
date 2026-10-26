"""Mensajes inmutables y estrategias deterministas compartidas por ambos modos."""

from collections import deque
from dataclasses import dataclass
from time import perf_counter

from .domain import Board, Direction, Ghost, Position, moved


@dataclass(frozen=True)
class Snapshot:
    tick: int
    board: Board
    player: Position
    direction: Direction
    ghosts: tuple[Position, ...]
    power_remaining: float


@dataclass(frozen=True)
class Request:
    ghost: Ghost
    snapshot: Snapshot


@dataclass(frozen=True)
class Result:
    ghost: Ghost
    tick: int
    direction: Direction
    duration: float
    success: bool = True
    error: str = ""


def target(request: Request) -> Position:
    s, ghost = request.snapshot, request.ghost
    x, y = s.player
    dx, dy = s.direction.value
    if ghost is Ghost.BLINKY:
        return s.player
    if ghost is Ghost.PINKY:
        return x + 4 * dx, y + 4 * dy
    if ghost is Ghost.INKY:
        bx, by = s.ghosts[0]
        return 2 * (x + 2 * dx) - bx, 2 * (y + 2 * dy) - by
    cx, cy = s.ghosts[3]
    return s.player if abs(cx - x) + abs(cy - y) > 6 else (1, len(s.board.cells) - 2)


def decide(request: Request) -> Direction:
    """BFS con desempate UP, LEFT, DOWN, RIGHT; huida al estar vulnerable."""
    s = request.snapshot
    origin = s.ghosts[list(Ghost).index(request.ghost)]
    options = [
        d for d in Direction if d is not Direction.STOP and s.board.walkable(moved(origin, d))
    ]
    if not options:
        return Direction.STOP
    if s.power_remaining > 0:
        return max(
            options,
            key=lambda d: sum(abs(a - b) for a, b in zip(moved(origin, d), s.player, strict=True)),
        )
    tx, ty = target(request)
    reachable: list[tuple[Position, Direction]] = []
    queue = deque([(origin, Direction.STOP)])
    seen = {origin}
    while queue:
        position, first = queue.popleft()
        reachable.append((position, first))
        for direction in options if position == origin else tuple(Direction)[:4]:
            neighbor = moved(position, direction)
            if neighbor not in seen and s.board.walkable(neighbor):
                seen.add(neighbor)
                queue.append((neighbor, direction if position == origin else first))
    # El objetivo anticipado puede estar fuera del tablero: usar la celda alcanzable más próxima.
    return min(reachable, key=lambda item: abs(item[0][0] - tx) + abs(item[0][1] - ty))[1]


def calculate(request: Request, strategy=decide) -> Result:
    start = perf_counter()
    try:
        direction = strategy(request)
        if not isinstance(direction, Direction):
            raise TypeError("La estrategia debe devolver Direction")
        return Result(request.ghost, request.snapshot.tick, direction, perf_counter() - start)
    except Exception as exc:  # noqa: BLE001 -- frontera de aislamiento de estrategias
        return Result(
            request.ghost,
            request.snapshot.tick,
            Direction.STOP,
            perf_counter() - start,
            False,
            f"{type(exc).__name__}: {exc}",
        )
