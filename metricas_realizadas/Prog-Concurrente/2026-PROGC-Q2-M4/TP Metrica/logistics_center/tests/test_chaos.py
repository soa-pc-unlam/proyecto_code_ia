"""Chaos stress test: random maintenance pauses must never lose packages.

Real Truck/Operator/Supervisor threads run against a recording dock and a
recording inventory. Every package object is tracked by identity from the
moment it enters the dock until it is registered, so the conservation check
is exact: nothing lost, nothing registered twice.
"""

import itertools
import queue
import sys
import threading
import unittest
from collections import Counter

import config
import main
from core.inventory import Inventory
from core.package import Package
from workers.base import BaseWorker
from workers.operator import Operator
from workers.supervisor import Supervisor
from workers.truck import Truck

MANY_WORKERS = 100
FEW_WORKERS = 2
DOCK_CAPACITY = config.QUEUE_MAX_SIZE
TIGHT_DOCK_CAPACITY = 1
ROUNDS = 2
RUN_SECONDS = 1.0
NO_DELAY = 0.0
CHAOS_UPTIME_RANGE = (0.01, 0.05)
CHAOS_PAUSE_RANGE = (0.01, 0.05)
STUCK_PAUSE_RANGE = (60.0, 60.0)
# Lets packages flow before the stuck pause, so the drain check is not vacuous.
STUCK_UPTIME_RANGE = (0.5, 0.5)
SWITCH_INTERVAL_SECONDS = 1e-5
BARRIER_TIMEOUT_SECONDS = 10.0
PAUSE_DEADLINE_SECONDS = 2.0
MAX_REPORTED = 10
LOSSY_DROP_EVERY = 10

PROFILES = {
    "many_trucks_few_operators": (MANY_WORKERS, FEW_WORKERS, DOCK_CAPACITY),
    "few_trucks_many_operators": (FEW_WORKERS, MANY_WORKERS, DOCK_CAPACITY),
    "balanced_high_count": (MANY_WORKERS, MANY_WORKERS, DOCK_CAPACITY),
    "tight_dock": (MANY_WORKERS, MANY_WORKERS, TIGHT_DOCK_CAPACITY),
}


class RecordingDock(queue.Queue):
  """Dock queue that remembers every package actually enqueued.

  `_put` runs while the queue holds its own mutex, so recording is
  synchronized by the real lock under test, not by an extra one.
  """

  def __init__(self, maxsize: int) -> None:
    super().__init__(maxsize)
    self.enqueued: list[Package] = []

  def _put(self, item: Package) -> None:
    super()._put(item)
    self.enqueued.append(item)


class RecordingInventory(Inventory):
  """Inventory that also records the identity of each registered package."""

  def __init__(self) -> None:
    super().__init__()
    self._record_lock = threading.Lock()
    self.registered_ids: list[int] = []

  def register(self, package: Package) -> None:
    super().register(package)
    with self._record_lock:
      self.registered_ids.append(id(package))


class LossyOperator(Operator):
  """Negative control: silently drops every `LOSSY_DROP_EVERY`-th package.

  The drop depends only on how many packages were taken, never on thread
  timing, so any run that moves at least that many packages loses one.
  """

  _taken = itertools.count(start=1)
  _taken_lock = threading.Lock()

  def _step(self) -> None:
    try:
      package = self._dock.get(timeout=config.QUEUE_TIMEOUT_SECONDS)
    except queue.Empty:
      return
    try:
      with LossyOperator._taken_lock:
        position = next(LossyOperator._taken)
      if position % LOSSY_DROP_EVERY != 0:
        self._inventory.register(package)
    finally:
      self._dock.task_done()


def gated(worker_cls: type[BaseWorker]) -> type[BaseWorker]:
  """Returns a subclass that waits on a shared start barrier before running."""

  class Gated(worker_cls):
    def __init__(self, *args, barrier: threading.Barrier, **kwargs) -> None:
      super().__init__(*args, **kwargs)
      self._barrier = barrier

    def run(self) -> None:
      self._barrier.wait(BARRIER_TIMEOUT_SECONDS)
      super().run()

  return Gated


GatedTruck = gated(Truck)
GatedOperator = gated(Operator)
GatedLossyOperator = gated(LossyOperator)


