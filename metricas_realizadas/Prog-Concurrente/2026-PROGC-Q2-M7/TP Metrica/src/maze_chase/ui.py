"""Adaptador Pygame: todas sus llamadas se realizan en el hilo principal."""

import math

import pygame

from .application import Controller
from .domain import Direction, Ghost, State

COLORS = ((255, 83, 105), (255, 150, 218), (80, 227, 231), (255, 180, 90))


class View:
    def __init__(self, controller: Controller, mode: str) -> None:
        pygame.display.init()
        pygame.font.init()
        self.controller, self.mode = controller, mode
        self.cell = controller.config.cell_size
        board = controller.game.board
        self.screen = pygame.display.set_mode(
            (len(board.cells[0]) * self.cell, len(board.cells) * self.cell + 150)
        )
        pygame.display.set_caption("Maze Chase · Laboratorio de concurrencia")
        self.font = pygame.font.Font(None, 25)
        self.large = pygame.font.Font(None, 44)
        self.debug = True

    def events(self) -> bool:
        mapping = {
            pygame.K_UP: Direction.UP,
            pygame.K_DOWN: Direction.DOWN,
            pygame.K_LEFT: Direction.LEFT,
            pygame.K_RIGHT: Direction.RIGHT,
        }
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                return False
            if event.type == pygame.KEYDOWN:
                if event.key == pygame.K_ESCAPE:
                    return False
                if event.key in mapping:
                    self.controller.game.requested = mapping[event.key]
                elif event.key in (pygame.K_SPACE, pygame.K_RETURN):
                    self.controller.toggle()
                elif event.key == pygame.K_r:
                    self.controller.restart()
                elif event.key == pygame.K_F3:
                    self.debug = not self.debug
        return True

    def text(self, value: str, x: int, y: int, color=(213, 223, 242), large=False) -> None:
        self.screen.blit((self.large if large else self.font).render(value, True, color), (x, y))

    def center(self, position: tuple[int, int]) -> tuple[int, int]:
        return position[0] * self.cell + self.cell // 2, position[
            1
        ] * self.cell + 90 + self.cell // 2

    def draw(self, fps: float) -> None:
        game = self.controller.game
        self.screen.fill((10, 15, 30))
        self.text("MAZE CHASE", 18, 12, (255, 216, 74), large=True)
        self.text(f"Puntos  {game.score:05d}    Vidas  {game.lives}    {game.state.value}", 18, 55)
        for y, row in enumerate(game.board.cells):
            for x, cell in enumerate(row):
                if cell == "#":
                    rect = pygame.Rect(
                        x * self.cell + 2, y * self.cell + 92, self.cell - 4, self.cell - 4
                    )
                    pygame.draw.rect(self.screen, (35, 65, 130), rect, border_radius=6)
                    pygame.draw.rect(self.screen, (65, 109, 193), rect, width=1, border_radius=6)
        for position in game.pellets:
            pygame.draw.circle(self.screen, (244, 221, 181), self.center(position), 3)
        for position in game.powers:
            pygame.draw.circle(self.screen, (255, 219, 98), self.center(position), 7)
        for index, ghost in enumerate(Ghost):
            center = self.center(game.ghosts[ghost])
            color = (72, 112, 250) if game.power_remaining > 0 else COLORS[index]
            pygame.draw.circle(self.screen, color, center, 12)
            pygame.draw.rect(self.screen, color, (center[0] - 12, center[1], 24, 11))
            for dx in (-5, 5):
                pygame.draw.circle(self.screen, (255, 255, 255), (center[0] + dx, center[1] - 3), 4)
                pygame.draw.circle(self.screen, (20, 30, 65), (center[0] + dx, center[1] - 3), 2)
        cx, cy = self.center(game.player)
        pygame.draw.circle(self.screen, (255, 216, 74), (cx, cy), 13)
        direction = game.direction if game.direction is not Direction.STOP else Direction.RIGHT
        angle = math.atan2(direction.value[1], direction.value[0])
        points = [(cx, cy)] + [
            (cx + 15 * math.cos(angle + offset), cy + 15 * math.sin(angle + offset))
            for offset in (-0.45, 0.45)
        ]
        pygame.draw.polygon(self.screen, (10, 15, 30), points)
        bottom = self.screen.get_height() - 53
        self.text("Flechas: mover  ·  Espacio: pausa  ·  R: reiniciar  ·  Esc: salir", 12, bottom)
        if self.debug:
            pending = 4 - len(self.controller.ready) if self.controller.pending is not None else 0
            self.text(
                f"{self.mode}  |  FPS {fps:.0f}  |  Pendientes {pending}  |  Poder {game.power_remaining:.1f}s",
                12,
                bottom + 26,
                (134, 163, 206),
            )
        if game.state is not State.RUNNING:
            overlay = pygame.Surface((self.screen.get_width(), 100), pygame.SRCALPHA)
            overlay.fill((5, 9, 22, 230))
            self.screen.blit(overlay, (0, 235))
            self.text(game.state.value.upper(), 175, 245, (255, 216, 74), large=True)
            self.text("Enter / Espacio: jugar o continuar · R: nueva partida", 75, 295)
        pygame.display.flip()
