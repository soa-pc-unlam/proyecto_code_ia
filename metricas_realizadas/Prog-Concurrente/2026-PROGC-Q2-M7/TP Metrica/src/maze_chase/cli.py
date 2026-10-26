"""Entrada interactiva y experimento automatizado con ticks a frecuencia fija."""

import argparse
import logging
from pathlib import Path
from time import perf_counter, sleep

import pygame

from .application import Config, Controller
from .domain import Direction, State
from .execution import Concurrent, Sequential
from .metrics import Metrics, export
from .ui import View

# Secuencia temporal fija; se repite aunque una partida termine y se reinicie.
INPUTS = (
    Direction.LEFT,
    Direction.UP,
    Direction.RIGHT,
    Direction.UP,
    Direction.LEFT,
    Direction.DOWN,
    Direction.RIGHT,
    Direction.DOWN,
)


def run(mode: str, config: Config, ticks: int | None = None, headless: bool = False) -> dict:
    if headless and ticks is None:
        raise ValueError("Headless requiere cantidad de ticks")
    metrics = Metrics()
    controller = Controller(Concurrent() if mode == "concurrent" else Sequential(), config)
    view = None
    completed = False
    run_error = None
    video_driver = None
    steps = 0
    try:
        if not headless:
            view = View(controller, mode)
            video_driver = pygame.display.get_driver()
        if ticks is not None:
            controller.game.state = State.RUNNING
        clock = pygame.time.Clock()
        deadline = perf_counter()
        running = True
        while running and (ticks is None or steps < ticks):
            if view:
                running = view.events()
                if not running:
                    break
            controller.poll()
            now = perf_counter()
            if now >= deadline:
                if ticks is not None:
                    if controller.game.state in (State.WON, State.LOST):
                        controller.restart()
                    controller.game.requested = INPUTS[(steps // 12) % len(INPUTS)]
                controller.tick()
                steps += 1
                deadline += 1 / config.tick_hz
                # Limita recuperación tras suspender la máquina: no producir una espiral de ticks.
                deadline = max(deadline, now - 1 / config.tick_hz)
            if view:
                view.draw(clock.get_fps())
                metrics.frames += 1
                clock.tick(config.fps)
            else:
                sleep(max(0, min(0.002, deadline - perf_counter())))
            metrics.sample()
        completed = True
    except KeyboardInterrupt:
        completed = True
    except Exception as exc:
        run_error = f"{type(exc).__name__}: {exc}"
        logging.getLogger(__name__).exception("execution_failed mode=%s", mode)
    finally:
        clean = controller.executor.close()
        controller.poll()
        pygame.quit()
        report = metrics.report(controller, mode, clean and completed, view is not None)
        report["scheduled_steps"] = steps
        report["execution_completed"] = completed
        report["run_error"] = run_error
        report["video_driver"] = video_driver
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("sequential", "concurrent"), default="concurrent")
    parser.add_argument("--tick-hz", type=float, default=8)
    parser.add_argument("--fps", type=int, default=60)
    parser.add_argument("--power-seconds", type=float, default=6)
    parser.add_argument("--ticks", type=int, help="Automatiza entradas durante N ticks")
    parser.add_argument("--headless", action="store_true")
    parser.add_argument("--output", type=Path, default=Path("results/latest.json"))
    args = parser.parse_args()
    if args.ticks is not None and args.ticks <= 0:
        parser.error("--ticks debe ser positivo")
    if args.headless and args.ticks is None:
        parser.error("--headless requiere --ticks")
    try:
        config = Config(args.tick_hz, args.fps, args.power_seconds)
    except ValueError as error:
        parser.error(str(error))
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    report = run(args.mode, config, args.ticks, args.headless)
    export(args.output, report)
    print(f"Métricas: {args.output} · cierre correcto: {report['clean_shutdown']}")
    if not report["clean_shutdown"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
