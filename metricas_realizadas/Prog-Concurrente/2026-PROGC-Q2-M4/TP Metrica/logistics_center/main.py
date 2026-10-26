"""Entry point: wires shared resources, starts workers, shuts down cleanly."""

import argparse
import logging
import math
import queue
import sys
import threading
from dataclasses import dataclass
from typing import TextIO

import config
from core.inventory import Inventory, InventorySnapshot
from core.package import Package
from ui.dashboard import Dashboard
from workers.base import BaseWorker
from workers.exporter import ExportWorker
from workers.operator import Operator
from workers.supervisor import Supervisor
from workers.truck import Truck

logger = logging.getLogger("main")

CLI_DESCRIPTION = (
    "Concurrent logistics center simulation: trucks (producers) unload "
    "packages into a bounded dock queue, operators (consumers) register them "
    "in a shared inventory, a dashboard renders live metrics, an exporter "
    "periodically persists a JSON report, and a supervisor (chaos monkey) "
    "triggers random maintenance pauses. Press Ctrl+C for a clean shutdown "
    "that drains the dock.")
CLI_EPILOG = (
    "example:\n"
    "  python main.py --trucks 4 --operators 2 --queue-size 20 \\\n"
    "      --processing-seconds 0.5 --export-interval 2 --no-chaos \\\n"
    "      --log-level INFO --log-file simulation.log")
SUMMARY_TITLE = "=== Final Summary ==="
SUMMARY_EMPTY_STOCK_LINE = "  (no stock)"


@dataclass(frozen=True)
class SimulationSettings:
  """Immutable runtime settings resolved from the command line.

  Defaults come from `config`; the module constants are never mutated.
  """

  trucks: int = config.TRUCK_COUNT
  operators: int = config.OPERATOR_COUNT
  queue_size: int = config.QUEUE_MAX_SIZE
  processing_seconds: float = config.OPERATOR_PROCESSING_SECONDS
  export_path: str = config.EXPORT_PATH
  export_interval: float = config.EXPORT_INTERVAL_SECONDS
  chaos: bool = True
  log_level: str = config.LOG_LEVEL
  log_file: str | None = None


def positive_int(value: str) -> int:
  """argparse type: an integer >= 1."""
  try:
    number = int(value)
  except ValueError:
    raise argparse.ArgumentTypeError(
        f"invalid integer value: {value!r}") from None
  if number < 1:
    raise argparse.ArgumentTypeError(f"must be >= 1, got {number}")
  return number


def positive_float(value: str) -> float:
  """argparse type: a finite number of seconds > 0."""
  try:
    number = float(value)
  except ValueError:
    raise argparse.ArgumentTypeError(
        f"invalid number value: {value!r}") from None
  if not math.isfinite(number) or number <= 0:
    raise argparse.ArgumentTypeError(
        f"must be a finite number > 0, got {value}")
  return number


def build_parser() -> argparse.ArgumentParser:
  """Builds the command-line parser; defaults come from `config`."""
  parser = argparse.ArgumentParser(
      description=CLI_DESCRIPTION, epilog=CLI_EPILOG,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      "--trucks", type=positive_int, default=config.TRUCK_COUNT,
      metavar="N", help="number of truck (producer) threads, >= 1 "
      "(default: %(default)s)")
  parser.add_argument(
      "--operators", type=positive_int, default=config.OPERATOR_COUNT,
      metavar="N", help="number of operator (consumer) threads, >= 1 "
      "(default: %(default)s)")
  parser.add_argument(
      "--queue-size", type=positive_int, default=config.QUEUE_MAX_SIZE,
      metavar="N", help="dock queue capacity in packages, >= 1 "
      "(default: %(default)s)")
  parser.add_argument(
      "--processing-seconds", type=positive_float,
      default=config.OPERATOR_PROCESSING_SECONDS, metavar="SECONDS",
      help="simulated time an operator spends per package, > 0 "
      "(default: %(default)s)")
  parser.add_argument(
      "--export-path", default=config.EXPORT_PATH, metavar="PATH",
      help="JSON report destination (default: %(default)s)")
  parser.add_argument(
      "--export-interval", type=positive_float,
      default=config.EXPORT_INTERVAL_SECONDS, metavar="SECONDS",
      help="seconds between periodic report exports, > 0 "
      "(default: %(default)s)")
  parser.add_argument(
      "--no-chaos", dest="chaos", action="store_false",
      help="disable the supervisor (no random maintenance pauses)")
  parser.add_argument(
      "--log-level", type=str.upper, choices=config.LOG_LEVEL_CHOICES,
      default=config.LOG_LEVEL, metavar="LEVEL",
      help="logging threshold, one of %(choices)s; keep it at WARNING or "
      "above to avoid breaking the dashboard (default: %(default)s)")
  parser.add_argument(
      "--log-file", default=None, metavar="PATH",
      help="write logs to this file instead of stderr")
  return parser


