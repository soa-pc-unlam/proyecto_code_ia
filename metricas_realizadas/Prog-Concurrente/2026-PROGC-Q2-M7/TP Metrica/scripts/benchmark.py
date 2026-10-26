"""Ejecutar con uv run python scripts/benchmark.py; cada ensayo usa un proceso nuevo."""

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from statistics import fmean

from maze_chase.metrics import export, export_csv


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ticks", type=int, default=240)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--tick-hz", type=float, default=30)
    parser.add_argument("--render", action="store_true", help="Incluir renderizado y FPS")
    parser.add_argument("--output-dir", type=Path, default=Path("results/benchmark"))
    args = parser.parse_args()
    if args.ticks <= 0 or args.repeats <= 0 or not 0 < args.tick_hz <= 240:
        parser.error("ticks/repeats positivos; tick-hz entre 0 y 240")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    reports = []
    for repeat in range(args.repeats):
        modes = ("sequential", "concurrent") if repeat % 2 == 0 else ("concurrent", "sequential")
        for mode in modes:
            path = args.output_dir / f"{mode}-{repeat + 1}.json"
            command = [
                sys.executable,
                "-m",
                "maze_chase.cli",
                "--mode",
                mode,
                "--ticks",
                str(args.ticks),
                "--tick-hz",
                str(args.tick_hz),
                "--output",
                str(path),
            ]
            if not args.render:
                command.append("--headless")
            subprocess.run(
                command, check=True, env={**os.environ, "PYGAME_HIDE_SUPPORT_PROMPT": "1"}
            )
            report = json.loads(path.read_text())
            report["repeat"] = repeat + 1
            reports.append(report)
    export(args.output_dir / "runs.json", {"runs": reports})
    export_csv(args.output_dir / "runs.csv", reports)
    fields = (
        "cpu_percent_mean",
        "cpu_percent_max",
        "rss_mb_mean",
        "rss_mb_max",
        "update_ms_mean",
        "decision_ms_mean",
        "elapsed_seconds",
        "ai_seconds_total",
    )
    summary = {
        mode: {key: fmean(r[key] for r in reports if r["mode"] == mode) for key in fields}
        for mode in ("sequential", "concurrent")
    }
    summary["concurrent_minus_sequential"] = {
        key: summary["concurrent"][key] - summary["sequential"][key] for key in fields
    }
    export(args.output_dir / "comparison.json", summary)
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
