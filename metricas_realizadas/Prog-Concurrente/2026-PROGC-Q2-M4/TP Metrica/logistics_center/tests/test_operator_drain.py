"""Drain-on-shutdown tests for Operator."""

import queue
import threading
import unittest

from core.inventory import Inventory
from core.package import Package
from workers.operator import Operator

PENDING_PACKAGES = 20
OPERATOR_COUNT = 3
NO_PROCESSING_DELAY = 0.0
JOIN_TIMEOUT_SECONDS = 5.0
ALIVE_CHECK_SECONDS = 0.2
TEST_SKU = "SKU-TEST"


class OperatorDrainTest(unittest.TestCase):
  """Operators must empty the dock before stopping."""

  def setUp(self) -> None:
    self.dock: "queue.Queue[Package]" = queue.Queue()
    self.inventory = Inventory()
    self.stop_event = threading.Event()
    self.producers_finished = threading.Event()

  def test_pending_packages_are_registered_after_shutdown(self) -> None:
    for _ in range(PENDING_PACKAGES):
      self.dock.put(Package(sku=TEST_SKU, units=1, batch_id="b"))
    self.stop_event.set()
    self.producers_finished.set()
    operators = [self._build_operator(index)
                 for index in range(OPERATOR_COUNT)]
    for operator in operators:
      operator.start()
    for operator in operators:
      operator.join(JOIN_TIMEOUT_SECONDS)
      self.assertFalse(operator.is_alive())
    self.assertEqual(self.inventory.snapshot().packages_received,
                     PENDING_PACKAGES)
    self.assertTrue(self.dock.empty())

  def test_keeps_running_until_producers_finish(self) -> None:
    operator = self._build_operator(0)
    operator.start()
    self.stop_event.set()
    operator.join(ALIVE_CHECK_SECONDS)
    self.assertTrue(operator.is_alive())
    self.producers_finished.set()
    operator.join(JOIN_TIMEOUT_SECONDS)
    self.assertFalse(operator.is_alive())

  def _build_operator(self, index: int) -> Operator:
    return Operator(f"Operator-{index}", self.dock, self.inventory,
                    self.stop_event, self.producers_finished,
                    processing_seconds=NO_PROCESSING_DELAY)


if __name__ == "__main__":
  unittest.main()
