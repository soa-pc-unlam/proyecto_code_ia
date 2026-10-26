"""Concurrency tests for the shared inventory."""

import threading
import unittest

from core.inventory import Inventory
from core.package import Package

WRITER_THREADS = 8
PACKAGES_PER_WRITER = 2_000
UNITS_PER_PACKAGE = 3
TEST_SKU = "SKU-TEST"


class InventoryConcurrencyTest(unittest.TestCase):
  """Verifies that concurrent writers never lose updates."""

  def test_concurrent_registers_keep_totals_consistent(self) -> None:
    inventory = Inventory()
    package = Package(sku=TEST_SKU, units=UNITS_PER_PACKAGE, batch_id="b")
    threads = [
        threading.Thread(target=self._register_many, args=(inventory, package))
        for _ in range(WRITER_THREADS)
    ]
    for thread in threads:
      thread.start()
    for thread in threads:
      thread.join()
    expected_packages = WRITER_THREADS * PACKAGES_PER_WRITER
    snapshot = inventory.snapshot()
    self.assertEqual(snapshot.packages_received, expected_packages)
    self.assertEqual(snapshot.stock[TEST_SKU],
                     expected_packages * UNITS_PER_PACKAGE)

  def test_snapshot_is_immutable(self) -> None:
    inventory = Inventory()
    inventory.register(Package(sku=TEST_SKU, units=1, batch_id="b"))
    with self.assertRaises(TypeError):
      inventory.snapshot().stock[TEST_SKU] = 0  # type: ignore[index]

  def test_rejects_non_positive_units(self) -> None:
    with self.assertRaises(ValueError):
      Inventory().register(Package(sku=TEST_SKU, units=0, batch_id="b"))

  @staticmethod
  def _register_many(inventory: Inventory, package: Package) -> None:
    for _ in range(PACKAGES_PER_WRITER):
      inventory.register(package)


if __name__ == "__main__":
  unittest.main()
