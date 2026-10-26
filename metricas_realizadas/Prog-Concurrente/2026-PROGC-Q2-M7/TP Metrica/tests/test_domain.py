from collections import deque
from dataclasses import FrozenInstanceError

import pytest

from maze_chase.ai import Request, Snapshot, decide, target
from maze_chase.domain import Board, Direction, Game, Ghost, State, moved


def test_level_connected_and_messages_immutable():
    game = Game()
    seen = {game.player}
    queue = deque(seen)
    while queue:
        for direction in Direction:
            candidate = moved(queue[0], direction)
            if game.board.walkable(candidate) and candidate not in seen:
                seen.add(candidate)
                queue.append(candidate)
        queue.popleft()
    assert game.pellets | game.powers | set(game.ghosts.values()) <= seen
    snapshot = Snapshot(1, game.board, game.player, Direction.UP, tuple(game.ghosts.values()), 0)
    with pytest.raises(FrozenInstanceError):
        snapshot.tick = 2
    assert isinstance(snapshot.board.cells, tuple)


def test_wall_continuous_movement_and_scoring():
    game = Game(state=State.RUNNING, player=(1, 3), requested=Direction.UP)
    game.advance({}, 0.1, 6)
    assert game.player == (1, 2)
    assert game.score == 10
    game.advance({}, 0.1, 6)
    assert game.player == (1, 1) and game.score == 60
    assert game.power_remaining == 6
    game.advance({}, 0.1, 6)
    assert game.player == (1, 1)
    assert game.power_remaining == pytest.approx(5.9)


def test_blocked_turn_keeps_previous_direction():
    game = Game(
        state=State.RUNNING, player=(2, 3), direction=Direction.RIGHT, requested=Direction.UP
    )
    game.advance({}, 0.1, 6)
    assert game.player == (3, 3)


@pytest.mark.parametrize("power", [0, 2])
def test_crossing_collision(power):
    game = Game(
        state=State.RUNNING, player=(1, 3), requested=Direction.RIGHT, power_remaining=power
    )
    game.ghosts[Ghost.BLINKY] = (2, 3)
    game.advance({Ghost.BLINKY: Direction.LEFT}, 0.1, 6)
    assert game.lives == (3 if power else 2)
    assert game.score == (210 if power else 10)


def test_multiple_ghosts_only_cost_one_life_and_loss():
    game = Game(state=State.RUNNING, lives=1)
    game.ghosts = {g: game.player for g in Ghost}
    game.advance({}, 0.1, 6)
    assert game.lives == 0 and game.state is State.LOST


def test_win_pause_and_power_expiration():
    game = Game(state=State.PAUSED, power_remaining=1)
    game.advance({}, 2, 6)
    assert game.power_remaining == 1
    game.pellets.clear()
    game.powers.clear()
    game.state = State.RUNNING
    game.advance({}, 2, 6)
    assert game.power_remaining == 0 and game.state is State.WON


def test_strategies_have_distinct_targets_and_valid_moves():
    game = Game()
    snapshot = Snapshot(1, game.board, (5, 3), Direction.RIGHT, tuple(game.ghosts.values()), 0)
    targets = [target(Request(g, snapshot)) for g in Ghost]
    assert targets[0] == (5, 3)
    assert targets[1] == (9, 3)
    assert targets[2] == (6, -3)
    near = Snapshot(2, game.board, (10, 9), Direction.UP, tuple(game.ghosts.values()), 0)
    assert target(Request(Ghost.CLYDE, near)) == (1, 11)
    for power in (0, 3):
        for y, row in enumerate(game.board.cells):
            for x, cell in enumerate(row):
                if cell == "#":
                    continue
                for ghost in Ghost:
                    s = Snapshot(
                        3, game.board, (x, y), Direction.LEFT, tuple(game.ghosts.values()), power
                    )
                    direction = decide(Request(ghost, s))
                    assert game.board.walkable(moved(game.ghosts[ghost], direction))


def test_board_rejects_ragged_matrix():
    with pytest.raises(ValueError):
        Board(("##", "#"))
