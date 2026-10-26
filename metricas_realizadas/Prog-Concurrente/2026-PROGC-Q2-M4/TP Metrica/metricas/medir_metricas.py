"""Mide CPU, memoria, throughput y conservación de paquetes de logistics_center.

Ejecuta main.py como proceso hijo (caja negra), lo muestrea con psutil y lo
detiene con SIGINT (equivalente a Ctrl+C). La conservación se verifica
sumando los paquetes entregados que loguean los camiones contra el total
registrado en el resumen final.

Uso:
  python medir_metricas.py <ruta a logistics_center> <salida.json>
"""

import json
import os
import re
import signal
import subprocess
import sys
import tempfile
import time

import psutil

SAMPLE_SECONDS = 0.5
REPETITIONS = 3
RUN_SECONDS = 20.0
SCENARIOS = [
    ("A: 2 camiones / 3 operarios (default)", ["--trucks", "2", "--operators", "3"]),
    ("B: 10 camiones / 10 operarios", ["--trucks", "10", "--operators", "10"]),
    ("C: 50 camiones / 50 operarios (estrés)", ["--trucks", "50", "--operators", "50"]),
    ("D: default sin chaos monkey", ["--no-chaos"]),
]
DELIVERED_RE = re.compile(r"Delivered (\d+)/(\d+) packages")
REGISTERED_RE = re.compile(r"Packages registered: (\d+) \| units: (\d+) \| left in dock: (\d+)")


def sample(process: psutil.Process, deadline: float) -> list[dict]:
  """Toma muestras de CPU%, RSS e hilos hasta el deadline."""
  samples = []
  process.cpu_percent(None)
  while time.monotonic() < deadline:
    time.sleep(SAMPLE_SECONDS)
    with process.oneshot():
      samples.append({
          "cpu_percent": process.cpu_percent(None),
          "rss_mb": process.memory_info().rss / 2**20,
          "threads": process.num_threads(),
      })
  return samples


def run_once(project: str, args: list[str], seconds: float) -> dict:
  """Corre una simulación, la interrumpe y devuelve sus métricas."""
  with tempfile.TemporaryDirectory() as tmp:
    log_path = os.path.join(tmp, "sim.log")
    cmd = [sys.executable, "main.py", "--log-level", "INFO", "--log-file",
           log_path, "--export-path", os.path.join(tmp, "r.json"), *args]
    start = time.monotonic()
    child = subprocess.Popen(cmd, cwd=project, stdout=subprocess.DEVNULL,
                             stderr=subprocess.DEVNULL)
    process = psutil.Process(child.pid)
    samples = sample(process, start + seconds)
    cpu_before_stop = process.cpu_times()
    child.send_signal(signal.SIGINT)
    stop_requested = time.monotonic()
    child.wait(timeout=60)
    shutdown_seconds = time.monotonic() - stop_requested
    log = open(log_path, encoding="utf-8").read()
  delivered = sum(int(m.group(1)) for m in DELIVERED_RE.finditer(log))
  registered = REGISTERED_RE.search(log)
  cpu_seconds = cpu_before_stop.user + cpu_before_stop.system
  steady = samples[2:] or samples
  return {
      "exit_code": child.returncode,
      "cpu_seconds": cpu_seconds,
      "cpu_percent_avg": sum(s["cpu_percent"] for s in steady) / len(steady),
      "cpu_percent_max": max(s["cpu_percent"] for s in steady),
      "rss_mb_avg": sum(s["rss_mb"] for s in steady) / len(steady),
      "rss_mb_max": max(s["rss_mb"] for s in samples),
      "rss_mb_first": steady[0]["rss_mb"],
      "rss_mb_last": steady[-1]["rss_mb"],
      "threads": max(s["threads"] for s in samples),
      "delivered": delivered,
      "registered": int(registered.group(1)) if registered else None,
      "left_in_dock": int(registered.group(3)) if registered else None,
      "shutdown_seconds": shutdown_seconds,
      "maintenance_pauses": log.count("Maintenance started"),
  }


def main() -> None:
  project, output = sys.argv[1], sys.argv[2]
  results = {"python": sys.version, "cpus": psutil.cpu_count(),
             "run_seconds": RUN_SECONDS, "scenarios": {}}
  for name, args in SCENARIOS:
    runs = [run_once(project, args, RUN_SECONDS) for _ in range(REPETITIONS)]
    results["scenarios"][name] = runs
    print(name, json.dumps(runs[-1]), flush=True)
  with open(output, "w", encoding="utf-8") as fh:
    json.dump(results, fh, indent=2)


if __name__ == "__main__":
  main()
