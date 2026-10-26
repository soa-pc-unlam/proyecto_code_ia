"""Persistence thread: periodically exports an inventory report to JSON."""

import contextlib
import json
import logging
import os
import queue
import tempfile
import threading
import time
from datetime import datetime
from typing import Any

import config
from core.inventory import Inventory, InventorySnapshot
from core.package import Package
from workers.base import BaseWorker

logger = logging.getLogger(__name__)

TEMP_SUFFIX = ".tmp"


class ExportWorker(BaseWorker):
  """Read-only observer that persists the inventory state to disk.

  The inventory lock is held only inside `Inventory.snapshot()`; building the
  payload, JSON serialization and disk I/O all happen afterwards on immutable
  data, so a slow disk never blocks the operators. Every write is atomic, so
  readers of the report always see either the previous or the new version.
  """

  def __init__(
      self,
      name: str,
      inventory: Inventory,
      dock: "queue.Queue[Package]",
      stop_event: threading.Event,
      path: str | os.PathLike[str] = config.EXPORT_PATH,
      interval_seconds: float = config.EXPORT_INTERVAL_SECONDS,
  ) -> None:
    super().__init__(name=name, stop_event=stop_event)
    self._inventory = inventory
    self._dock = dock
    self._path = os.fspath(path)
    self._interval_seconds = interval_seconds

  def _step(self) -> None:
    """Exports one report, then waits interruptibly for the next interval."""
    self.export()
    self._stop_event.wait(self._interval_seconds)

  def export(self) -> bool:
    """Captures the current state and writes it atomically to the target.

    Safe to call from another thread once this worker has stopped (e.g. for
    a final export after the dock has been drained). Persistence is a side
    concern: an I/O failure is logged and the cycle skipped, so it never
    stops the simulation.

    Returns:
      True if the report was written, False if this cycle was skipped.
    """
    snapshot = self._inventory.snapshot()
    dock_size = self._dock.qsize()
    try:
      write_json_atomic(self._path, build_report(snapshot, dock_size))
    except OSError as exc:
      logger.warning("Export to %s skipped: %r", self._path, exc)
      return False
    logger.debug("Report exported to %s", self._path)
    return True


def build_report(
    snapshot: InventorySnapshot,
    dock_size: int,
    now: datetime | None = None,
) -> dict[str, Any]:
  """Builds the JSON-serializable report from already-captured data.

  Args:
    snapshot: Immutable inventory state.
    dock_size: Packages waiting in the dock queue.
    now: Report timestamp; defaults to the current local time.

  Returns:
    A dict with an ISO 8601 timestamp, totals, stock per SKU and dock size.
  """
  timestamp = now or datetime.now().astimezone()
  return {
      "timestamp": timestamp.isoformat(),
      "packages_received": snapshot.packages_received,
      "units_received": snapshot.units_received,
      "stock": dict(sorted(snapshot.stock.items())),
      "dock_size": dock_size,
  }


def write_json_atomic(path: str, payload: dict[str, Any]) -> None:
  """Writes `payload` as JSON to `path` so readers never see a partial file.

  The data goes to a temp file in the same directory (same filesystem, so
  the rename is atomic), is flushed and fsynced, and is then swapped onto the
  target with `os.replace()`. The temp file is removed on any failure.

  Args:
    path: Destination file.
    payload: JSON-serializable data.

  Raises:
    OSError: If writing or replacing fails.
    TypeError: If `payload` is not JSON-serializable.
  """
  directory = os.path.dirname(os.path.abspath(path))
  prefix = f".{os.path.basename(path)}."
  temp_path: str | None = None
  try:
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", dir=directory, prefix=prefix,
        suffix=TEMP_SUFFIX, delete=False) as temp_file:
      temp_path = temp_file.name
      json.dump(payload, temp_file, indent=2)
      temp_file.flush()
      os.fsync(temp_file.fileno())
    # Replaced only after closing: Windows cannot rename an open file.
    _replace_with_retry(temp_path, path)
  except BaseException:
    if temp_path is not None:
      with contextlib.suppress(FileNotFoundError):
        os.remove(temp_path)
    raise


def _replace_with_retry(source: str, target: str) -> None:
  """`os.replace()` that tolerates transient Windows sharing violations.

  On Windows the swap fails with `PermissionError` while another thread or
  process has the target open; the reader closes it quickly, so a short,
  bounded exponential backoff is enough. On POSIX the first attempt always
  succeeds.

  Raises:
    PermissionError: If the target stays locked after every attempt.
  """
  delay = config.EXPORT_REPLACE_BACKOFF_SECONDS
  for _ in range(config.EXPORT_REPLACE_ATTEMPTS - 1):
    try:
      os.replace(source, target)
      return
    except PermissionError:
      time.sleep(delay)
      delay *= 2
  os.replace(source, target)