def parse_args(argv: list[str] | None = None) -> SimulationSettings:
  """Parses `argv` (defaults to `sys.argv[1:]`) into settings.

  Invalid values make argparse print usage and exit with `SystemExit`.
  """
  namespace = build_parser().parse_args(argv)
  return SimulationSettings(**vars(namespace))


def configure_logging(settings: SimulationSettings) -> None:
  """Sends logs to `settings.log_file` if given, otherwise to stderr."""
  logging.basicConfig(level=settings.log_level, format=config.LOG_FORMAT,
                      filename=settings.log_file)


def build_trucks(
    settings: SimulationSettings,
    dock: "queue.Queue[Package]",
    stop_event: threading.Event,
    maintenance_event: threading.Event,
) -> list[Truck]:
  """Creates the producer threads."""
  return [Truck(f"Truck-{index}", dock, stop_event,
                maintenance_event=maintenance_event)
          for index in range(1, settings.trucks + 1)]


def build_operators(
    settings: SimulationSettings,
    dock: "queue.Queue[Package]",
    inventory: Inventory,
    stop_event: threading.Event,
    producers_finished: threading.Event,
    maintenance_event: threading.Event,
) -> list[Operator]:
  """Creates the consumer/writer threads."""
  return [Operator(f"Operator-{index}", dock, inventory, stop_event,
                   producers_finished,
                   processing_seconds=settings.processing_seconds,
                   maintenance_event=maintenance_event)
          for index in range(1, settings.operators + 1)]


def build_supervisor(
    settings: SimulationSettings,
    stop_event: threading.Event,
    maintenance_event: threading.Event,
) -> Supervisor | None:
  """Creates the chaos monkey, or None when chaos is disabled."""
  if not settings.chaos:
    return None
  return Supervisor("Supervisor", stop_event, maintenance_event)


def wait_for_shutdown(stop_event: threading.Event) -> None:
  """Blocks the main thread until Ctrl+C or a worker requests shutdown.

  `wait()` with a timeout keeps the main thread responsive to SIGINT.
  """
  try:
    while not stop_event.wait(config.MAIN_LOOP_WAIT_SECONDS):
      pass
  except KeyboardInterrupt:
    logger.info("Ctrl+C received, requesting shutdown")
  finally:
    stop_event.set()


def join_all(workers: list[BaseWorker], timeout: float) -> None:
  """Joins the given workers, each with a bounded timeout.

  Workers that were never started (e.g. a later `start()` failed) are
  skipped, since joining them would raise.
  """
  for worker in workers:
    if worker.ident is None:
      continue
    worker.join(timeout=timeout)
    if worker.is_alive():
      logger.warning("%s did not stop within timeout", worker.name)
    elif worker.error is not None:
      logger.error("%s failed: %r", worker.name, worker.error)


