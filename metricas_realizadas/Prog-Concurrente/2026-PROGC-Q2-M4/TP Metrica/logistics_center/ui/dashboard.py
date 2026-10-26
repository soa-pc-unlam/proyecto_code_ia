"""Reader thread: periodically renders live metrics to the terminal."""

import queue
import sys
import threading
from typing import TextIO

import config
from core.inventory import Inventory, InventorySnapshot
from core.package import Package
from workers.base import BaseWorker

# ANSI escape: erase the whole screen and move the cursor to the top-left.
CLEAR_SCREEN = "\033[2J\033[H"
TITLE = "=== Logistics Center Dashboard ==="
EMPTY_STOCK_LINE = "  (no stock yet)"


class Dashboard(BaseWorker):
  """Read-only observer of the inventory and the dock queue.

  Locks are held only inside `Inventory.snapshot()` and `Queue.qsize()`;
  formatting and terminal I/O happen afterwards on immutable data, so a slow
  terminal never blocks writers.
  """

  def __init__(
      self,
      name: str,
      inventory: Inventory,
      dock: "queue.Queue[Package]",
      stop_event: threading.Event,
      output: TextIO | None = None,
      refresh_seconds: float = config.DASHBOARD_REFRESH_SECONDS,
  ) -> None:
    super().__init__(name=name, stop_event=stop_event)
    self._inventory = inventory
    self._dock = dock
    self._output = output or sys.stdout
    self._refresh_seconds = refresh_seconds

  def _step(self) -> None:
    """Captures metrics, renders one frame, then waits interruptibly."""
    snapshot = self._inventory.snapshot()
    dock_size = self._dock.qsize()
    self._output.write(CLEAR_SCREEN + render(snapshot, dock_size))
    self._output.flush()
    self._stop_event.wait(self._refresh_seconds)


def render(snapshot: InventorySnapshot, dock_size: int) -> str:
  """Formats one dashboard frame from already-captured data."""
  lines = [
      TITLE,
      f"Packages received: {snapshot.packages_received}",
      f"Units received: {snapshot.units_received}",
      f"Dock queue size: {dock_size}",
      "Stock per SKU:",
      *_stock_lines(snapshot),
  ]
  return "\n".join(lines) + "\n"


def _stock_lines(snapshot: InventorySnapshot) -> list[str]:
  """One line per SKU, sorted for a stable layout between frames."""
  if not snapshot.stock:
    return [EMPTY_STOCK_LINE]
  return [f"  {sku}: {units}" for sku, units in sorted(snapshot.stock.items())]
