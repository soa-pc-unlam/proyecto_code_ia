"""Tests for the command-line interface of the simulation."""

import contextlib
import dataclasses
import io
import queue
import threading
import unittest

import config
import main
from core.inventory import Inventory
from workers.supervisor import Supervisor


def parse_quietly(argv: list[str]) -> main.SimulationSettings:
  """Parses `argv` while swallowing argparse's usage/error output."""
  with contextlib.redirect_stderr(io.StringIO()):
    return main.parse_args(argv)


class ParseArgsDefaultsTest(unittest.TestCase):
  """Without arguments every setting must match the config constants."""

  def test_defaults_equal_config_values(self) -> None:
    settings = main.parse_args([])
    self.assertEqual(settings, main.SimulationSettings(
        trucks=config.TRUCK_COUNT,
        operators=config.OPERATOR_COUNT,
        queue_size=config.QUEUE_MAX_SIZE,
        processing_seconds=config.OPERATOR_PROCESSING_SECONDS,
        export_path=config.EXPORT_PATH,
        export_interval=config.EXPORT_INTERVAL_SECONDS,
        chaos=True,
        log_level=config.LOG_LEVEL,
        log_file=None,
    ))

  def test_default_log_level_is_warning(self) -> None:
    self.assertEqual(main.parse_args([]).log_level, "WARNING")

  def test_settings_are_frozen(self) -> None:
    settings = main.parse_args([])
    with self.assertRaises(dataclasses.FrozenInstanceError):
      settings.trucks = 99  # type: ignore[misc]


class ParseArgsOverridesTest(unittest.TestCase):
  """Every flag must override its default."""

  def test_overrides_are_applied(self) -> None:
    settings = main.parse_args([
        "--trucks", "4",
        "--operators", "6",
        "--queue-size", "10",
        "--processing-seconds", "0.05",
        "--export-path", "out.json",
        "--export-interval", "1.5",
        "--no-chaos",
        "--log-level", "debug",
        "--log-file", "sim.log",
    ])
    self.assertEqual(settings, main.SimulationSettings(
        trucks=4,
        operators=6,
        queue_size=10,
        processing_seconds=0.05,
        export_path="out.json",
        export_interval=1.5,
        chaos=False,
        log_level="DEBUG",
        log_file="sim.log",
    ))

  def test_parsing_does_not_mutate_config(self) -> None:
    before = (config.TRUCK_COUNT, config.QUEUE_MAX_SIZE)
    main.parse_args(["--trucks", "9", "--queue-size", "1"])
    self.assertEqual((config.TRUCK_COUNT, config.QUEUE_MAX_SIZE), before)


class ParseArgsValidationTest(unittest.TestCase):
  """Invalid values must make argparse exit with an error."""

  INVALID_ARGS = (
      ["--trucks", "0"],
      ["--trucks", "-1"],
      ["--trucks", "two"],
      ["--operators", "0"],
      ["--queue-size", "0"],
      ["--processing-seconds", "0"],
      ["--processing-seconds", "-0.5"],
      ["--processing-seconds", "nan"],
      ["--export-interval", "0"],
      ["--export-interval", "inf"],
      ["--log-level", "verbose"],
  )

  def test_invalid_values_exit(self) -> None:
    for argv in self.INVALID_ARGS:
      with self.subTest(argv=argv):
        with self.assertRaises(SystemExit) as raised:
          parse_quietly(argv)
        self.assertEqual(raised.exception.code, 2)


class BuildWorkersTest(unittest.TestCase):
  """Factories must honor the settings instead of the config constants."""

  def setUp(self) -> None:
    self.stop_event = threading.Event()
    self.maintenance_event = threading.Event()

  def test_no_chaos_skips_the_supervisor(self) -> None:
    settings = main.parse_args(["--no-chaos"])
    self.assertIsNone(main.build_supervisor(
        settings, self.stop_event, self.maintenance_event))

  def test_chaos_builds_the_supervisor_by_default(self) -> None:
    settings = main.parse_args([])
    self.assertIsInstance(main.build_supervisor(
        settings, self.stop_event, self.maintenance_event), Supervisor)

  def test_worker_counts_follow_settings(self) -> None:
    settings = main.parse_args(["--trucks", "4", "--operators", "5"])
    dock: queue.Queue = queue.Queue()
    trucks = main.build_trucks(settings, dock, self.stop_event,
                               self.maintenance_event)
    operators = main.build_operators(settings, dock, Inventory(),
                                     self.stop_event, threading.Event(),
                                     self.maintenance_event)
    self.assertEqual(len(trucks), 4)
    self.assertEqual(len(operators), 5)


if __name__ == "__main__":
  unittest.main()
