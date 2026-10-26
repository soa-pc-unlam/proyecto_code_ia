"""Thread-safe central inventory shared by writers and readers."""

import threading
from collections import Counter
from dataclasses import dataclass
from types import MappingProxyType
from typing import Mapping

from core.package import Package


@dataclass(frozen=True, slots=True)
class InventorySnapshot:
  """Immutable, consistent point-in-time view of the inventory.

  Attributes:
    stock: Units in stock per SKU (read-only mapping).
    packages_received: Total packages registered so far.
    units_received: Total units registered so far.
  """

  stock: Mapping[str, int]
  packages_received: int
  units_received: int


class Inventory:
  """Encapsulates the shared stock and statistics behind a single lock.

  Every read and write goes through the same lock, so statistics and stock
  are always mutually consistent. Readers never receive internal references:
  they get an immutable snapshot copied while the lock is held.
  """

  def __init__(self) -> None:
    self._lock = threading.Lock()
    self._stock: Counter[str] = Counter()
    self._packages_received = 0
    self._units_received = 0

  def register(self, package: Package) -> None:
    """Adds a package to the stock and updates statistics atomically.

    Args:
      package: Package extracted from the dock queue.

    Raises:
      ValueError: If the package carries a non-positive amount of units.
    """
    if package.units <= 0:
      raise ValueError(f"Invalid units for package: {package!r}")
    with self._lock:
      self._stock[package.sku] += package.units
      self._packages_received += 1
      self._units_received += package.units

  def snapshot(self) -> InventorySnapshot:
    """Returns a consistent, immutable copy of the current state."""
    with self._lock:
      return InventorySnapshot(
          stock=MappingProxyType(dict(self._stock)),
          packages_received=self._packages_received,
          units_received=self._units_received,
      )

  def units_of(self, sku: str) -> int:
    """Returns the units in stock for a given SKU (0 if unknown)."""
    with self._lock:
      return self._stock[sku]
