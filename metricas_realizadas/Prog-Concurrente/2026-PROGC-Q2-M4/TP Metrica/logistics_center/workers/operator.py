"""Consumer/writer thread: operators that move packages into the inventory."""

import logging
import queue
import threading

import config
from core.inventory import Inventory
from core.package import Package
from workers.base import BaseWorker

logger = logging.getLogger(__name__)


class Operator(BaseWorker):
  """Takes packages from the dock queue and registers them in inventory.

  Two-phase shutdown: `stop_event` only skips the simulated processing delay,
  while the loop keeps running until `producers_finished` is set and the dock
  is empty, so no delivered package is lost.
  """

  def __init__(
      self,
      name: str,
      dock: "queue.Queue[Package]",
      inventory: Inventory,
      stop_event: threading.Event,
      producers_finished: threading.Event,
      processing_seconds: float = config.OPERATOR_PROCESSING_SECONDS,
      maintenance_event: threading.Event | None = None,
  ) -> None:
    super().__init__(name=name, stop_event=stop_event,
                     maintenance_event=maintenance_event)
    self._dock = dock
    self._inventory = inventory
    self._producers_finished = producers_finished
    self._processing_seconds = processing_seconds

  def _should_stop(self) -> bool:
    """Stops only when no producer can enqueue more and the dock is empty.

    Once producers are finished nobody puts into the dock, so `empty()`
    cannot flip back to False and the check is race-free.
    """
    return self._producers_finished.is_set() and self._dock.empty()

  def _step(self) -> None:
    """Processes one package, or returns on timeout to re-check shutdown.

    Holds before taking a package while maintenance is active, so a package
    is never left half-processed: once taken, it is always registered.
    """
    self._wait_while_paused()
    try:
      package = self._dock.get(timeout=config.QUEUE_TIMEOUT_SECONDS)
    except queue.Empty:
      return
    try:
      self._stop_event.wait(self._processing_seconds)
      self._inventory.register(package)
      logger.debug("Registered %s", package)
    finally:
      self._dock.task_done()
