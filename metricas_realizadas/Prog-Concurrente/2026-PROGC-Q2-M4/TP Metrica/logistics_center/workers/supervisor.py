"""Chaos-monkey thread: randomly triggers global maintenance pauses."""

import logging
import random
import threading

import config
from workers.base import BaseWorker

logger = logging.getLogger(__name__)

SecondsRange = tuple[float, float]


class Supervisor(BaseWorker):
  """Randomly sets and clears the shared `maintenance_event`.

  Each step is one cycle: a random uptime, then a random maintenance window.
  Both waits use the stop event, so shutdown interrupts them immediately, and
  `_on_stop()` always clears the flag: a stopped (or crashed) supervisor can
  never leave the rest of the system paused.
  """

  def __init__(
      self,
      name: str,
      stop_event: threading.Event,
      maintenance_event: threading.Event,
      rng: random.Random | None = None,
      uptime_range: SecondsRange = config.SUPERVISOR_UPTIME_RANGE_SECONDS,
      pause_range: SecondsRange = config.SUPERVISOR_PAUSE_RANGE_SECONDS,
  ) -> None:
    super().__init__(name=name, stop_event=stop_event,
                     maintenance_event=maintenance_event)
    self._rng = rng or random.Random()
    self._uptime_range = uptime_range
    self._pause_range = pause_range
    self._pause_count = 0

  @property
  def pause_count(self) -> int:
    """Maintenance windows started so far (written only by this thread)."""
    return self._pause_count

  def _step(self) -> None:
    """Runs one uptime period followed by one maintenance window."""
    if self._stop_event.wait(self._random_seconds(self._uptime_range)):
      return
    self._maintenance_event.set()
    self._pause_count += 1
    logger.info("Maintenance started (#%d)", self._pause_count)
    self._stop_event.wait(self._random_seconds(self._pause_range))
    self._maintenance_event.clear()
    logger.info("Maintenance finished")

  def _random_seconds(self, seconds_range: SecondsRange) -> float:
    """Draws a uniform random duration within the given range."""
    return self._rng.uniform(*seconds_range)

  def _on_stop(self) -> None:
    """Guarantees no worker is left waiting on a stale maintenance flag."""
    self._maintenance_event.clear()
