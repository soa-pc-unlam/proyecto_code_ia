"""Maintenance pause tests for Truck and Operator."""

import queue
import threading
import time
import unittest

from core.inventory import Inventory
from core.package import Package
from workers.operator import Operator
from workers.truck import Truck

PAUSED_OBSERVATION_SECONDS = 0.3
RESUME_DEADLINE_SECONDS = 2.0
POLL_SECONDS = 0.02
JOIN_TIMEOUT_SECONDS = 5.0
NO_DELAY = 0.0
PENDING_PACKAGES = 5
TEST_SKU = "SKU-TEST"


def wait_until(predicate, deadline_seconds: float) -> bool:
  """Polls `predicate` until it holds or the deadline expires."""
  deadline = time.monotonic() + deadline_seconds
  while time.monotonic() < deadline:
    if predicate():
      return True
    time.sleep(POLL_SECONDS)
  return predicate()


class MaintenancePauseTest(unittest.TestCase):
  """Workers must hold while maintenance is active and resume afterwards."""

  def setUp(self) -> None:
    self.dock: "queue.Queue[Package]" = queue.Queue()
    self.inventory = Inventory()
    self.stop_event = threading.Event()
    self.producers_finished = threading.Event()
    self.maintenance_event = threading.Event()

  def test_operator_holds_while_paused_and_resumes(self) -> None:
    self._fill_dock(PENDING_PACKAGES)
    self.maintenance_event.set()
    operator = self._build_operator()
    operator.start()
    time.sleep(PAUSED_OBSERVATION_SECONDS)
    self.assertEqual(self.dock.qsize(), PENDING_PACKAGES)
    self.maintenance_event.clear()
    self.producers_finished.set()
    operator.join(JOIN_TIMEOUT_SECONDS)
    self.assertFalse(operator.is_alive())
    self.assertEqual(self.inventory.snapshot().packages_received,
                     PENDING_PACKAGES)

  def test_truck_holds_while_paused_and_resumes(self) -> None:
    self.maintenance_event.set()
    truck = Truck("Truck-T", self.dock, self.stop_event,
                  maintenance_event=self.maintenance_event,
                  arrival_seconds=NO_DELAY)
    truck.start()
    time.sleep(PAUSED_OBSERVATION_SECONDS)
    self.assertTrue(self.dock.empty())
    self.maintenance_event.clear()
    self.assertTrue(wait_until(lambda: not self.dock.empty(),
                               RESUME_DEADLINE_SECONDS))
    self.stop_event.set()
    truck.join(JOIN_TIMEOUT_SECONDS)
    self.assertFalse(truck.is_alive())

  def test_shutdown_overrides_stuck_pause(self) -> None:
    self._fill_dock(PENDING_PACKAGES)
    self.maintenance_event.set()
    self.stop_event.set()
    self.producers_finished.set()
    operator = self._build_operator()
    operator.start()
    operator.join(JOIN_TIMEOUT_SECONDS)
    self.assertFalse(operator.is_alive())
    self.assertTrue(self.dock.empty())

  def _fill_dock(self, count: int) -> None:
    for _ in range(count):
      self.dock.put(Package(sku=TEST_SKU, units=1, batch_id="b"))

  def _build_operator(self) -> Operator:
    return Operator("Operator-T", self.dock, self.inventory, self.stop_event,
                    self.producers_finished, processing_seconds=NO_DELAY,
                    maintenance_event=self.maintenance_event)


if __name__ == "__main__":
  unittest.main()
