"""Tests for the chaos-monkey Supervisor."""

import random
import threading
import unittest

from tests.test_maintenance_pause import wait_until
from workers.supervisor import Supervisor

FAST_RANGE = (0.01, 0.03)
LONG_PAUSE_RANGE = (60.0, 60.0)
DEADLINE_SECONDS = 2.0
JOIN_TIMEOUT_SECONDS = 2.0
MIN_EXPECTED_PAUSES = 3
SEED = 42


class SupervisorTest(unittest.TestCase):
  """The supervisor must toggle maintenance and never leave it set."""

  def setUp(self) -> None:
    self.stop_event = threading.Event()
    self.maintenance_event = threading.Event()

  def test_toggles_maintenance_repeatedly(self) -> None:
    supervisor = self._build_supervisor(FAST_RANGE)
    supervisor.start()
    self.assertTrue(wait_until(
        lambda: supervisor.pause_count >= MIN_EXPECTED_PAUSES,
        DEADLINE_SECONDS))
    self._stop(supervisor)

  def test_clears_maintenance_when_stopped_mid_pause(self) -> None:
    supervisor = self._build_supervisor(LONG_PAUSE_RANGE)
    supervisor.start()
    self.assertTrue(wait_until(self.maintenance_event.is_set,
                               DEADLINE_SECONDS))
    self._stop(supervisor)
    self.assertFalse(self.maintenance_event.is_set())

  def test_routine_pauses_stay_below_warning(self) -> None:
    # Warnings reach stderr by default and would break the dashboard.
    supervisor = self._build_supervisor(FAST_RANGE)
    with self.assertNoLogs("workers.supervisor", level="WARNING"):
      supervisor.start()
      self.assertTrue(wait_until(
          lambda: supervisor.pause_count >= MIN_EXPECTED_PAUSES,
          DEADLINE_SECONDS))
      self._stop(supervisor)

  def _build_supervisor(self, pause_range: tuple[float, float]) -> Supervisor:
    return Supervisor("Supervisor-T", self.stop_event, self.maintenance_event,
                      rng=random.Random(SEED), uptime_range=FAST_RANGE,
                      pause_range=pause_range)

  def _stop(self, supervisor: Supervisor) -> None:
    self.stop_event.set()
    supervisor.join(JOIN_TIMEOUT_SECONDS)
    self.assertFalse(supervisor.is_alive())
    self.assertIsNone(supervisor.error)


if __name__ == "__main__":
  unittest.main()
