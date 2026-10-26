"""Reglas puras respecto a infraestructura: no importa Pygame ni threading."""

from dataclasses import dataclass, field
from enum import Enum

Position = tuple[int, int]


class Direction(Enum):
    UP = (0, -1)
    LEFT = (-1, 0)
    DOWN = (0, 1)
    RIGHT = (1, 0)
    STOP = (0, 0)


class State(Enum):
    START = "Inicio"
    RUNNING = "Jugando"
    PAUSED = "Pausa"
    WON = "Victoria"
    LOST = "Derrota"


class Ghost(Enum):
    BLINKY = "Blinky"
    PINKY = "Pinky"
    INKY = "Inky"
    CLYDE = "Clyde"


LEVEL = (
    "###################",
    "#o.......#.......o#",
    "#.##.###.#.###.##.#",
    "#.................#",
    "#.##.#.#####.#.##.#",
    "#....#...#...#....#",
    "####.###.#.###.####",
    "#.................#",
    "#.##.###...###.##.#",
    "#......     ......#",
    "#.##.#.#####.#.##.#",
    "#o...#... ...#...o#",
    "###################",
)
PLAYER_START = (9, 11)
GHOST_STARTS = ((8, 9), (9, 9), (10, 9), (11, 9))


def moved(position: Position, direction: Direction) -> Position:
    return position[0] + direction.value[0], position[1] + direction.value[1]


@dataclass(frozen=True)
class Board:
    cells: tuple[str, ...] = LEVEL

    def __post_init__(self) -> None:
        if not self.cells or not self.cells[0] or len({len(row) for row in self.cells}) != 1:
            raise ValueError("El tablero debe ser una matriz rectangular no vacía")

    def walkable(self, position: Position) -> bool:
        x, y = position
        return 0 <= y < len(self.cells) and 0 <= x < len(self.cells[0]) and self.cells[y][x] != "#"


@dataclass
class Game:
    board: Board = field(default_factory=Board)
    player: Position = PLAYER_START
    direction: Direction = Direction.STOP
    requested: Direction = Direction.STOP
    ghosts: dict[Ghost, Position] = field(
        default_factory=lambda: dict(zip(Ghost, GHOST_STARTS, strict=True))
    )
    state: State = State.START
    score: int = 0
    lives: int = 3
    power_remaining: float = 0.0
    pellets: set[Position] = field(init=False)
    powers: set[Position] = field(init=False)

    def __post_init__(self) -> None:
        self.pellets = {
            (x, y)
            for y, row in enumerate(self.board.cells)
            for x, cell in enumerate(row)
            if cell == "."
        }
        self.powers = {
            (x, y)
            for y, row in enumerate(self.board.cells)
            for x, cell in enumerate(row)
            if cell == "o"
        }

    def reset_positions(self) -> None:
        self.player = PLAYER_START
        self.direction = self.requested = Direction.STOP
        self.ghosts = dict(zip(Ghost, GHOST_STARTS, strict=True))
        self.power_remaining = 0.0

    def advance(self, proposals: dict[Ghost, Direction], dt: float, power_seconds: float) -> None:
        """Aplica simultáneamente movimientos y detecta también cruces de celdas."""
        if self.state is not State.RUNNING:
            return
        self.power_remaining = max(0.0, self.power_remaining - dt)
        old_player, old_ghosts = self.player, self.ghosts.copy()
        if self.board.walkable(moved(self.player, self.requested)):
            self.direction = self.requested
        destination = moved(self.player, self.direction)
        if self.board.walkable(destination):
            self.player = destination
        if self.player in self.pellets:
            self.pellets.remove(self.player)
            self.score += 10
        if self.player in self.powers:
            self.powers.remove(self.player)
            self.score += 50
            self.power_remaining = power_seconds
        for ghost in Ghost:
            destination = moved(self.ghosts[ghost], proposals.get(ghost, Direction.STOP))
            if self.board.walkable(destination):
                self.ghosts[ghost] = destination
        collisions = [
            ghost
            for ghost in Ghost
            if self.ghosts[ghost] == self.player
            or (old_ghosts[ghost] == self.player and self.ghosts[ghost] == old_player)
        ]
        if collisions and self.power_remaining > 0:
            for ghost in collisions:
                self.score += 200
                self.ghosts[ghost] = GHOST_STARTS[list(Ghost).index(ghost)]
        elif collisions:
            self.lives -= 1
            self.reset_positions()
            if self.lives <= 0:
                self.state = State.LOST
        if not self.pellets and not self.powers and self.state is State.RUNNING:
            self.state = State.WON

    def invariant_errors(self) -> int:
        return (
            sum(not self.board.walkable(p) for p in [self.player, *self.ghosts.values()])
            + int(self.lives < 0)
            + int(self.power_remaining < 0)
            + int(bool(self.pellets & self.powers))
        )
