"""Muestreo de proceso en el hilo principal y exportación JSON/CSV."""

import csv
import json
import platform
import sys
from dataclasses import asdict
from pathlib import Path
from statistics import fmean
from time import perf_counter

import psutil
import pygame

from .application import Controller


def mean(values: list[float]) -> float:
    return fmean(values) if values else 0.0


class Metrics:
    def __init__(self) -> None:
        self.process = psutil.Process()
        self.started = self.last = perf_counter()
        self.cpu_start = self.cpu_last = self.cpu_time()
        self.cpu: list[float] = []
        self.memory: list[float] = []
        self.frames = 0
        self.sample(force=True)

    def cpu_time(self) -> float:
        times = self.process.cpu_times()
        return times.user + times.system

    def sample(self, force: bool = False) -> None:
        now = perf_counter()
        if force or now - self.last >= 0.1:
            cpu = self.cpu_time()
            if now - self.last >= 0.01:
                self.cpu.append(100 * (cpu - self.cpu_last) / (now - self.last))
            self.memory.append(self.process.memory_info().rss / 1024**2)
            self.last, self.cpu_last = now, cpu

    def report(self, controller: Controller, mode: str, clean: bool, rendered: bool) -> dict:
        self.sample(force=True)
        elapsed = perf_counter() - self.started
        stats = controller.executor.statistics()
        processed = sum(s.processed for s in stats)
        ai = sum(s.ai_seconds for s in stats)
        counts = asdict(controller.counts)
        updates = counts.pop("update_seconds")
        return {
            "mode": mode,
            "environment": {
                "python": sys.version,
                "os": platform.platform(),
                "processor": platform.processor() or platform.machine(),
                "cpu_count": psutil.cpu_count(),
                "pygame": pygame.version.ver,
            },
            "config": asdict(controller.config),
            "elapsed_seconds": elapsed,
            "cpu_percent_mean": 100 * (self.cpu_time() - self.cpu_start) / elapsed,
            "cpu_percent_max": max(self.cpu, default=0.0),
            "rss_mb_mean": mean(self.memory),
            "rss_mb_max": max(self.memory, default=0.0),
            "samples": len(self.memory),
            "fps_mean": self.frames / elapsed if rendered else None,
            "update_ms_mean": mean(updates) * 1000,
            "update_ms_max": max(updates, default=0) * 1000,
            "ai_seconds_total": ai,
            "decision_ms_mean": ai / processed * 1000 if processed else 0,
            "decision_ms_max": max((s.max_latency for s in stats), default=0) * 1000,
            "tasks_sent": controller.executor.sent,
            "tasks_processed": processed,
            "tasks_replaced_or_cancelled": controller.executor.replaced,
            "results_dropped_full": sum(s.result_drops for s in stats),
            "worker_errors": sum(s.errors for s in stats),
            "thread_failures": controller.executor.failures(),
            "clean_shutdown": clean,
            "worker_statistics_complete": clean,
            "counts": counts,
            "final_state": controller.game.state.value,
            "score": controller.game.score,
            "player": controller.game.player,
            "ghosts": {g.value: p for g, p in controller.game.ghosts.items()},
        }


def export(path: Path, report: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def export_csv(path: Path, reports: list[dict]) -> None:
    rows = [
        {**{k: v for k, v in r.items() if not isinstance(v, (dict, list, tuple))}, **r["counts"]}
        for r in reports
    ]
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