def format_summary(snapshot: InventorySnapshot, dock_size: int) -> str:
  """Formats the user-facing final summary from already-captured data."""
  stock_lines = [f"  {sku}: {units}"
                 for sku, units in sorted(snapshot.stock.items())]
  lines = [
      SUMMARY_TITLE,
      f"Packages registered: {snapshot.packages_received}",
      f"Units registered: {snapshot.units_received}",
      f"Packages left in dock: {dock_size}",
      "Final stock per SKU:",
      *(stock_lines or [SUMMARY_EMPTY_STOCK_LINE]),
  ]
  return "\n".join(lines)


def report(
    inventory: Inventory,
    dock: "queue.Queue[Package]",
    output: TextIO | None = None,
) -> None:
  """Prints the final summary and also records it in the log.

  The summary is user-facing output, so it always goes to `output`
  (stdout by default) regardless of the log level; it must run after the
  dashboard has stopped so no frame overwrites it.
  """
  snapshot = inventory.snapshot()
  dock_size = dock.qsize()
  print(format_summary(snapshot, dock_size), file=output or sys.stdout,
        flush=True)
  logger.info("Final stock: %s", dict(snapshot.stock))
  logger.info("Packages registered: %d | units: %d | left in dock: %d",
              snapshot.packages_received, snapshot.units_received, dock_size)


def shutdown(
    first_phase: list[BaseWorker],
    operators: list[Operator],
    producers_finished: threading.Event,
) -> None:
  """Two-phase shutdown: stop producers/readers, then drain the dock.

  Args:
    first_phase: Workers driven only by the stop event (trucks, dashboard,
      exporter, supervisor). Stopping the supervisor clears any active
      maintenance.
    operators: Consumers that keep running until the dock is empty.
    producers_finished: Signal that no more packages will be enqueued.

  Raises:
    KeyboardInterrupt: If a second Ctrl+C interrupts the first phase. The
      operators are still released, so they drain the dock and exit on
      their own instead of keeping the process alive.
  """
  try:
    join_all(first_phase, config.JOIN_TIMEOUT_SECONDS)
  finally:
    # Operators only exit once this is set and they are non-daemon threads,
    # so it must be set even if the joins above are interrupted.
    producers_finished.set()
  logger.info("Producers stopped, draining dock")
  join_all(operators, config.DRAIN_TIMEOUT_SECONDS)


def run_workers(
    first_phase: list[BaseWorker],
    operators: list[Operator],
    stop_event: threading.Event,
    producers_finished: threading.Event,
) -> None:
  """Starts every worker, waits for shutdown, then stops them in two phases.

  The shutdown runs even if starting a worker fails (e.g. the OS refuses a
  new thread), so the non-daemon threads already running never keep the
  process alive; the start error is re-raised afterwards.
  """
  try:
    for worker in [*first_phase, *operators]:
      worker.start()
    wait_for_shutdown(stop_event)
  finally:
    stop_event.set()
    shutdown(first_phase, operators, producers_finished)


def main(argv: list[str] | None = None) -> None:
  """Runs the logistics center simulation with settings from `argv`."""
  settings = parse_args(argv)
  configure_logging(settings)
  stop_event = threading.Event()
  producers_finished = threading.Event()
  maintenance_event = threading.Event()
  dock: "queue.Queue[Package]" = queue.Queue(maxsize=settings.queue_size)
  inventory = Inventory()
  trucks = build_trucks(settings, dock, stop_event, maintenance_event)
  operators = build_operators(settings, dock, inventory, stop_event,
                              producers_finished, maintenance_event)
  dashboard = Dashboard("Dashboard", inventory, dock, stop_event)
  exporter = ExportWorker("Exporter", inventory, dock, stop_event,
                          path=settings.export_path,
                          interval_seconds=settings.export_interval)
  supervisor = build_supervisor(settings, stop_event, maintenance_event)
  first_phase: list[BaseWorker] = [*trucks, dashboard, exporter]
  if supervisor is not None:
    first_phase.append(supervisor)
  run_workers(first_phase, operators, stop_event, producers_finished)
  # The periodic exporter is already stopped: persist the drained final state
  # so the file matches the final report.
  exporter.export()
  report(inventory, dock)


if __name__ == "__main__":
  main()
