"""Rendering and lifecycle tests for Dashboard."""

import io
import queue
import threading
import unittest

from core.inventory import Inventory
from core.package import Package
from ui.dashboard import CLEAR_SCREEN, Dashboard

TEST_SKU = "SKU-TEST"
TEST_UNITS = 7
PENDING_PACKAGES = 3
JOIN_TIMEOUT_SECONDS = 2.0


class DashboardTest(unittest.TestCase):
  """Dashboard must render a consistent frame and honor the stop event."""

  def setUp(self) -> None:
    self.dock: "queue.Queue[Package]" = queue.Queue()
    self.inventory = Inventory()
    self.stop_event = threading.Event()
    self.output = io.StringIO()
    self.dashboard = Dashboard("Dashboard", self.inventory, self.dock,
                               self.stop_event, output=self.output)

  def test_frame_shows_metrics_and_dock_depth(self) -> None:
    self.inventory.register(Package(sku=TEST_SKU, units=TEST_UNITS,
                                    batch_id="b"))
    for _ in range(PENDING_PACKAGES):
      self.dock.put(Package(sku=TEST_SKU, units=1, batch_id="b"))
    self.stop_event.set()
    self.dashboard._step()  # pylint: disable=protected-access
    frame = self.output.getvalue()
    self.assertTrue(frame.startswith(CLEAR_SCREEN))
    self.assertIn(f"{TEST_SKU}: {TEST_UNITS}", frame)
    self.assertIn(f"Dock queue size: {PENDING_PACKAGES}", frame)

  def test_stops_when_stop_event_is_set(self) -> None:
    self.dashboard.start()
    self.stop_event.set()
    self.dashboard.join(JOIN_TIMEOUT_SECONDS)
    self.assertFalse(self.dashboard.is_alive())
    self.assertIsNone(self.dashboard.error)


if __name__ == "__main__":
  unittest.main()
