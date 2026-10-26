"""Tests for the ExportWorker JSON persistence."""

import json
import os
import queue
import tempfile
import threading
import time
import unittest
from datetime import datetime
from unittest import mock

import config
from core.inventory import Inventory
from core.package import Package
from tests.test_maintenance_pause import wait_until
from workers.exporter import ExportWorker

REPORT_NAME = "report.json"
EXPECTED_KEYS = {"timestamp", "packages_received", "units_received", "stock",
                 "dock_size"}
NO_DELAY = 0.0
LONG_INTERVAL_SECONDS = 60.0
FIRST_EXPORT_DEADLINE_SECONDS = 2.0
PROMPT_STOP_SECONDS = 1.0
JOIN_TIMEOUT_SECONDS = 5.0
STRESS_DURATION_SECONDS = 1.0
FAST_INTERVAL_SECONDS = 0.01
FAILING_CYCLES = 3


class ExportWorkerTest(unittest.TestCase):
  """The exporter must write valid, atomic reports and stop promptly."""

  def setUp(self) -> None:
    temp_dir = tempfile.TemporaryDirectory()
    self.addCleanup(temp_dir.cleanup)
    self.directory = temp_dir.name
    self.path = os.path.join(self.directory, REPORT_NAME)
    self.inventory = Inventory()
    self.dock: "queue.Queue[Package]" = queue.Queue()
    self.stop_event = threading.Event()

  def test_report_contains_expected_keys_and_values(self) -> None:
    self.inventory.register(Package("SKU-A", 3, "B1"))
    self.inventory.register(Package("SKU-B", 4, "B1"))
    self.inventory.register(Package("SKU-A", 2, "B2"))
    self.dock.put(Package("SKU-C", 1, "B3"))

    self._build_exporter(LONG_INTERVAL_SECONDS).export()

    with open(self.path, encoding="utf-8") as handle:
      report = json.load(handle)
    self.assertEqual(set(report), EXPECTED_KEYS)
    self.assertEqual(report["packages_received"], 3)
    self.assertEqual(report["units_received"], 9)
    self.assertEqual(report["stock"], {"SKU-A": 5, "SKU-B": 4})
    self.assertEqual(report["dock_size"], 1)
    self.assertIsNotNone(
        datetime.fromisoformat(report["timestamp"]).tzinfo)
    self.assertEqual(os.listdir(self.directory), [REPORT_NAME])

  def test_concurrent_reader_never_sees_partial_json(self) -> None:
    exporter = self._build_exporter(NO_DELAY)
    writer_stop = threading.Event()
    writer = threading.Thread(target=self._register_until,
                              args=(writer_stop,))
    exporter.start()
    writer.start()

    successful_reads, invalid_reads = self._read_repeatedly(
        STRESS_DURATION_SECONDS)

    writer_stop.set()
    writer.join(JOIN_TIMEOUT_SECONDS)
    self._stop(exporter)
    self.assertEqual(invalid_reads, [])
    self.assertGreater(successful_reads, 0)
    self.assertEqual(os.listdir(self.directory), [REPORT_NAME])

  def test_stop_mid_interval_returns_promptly(self) -> None:
    exporter = self._build_exporter(LONG_INTERVAL_SECONDS)
    exporter.start()
    self.assertTrue(wait_until(lambda: os.path.exists(self.path),
                               FIRST_EXPORT_DEADLINE_SECONDS))

    started = time.monotonic()
    self.stop_event.set()
    exporter.join(PROMPT_STOP_SECONDS)
    elapsed = time.monotonic() - started

    self.assertFalse(exporter.is_alive())
    self.assertLess(elapsed, PROMPT_STOP_SECONDS)
    self.assertIsNone(exporter.error)

  def test_locked_target_skips_cycle_and_keeps_running(self) -> None:
    exporter = self._build_exporter(FAST_INTERVAL_SECONDS)
    with mock.patch("workers.exporter.os.replace",
                    side_effect=PermissionError("locked")) as replace,         self.assertLogs("workers.exporter", level="WARNING"):
      exporter.start()
      expected_calls = FAILING_CYCLES * config.EXPORT_REPLACE_ATTEMPTS
      self.assertTrue(wait_until(
          lambda: replace.call_count >= expected_calls,
          FIRST_EXPORT_DEADLINE_SECONDS))
      self.assertTrue(exporter.is_alive())
      self.assertIsNone(exporter.error)
      self.assertFalse(self.stop_event.is_set())

    self.assertTrue(wait_until(lambda: os.path.exists(self.path),
                               FIRST_EXPORT_DEADLINE_SECONDS))
    self._stop(exporter)
    self.assertEqual(os.listdir(self.directory), [REPORT_NAME])

  def _build_exporter(self, interval_seconds: float) -> ExportWorker:
    return ExportWorker("Exporter-T", self.inventory, self.dock,
                        self.stop_event, path=self.path,
                        interval_seconds=interval_seconds)

  def _register_until(self, writer_stop: threading.Event) -> None:
    """Keeps mutating the inventory so consecutive reports differ."""
    while not writer_stop.is_set():
      self.inventory.register(Package("SKU-A", 1, "B-STRESS"))

  def _read_repeatedly(self, duration: float) -> tuple[int, list[str]]:
    """Loads the report in a loop, collecting any undecodable content.

    `FileNotFoundError` (not created yet) and `PermissionError` (Windows,
    target being swapped) mean the file is temporarily unavailable and are
    retried. Only content that was actually read must always be valid JSON.
    """
    successful_reads = 0
    invalid_reads: list[str] = []
    deadline = time.monotonic() + duration
    while time.monotonic() < deadline:
      try:
        with open(self.path, encoding="utf-8") as handle:
          content = handle.read()
      except (PermissionError, FileNotFoundError):
        continue  # Temporarily unavailable: not yet created or being swapped.
      try:
        json.loads(content)
        successful_reads += 1
      except json.JSONDecodeError:
        invalid_reads.append(content)
    return successful_reads, invalid_reads

  def _stop(self, exporter: ExportWorker) -> None:
    self.stop_event.set()
    exporter.join(JOIN_TIMEOUT_SECONDS)
    self.assertFalse(exporter.is_alive())
    self.assertIsNone(exporter.error)


if __name__ == "__main__":
  unittest.main()