class ChaosScenario:
  """Wires one simulation run: trucks, operators and a chaos supervisor."""

  def __init__(self, trucks: int, operators: int, capacity: int,
               pause_range: tuple[float, float] = CHAOS_PAUSE_RANGE,
               uptime_range: tuple[float, float] = CHAOS_UPTIME_RANGE,
               operator_cls: type[BaseWorker] = GatedOperator) -> None:
    self.dock = RecordingDock(capacity)
    self.inventory = RecordingInventory()
    self.stop_event = threading.Event()
    self.producers_finished = threading.Event()
    self.maintenance_event = threading.Event()
    self.barrier = threading.Barrier(trucks + operators)
    self.trucks = [self._build_truck(index) for index in range(trucks)]
    self.operators = [self._build_operator(index, operator_cls)
                      for index in range(operators)]
    self.supervisor = Supervisor(
        "Supervisor", self.stop_event, self.maintenance_event,
        uptime_range=uptime_range, pause_range=pause_range)

  @property
  def first_phase(self) -> list[BaseWorker]:
    return [*self.trucks, self.supervisor]

  @property
  def all_workers(self) -> list[BaseWorker]:
    return [*self.first_phase, *self.operators]

  def _build_truck(self, index: int) -> BaseWorker:
    return GatedTruck(f"Truck-{index}", self.dock, self.stop_event,
                      maintenance_event=self.maintenance_event,
                      arrival_seconds=NO_DELAY, barrier=self.barrier)

  def _build_operator(self, index: int, operator_cls) -> BaseWorker:
    return operator_cls(f"Operator-{index}", self.dock, self.inventory,
                        self.stop_event, self.producers_finished,
                        processing_seconds=NO_DELAY,
                        maintenance_event=self.maintenance_event,
                        barrier=self.barrier)


class ChaosStressTest(unittest.TestCase):
  """Random pauses must not lose, duplicate or strand any package."""

  def setUp(self) -> None:
    previous = sys.getswitchinterval()
    sys.setswitchinterval(SWITCH_INTERVAL_SECONDS)
    self.addCleanup(sys.setswitchinterval, previous)

  def test_random_pauses_do_not_lose_packages(self) -> None:
    for name, (trucks, operators, capacity) in PROFILES.items():
      for round_index in range(ROUNDS):
        with self.subTest(profile=name, round=round_index):
          scenario = ChaosScenario(trucks, operators, capacity)
          self._run(scenario)
          self._assert_healthy(scenario)

  def test_shutdown_during_pause_drains_dock(self) -> None:
    scenario = ChaosScenario(MANY_WORKERS, MANY_WORKERS, DOCK_CAPACITY,
                             pause_range=STUCK_PAUSE_RANGE,
                             uptime_range=STUCK_UPTIME_RANGE)
    self._run(scenario, stop_during_pause=True)
    self._assert_healthy(scenario)
    self.assertFalse(scenario.maintenance_event.is_set())

  def test_harness_detects_planted_loss(self) -> None:
    scenario = ChaosScenario(MANY_WORKERS, MANY_WORKERS, DOCK_CAPACITY,
                             operator_cls=GatedLossyOperator)
    self._run(scenario)
    self.assertGreaterEqual(len(scenario.dock.enqueued), LOSSY_DROP_EVERY)
    with self.assertRaises(AssertionError):
      self._assert_conservation(scenario)

  def _run(self, scenario: ChaosScenario,
           stop_during_pause: bool = False) -> None:
    for worker in scenario.all_workers:
      worker.start()
    scenario.stop_event.wait(RUN_SECONDS)
    if stop_during_pause:
      self.assertTrue(scenario.maintenance_event.wait(PAUSE_DEADLINE_SECONDS))
    scenario.stop_event.set()
    main.shutdown(scenario.first_phase, scenario.operators,
                  scenario.producers_finished)
    self._assert_all_stopped(scenario.all_workers)

  def _assert_all_stopped(self, workers: list[BaseWorker]) -> None:
    alive = [worker.name for worker in workers if worker.is_alive()]
    self.assertFalse(alive, f"Deadlock suspected: {alive[:MAX_REPORTED]}")

  def _assert_healthy(self, scenario: ChaosScenario) -> None:
    errors = [(w.name, w.error) for w in scenario.all_workers if w.error]
    self.assertFalse(errors, f"Worker errors: {errors[:MAX_REPORTED]}")
    self.assertFalse(scenario.barrier.broken, "Start barrier timed out")
    self.assertGreater(scenario.supervisor.pause_count, 0)
    self.assertTrue(scenario.dock.enqueued, "No package was produced")
    self._assert_conservation(scenario)

  def _assert_conservation(self, scenario: ChaosScenario) -> None:
    produced = Counter(id(package) for package in scenario.dock.enqueued)
    registered = Counter(scenario.inventory.registered_ids)
    duplicates = [key for key, count in registered.items() if count > 1]
    lost = produced.keys() - registered.keys()
    self.assertFalse(duplicates, f"Duplicated: {duplicates[:MAX_REPORTED]}")
    self.assertFalse(lost, f"Lost {len(lost)} of {len(produced)} packages")
    self.assertEqual(registered, produced)
    self.assertTrue(scenario.dock.empty())
    self._assert_totals(scenario)

  def _assert_totals(self, scenario: ChaosScenario) -> None:
    snapshot = scenario.inventory.snapshot()
    expected_units = sum(package.units for package in scenario.dock.enqueued)
    self.assertEqual(snapshot.packages_received, len(scenario.dock.enqueued))
    self.assertEqual(snapshot.units_received, expected_units)


if __name__ == "__main__":
  unittest.main()
