import json

import pygame
import pytest

from maze_chase.application import Config, Controller
from maze_chase.cli import run
from maze_chase.domain import Direction, State
from maze_chase.execution import Sequential
from maze_chase.metrics import export
from maze_chase.ui import View


@pytest.mark.parametrize("mode", ["sequential", "concurrent"])
def test_rendered_run_exports_metrics_and_closes(monkeypatch, tmp_path, mode):
    monkeypatch.setenv("SDL_VIDEODRIVER", "dummy")
    report = run(mode, Config(tick_hz=60, fps=120), ticks=12)
    export(tmp_path / "run.json", report)
    saved = json.loads((tmp_path / "run.json").read_text())
    assert saved["clean_shutdown"]
    assert saved["counts"]["ticks"] == 12
    assert saved["tasks_processed"] > 0
    assert saved["fps_mean"] > 0
    assert saved["rss_mb_max"] > 0
    assert saved["counts"]["invariant_violations"] == 0
    assert not pygame.display.get_init()


def test_keyboard_states_and_quit(monkeypatch):
    monkeypatch.setenv("SDL_VIDEODRIVER", "dummy")
    c = Controller(Sequential())
    view = View(c, "sequential")
    try:
        for key, expected in [
            (pygame.K_RETURN, State.RUNNING),
            (pygame.K_SPACE, State.PAUSED),
            (pygame.K_r, State.RUNNING),
        ]:
            pygame.event.post(pygame.event.Event(pygame.KEYDOWN, key=key))
            assert view.events()
            assert c.game.state is expected
        pygame.event.post(pygame.event.Event(pygame.KEYDOWN, key=pygame.K_LEFT))
        view.events()
        assert c.game.requested is Direction.LEFT
        pygame.event.post(pygame.event.Event(pygame.QUIT))
        assert not view.events()
    finally:
        pygame.quit()


def test_failed_display_still_cleans_workers_and_reports(monkeypatch):
    from threading import enumerate as threads

    def fail():
        raise pygame.error("injected display failure")

    monkeypatch.setattr(pygame.display, "init", fail)
    report = run("concurrent", Config(), ticks=1)
    assert not report["clean_shutdown"]
    assert not report["execution_completed"]
    assert "injected display failure" in report["run_error"]
    assert not any(t.name.startswith("ghost-") for t in threads())
