from threading import Event
from threading import enumerate as threads
from time import monotonic, sleep

from maze_chase.ai import Request, Result, Snapshot
from maze_chase.application import Controller
from maze_chase.domain import Direction, Game, Ghost, State
from maze_chase.execution import Concurrent, Sequential


def request(tick=1, ghost=Ghost.BLINKY):
    game = Game()
    return Request(
        ghost,
        Snapshot(tick, game.board, game.player, Direction.LEFT, tuple(game.ghosts.values()), 0),
    )


def wait_until(predicate, timeout=2):
    deadline = monotonic() + timeout
    while monotonic() < deadline:
        if predicate():
            return
        sleep(0.002)
    assert predicate(), "Se agotó el plazo de la prueba"


def test_four_workers_equal_sequential_and_close():
    concurrent, sequential = Concurrent(), Sequential()
    try:
        assert len(concurrent.workers) == 4
        assert all(w.is_alive() for w in concurrent.workers.values())
        for ghost in Ghost:
            concurrent.submit(request(ghost=ghost))
            sequential.submit(request(ghost=ghost))
        results = []

        def complete():
            results.extend(concurrent.collect())
            return len(results) == 4

        wait_until(complete)
        assert {(r.ghost, r.direction) for r in results} == {
            (r.ghost, r.direction) for r in sequential.collect()
        }
    finally:
        assert concurrent.close()
    assert not any(t.name.startswith("ghost-") for t in threads())


def test_slow_workers_dont_block_ticks_and_replace_old_requests():
    gate, entered = Event(), Event()

    def slow(_):
        entered.set()
        gate.wait(2)
        return Direction.STOP

    executor = Concurrent(slow)
    controller = Controller(executor)
    controller.game.state = State.RUNNING
    try:
        controller.tick()
        assert entered.wait(1)
        start = monotonic()
        for _ in range(100):
            controller.tick()
        assert monotonic() - start < 0.5
        assert executor.replaced > 0
        assert controller.counts.missed > 0
        assert all(w.tasks.qsize() <= 1 for w in executor.workers.values())
        gate.set()
    finally:
        gate.set()
        assert executor.close()


def test_stale_duplicate_invalid_and_restart_generation():
    executor = Sequential()
    c = Controller(executor)
    c.game.state = State.RUNNING
    c.tick()
    tick = c.pending
    executor.results = [
        Result(Ghost.BLINKY, tick - 1, Direction.STOP, 0),
        Result(Ghost.BLINKY, tick, Direction.STOP, 0),
        Result(Ghost.BLINKY, tick, Direction.STOP, 0),
    ]
    c.poll()
    assert c.counts.stale == 1 and c.counts.duplicates == 1
    c.game.ghosts[Ghost.BLINKY] = (1, 1)
    c.ready[Ghost.BLINKY] = Result(Ghost.BLINKY, tick, Direction.UP, 0)
    c.tick()
    assert c.counts.invalid == 1
    c.restart()
    executor.results.append(Result(Ghost.BLINKY, tick, Direction.RIGHT, 0))
    c.poll()
    assert not c.ready and c.pending is None
    assert c.generation > tick


def test_worker_exceptions_are_messages_and_worker_survives():
    def fail(_):
        raise ValueError("injected")

    executor = Concurrent(fail)
    try:
        executor.submit(request())
        results = []

        def complete():
            results.extend(executor.collect())
            return bool(results)

        wait_until(complete)
        assert not results[0].success and "injected" in results[0].error
        assert executor.workers[Ghost.BLINKY].is_alive()
    finally:
        assert executor.close()
    assert sum(s.errors for s in executor.statistics()) == 1


def test_full_output_queue_never_blocks_shutdown():
    executor = Concurrent()
    worker = executor.workers[Ghost.BLINKY]
    for _ in range(2):
        worker.results.put_nowait(Result(Ghost.BLINKY, 0, Direction.STOP, 0))
    executor.submit(request())
    wait_until(lambda: worker.tasks.empty())
    sleep(0.03)
    assert executor.close()
    assert worker.stats.result_drops == 1


def test_pause_freezes_and_discards_pending_results():
    c = Controller(Sequential())
    c.toggle()
    c.tick()
    position = c.game.player
    c.toggle()
    c.tick()
    assert c.game.state is State.PAUSED and c.game.player == position
    assert c.pending is None and not c.ready


def test_arrival_order_does_not_change_application():
    controllers = [Controller(Sequential()), Controller(Sequential())]
    for c in controllers:
        c.game.state = State.RUNNING
        c.tick()
    controllers[1].executor.results.reverse()
    for c in controllers:
        c.tick()
    assert controllers[0].game == controllers[1].game


def test_sequential_creates_no_workers():
    before = set(threads())
    executor = Sequential()
    executor.submit(request())
    assert set(threads()) == before
    assert executor.close()


def test_complete_trajectory_matches_when_all_decisions_arrive():
    concurrent = Concurrent()
    controllers = [Controller(Sequential()), Controller(concurrent)]
    try:
        for c in controllers:
            c.game.state = State.RUNNING
        for step in range(60):
            for c in controllers:
                if c.game.state in (State.LOST, State.WON):
                    c.restart()
                c.game.requested = (Direction.LEFT, Direction.UP, Direction.RIGHT)[(step // 8) % 3]
                c.tick()
            assert controllers[0].game == controllers[1].game
            if controllers[1].pending is not None:

                def complete():
                    controllers[1].poll()
                    return len(controllers[1].ready) == 4

                wait_until(complete)
        assert controllers[1].counts.missed == 0
    finally:
        assert concurrent.close()


def test_noncooperative_strategy_reports_failed_bounded_shutdown():
    gate, entered = Event(), Event()

    def stuck(_):
        entered.set()
        gate.wait(5)
        return Direction.STOP

    executor = Concurrent(stuck)
    executor.submit(request())
    assert entered.wait(1)
    start = monotonic()
    try:
        assert not executor.close()
        assert monotonic() - start < 1.5
        assert len(executor.statistics()) == 3
    finally:
        gate.set()
        for worker in executor.workers.values():
            worker.join(1)
        assert all(not worker.is_alive() for worker in executor.workers.values())


def test_unexpected_worker_termination_is_counted_once(monkeypatch):
    from maze_chase.execution import Worker

    monkeypatch.setattr(Worker, "run", lambda self: None)
    executor = Concurrent()
    for worker in executor.workers.values():
        worker.join(1)
    assert executor.failures() == 4
    assert executor.failures() == 4
    assert not executor.close()
