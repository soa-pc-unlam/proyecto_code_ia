"""Lifecycle tests for BaseWorker."""

import threading
import unittest

from workers.base import BaseWorker

JOIN_TIMEOUT_SECONDS = 2.0
STEP_WAIT_SECONDS = 0.01


class _FailingWorker(BaseWorker):
  def _step(self) -> None:
    raise RuntimeError("boom")


class _IdleWorker(BaseWorker):
  def _step(self) -> None:
    self._stop_event.wait(STEP_WAIT_SECONDS)


class BaseWorkerTest(unittest.TestCase):
  """Verifies graceful shutdown and fail-fast error propagation."""

  def test_unhandled_error_is_stored_and_triggers_shutdown(self) -> None:
    stop_event = threading.Event()
    worker = _FailingWorker("failing", stop_event)
    with self.assertLogs("workers.base", level="ERROR"):
      worker.start()
      worker.join(JOIN_TIMEOUT_SECONDS)
    self.assertFalse(worker.is_alive())
    self.assertIsInstance(worker.error, RuntimeError)
    self.assertTrue(stop_event.is_set())

  def test_stop_event_ends_worker(self) -> None:
    stop_event = threading.Event()
    worker = _IdleWorker("idle", stop_event)
    worker.start()
    stop_event.set()
    worker.join(JOIN_TIMEOUT_SECONDS)
    self.assertFalse(worker.is_alive())
    self.assertIsNone(worker.error)


if __name__ == "__main__":
  unittest.main()
