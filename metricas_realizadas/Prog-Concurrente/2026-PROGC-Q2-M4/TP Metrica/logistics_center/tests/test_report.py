"""Tests for the user-facing final summary printed by main.report()."""

import contextlib
import io
import logging
import queue
import unittest

import main
from core.inventory import Inventory
from core.package import Package


class ReportTest(unittest.TestCase):
  """The final summary is output, not a log: it must ignore the log level."""

  def setUp(self) -> None:
    self.inventory = Inventory()
    self.inventory.register(Package(sku="SKU-B", units=4, batch_id="b-1"))
    self.inventory.register(Package(sku="SKU-A", units=3, batch_id="b-1"))
    self.inventory.register(Package(sku="SKU-B", units=2, batch_id="b-2"))
    self.dock: "queue.Queue[Package]" = queue.Queue()
    self.dock.put(Package(sku="SKU-C", units=1, batch_id="b-3"))

  def test_prints_summary_even_when_logging_is_silenced(self) -> None:
    logging.disable(logging.CRITICAL)
    self.addCleanup(logging.disable, logging.NOTSET)
    output = io.StringIO()

    main.report(self.inventory, self.dock, output=output)

    self.assertEqual(output.getvalue(), "\n".join([
        main.SUMMARY_TITLE,
        "Packages registered: 3",
        "Units registered: 9",
        "Packages left in dock: 1",
        "Final stock per SKU:",
        "  SKU-A: 3",
        "  SKU-B: 6",
    ]) + "\n")

  def test_defaults_to_stdout(self) -> None:
    stdout = io.StringIO()
    with contextlib.redirect_stdout(stdout):
      main.report(self.inventory, self.dock)
    self.assertIn("Packages registered: 3", stdout.getvalue())

  def test_still_logs_summary_at_info(self) -> None:
    with self.assertLogs("main", level="INFO") as logs:
      main.report(self.inventory, self.dock, output=io.StringIO())
    self.assertTrue(any("left in dock: 1" in line for line in logs.output))

  def test_empty_inventory_shows_placeholder(self) -> None:
    output = io.StringIO()
    main.report(Inventory(), queue.Queue(), output=output)
    self.assertIn(main.SUMMARY_EMPTY_STOCK_LINE, output.getvalue())


if __name__ == "__main__":
  unittest.main()
