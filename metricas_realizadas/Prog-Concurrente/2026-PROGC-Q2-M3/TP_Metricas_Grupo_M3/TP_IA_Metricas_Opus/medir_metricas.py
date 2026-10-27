# /// script
# requires-python = ">=3.12"
# dependencies = ["psutil>=5.9"]
# ///
"""Medición externa de CPU y memoria de la aplicación usando psutil.

Abre `main.py` en un proceso aparte, ejecuta automáticamente cada escenario
(carga distribuida y alta contención) y lo mide desde afuera. La ventana de
medición queda delimitada por los marcadores "[SIMULACION] INICIO/FIN" que
imprime la aplicación.

    uv run medir_metricas.py
    uv run medir_metricas.py --repeticiones 5 --usuarios 100 --solicitudes 200

CPU en modo "100% = 1 núcleo" (como el Administrador de tareas por proceso de
psutil): con 16 CPUs lógicas el máximo teórico es 1600%.
Memoria = RSS (Working Set en Windows) en MB.
"""

from __future__ import annotations

import argparse
import csv
import os
import re
import statistics
import subprocess
import sys
import threading
import time
from dataclasses import dataclass
from pathlib import Path

import psutil

CARPETA = Path(__file__).resolve().parent
MB = 1024 * 1024

ESCENARIOS = (("distribuida", "Carga Distribuida"), ("contencion", "Alta Contención"))

PATRON_PID = re.compile(r"PID del proceso:\s*(\d+)")
PATRONES_RESUMEN = {
    "procesadas": re.compile(r"Solicitudes procesadas:\s*(\d+)"),
    "exitosas": re.compile(r"Reservas exitosas:\s*(\d+)"),
    "rechazadas": re.compile(r"Solicitudes rechazadas:\s*(\d+)"),
    "tiempo_app_s": re.compile(r"Tiempo total de procesamiento:\s*([\d.]+)"),
    "verificacion": re.compile(r"Verificación:\s*(\w+)"),
}

Instantanea = tuple[float, float, int]  # (instante, segundos de CPU acumulados, RSS en bytes)


@dataclass
class Corrida:
    escenario: str
    repeticion: int
    cpu_promedio: float
    cpu_pico: float
    memoria_pico_mb: float
    memoria_promedio_mb: float
    duracion_s: float
    muestras: int
    procesadas: str
    exitosas: str
    rechazadas: str
    tiempo_app_s: str
    verificacion: str


class LectorSalida(threading.Thread):
    """Lee la consola de la aplicación y registra el instante exacto de inicio y fin del escenario."""

    def __init__(self, proceso: subprocess.Popen) -> None:
        super().__init__(daemon=True)
        self._proceso = proceso
        self.pid_detectado = threading.Event()
        self.pid: int | None = None
        self._objetivo: psutil.Process | None = None
        self.inicio: Instantanea | None = None
        self.fin: Instantanea | None = None
        self.salida: list[str] = []

    def run(self) -> None:
        assert self._proceso.stdout is not None
        for linea in self._proceso.stdout:
            linea = linea.rstrip()
            self.salida.append(linea)
            if self.pid is None and (coincidencia := PATRON_PID.search(linea)):
                self.pid = int(coincidencia.group(1))
                self._objetivo = psutil.Process(self.pid)
                self.pid_detectado.set()
            elif linea.startswith("[SIMULACION] INICIO"):
                self.inicio = self._instantanea()
            elif linea.startswith("[SIMULACION] FIN"):
                self.fin = self._instantanea()
        self.pid_detectado.set()

    def _instantanea(self) -> Instantanea:
        momento = time.perf_counter()
        assert self._objetivo is not None
        tiempos = self._objetivo.cpu_times()
        return momento, tiempos.user + tiempos.system, self._objetivo.memory_info().rss

    def resumen_app(self) -> dict[str, str]:
        texto = "\n".join(self.salida)
        datos = {}
        for clave, patron in PATRONES_RESUMEN.items():
            coincidencia = patron.search(texto)
            datos[clave] = coincidencia.group(1) if coincidencia else "?"
        return datos


def comando_aplicacion(args: argparse.Namespace, escenario: str) -> list[str]:
    # Se usa el intérprete base (no el lanzador del entorno virtual) para que el PID sea el de la app.
    interprete = getattr(sys, "_base_executable", sys.executable)
    return [
        interprete,
        str(CARPETA / "main.py"),
        "--escenario", escenario,
        "--workers", str(args.workers),
        "--demora", str(args.demora),
        "--usuarios", str(args.usuarios),
        "--solicitudes", str(args.solicitudes),
        "--asientos-disputa", str(args.asientos_disputa),
        "--semilla", str(args.semilla),
        "--salir-al-terminar",
    ]


def medir_corrida(args: argparse.Namespace, escenario: str, repeticion: int) -> Corrida:
    proceso = subprocess.Popen(
        comando_aplicacion(args, escenario),
        cwd=CARPETA,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        env={**os.environ, "PYTHONIOENCODING": "utf-8", "PYTHONUNBUFFERED": "1"},
    )
    lector = LectorSalida(proceso)
    lector.start()
    if not lector.pid_detectado.wait(30) or lector.pid is None:
        proceso.kill()
        raise RuntimeError("La aplicación no informó su PID:\n" + "\n".join(lector.salida))

    objetivo = psutil.Process(lector.pid)
    objetivo.cpu_percent(None)
    muestras: list[tuple[float, float, int]] = []
    limite = time.perf_counter() + args.timeout
    while proceso.poll() is None:
        time.sleep(args.intervalo)
        try:
            cpu = objetivo.cpu_percent(None)
            rss = objetivo.memory_info().rss
        except psutil.Error:
            break
        muestras.append((time.perf_counter(), cpu, rss))
        if time.perf_counter() > limite:
            objetivo.kill()
            proceso.kill()
            raise RuntimeError(f"La corrida superó el tiempo máximo de {args.timeout} s")
    proceso.wait()
    lector.join(5)

    if lector.inicio is None or lector.fin is None:
        raise RuntimeError("La aplicación no reportó inicio/fin del escenario:\n" + "\n".join(lector.salida))

    t_inicio, cpu_inicio, rss_inicio = lector.inicio
    t_fin, cpu_fin, rss_fin = lector.fin
    duracion = t_fin - t_inicio
    # Promedio exacto: tiempo de CPU consumido por el proceso dentro de la ventana / tiempo real.
    cpu_promedio = (cpu_fin - cpu_inicio) / duracion * 100 if duracion > 0 else 0.0
    # Una muestra pertenece a la ventana si su intervalo [t - intervalo, t] se superpone con ella.
    en_ventana = [m for m in muestras if m[0] > t_inicio and m[0] - args.intervalo < t_fin]
    cpu_pico = max([cpu_promedio, *(m[1] for m in en_ventana)])
    memorias = [rss_inicio, rss_fin, *(m[2] for m in en_ventana)]

    datos = lector.resumen_app()
    return Corrida(
        escenario=escenario,
        repeticion=repeticion,
        cpu_promedio=cpu_promedio,
        cpu_pico=cpu_pico,
        memoria_pico_mb=max(memorias) / MB,
        memoria_promedio_mb=statistics.mean(memorias) / MB,
        duracion_s=duracion,
        muestras=len(en_ventana),
        **datos,
    )


def leer_argumentos() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Medición externa de CPU y memoria con psutil")
    parser.add_argument("--codigo", default="M1", help="código de la medición en la tabla")
    parser.add_argument("--repeticiones", type=int, default=3, help="corridas por escenario")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--demora", type=int, default=10, help="demora simulada por reserva (ms)")
    parser.add_argument("--usuarios", type=int, default=50, help="usuarios productores")
    parser.add_argument("--solicitudes", type=int, default=200, help="solicitudes por usuario")
    parser.add_argument("--asientos-disputa", type=int, default=1)
    parser.add_argument("--semilla", type=int, default=42)
    parser.add_argument("--intervalo", type=float, default=0.1, help="intervalo de muestreo (s)")
    parser.add_argument("--timeout", type=float, default=300, help="tiempo máximo por corrida (s)")
    parser.add_argument("--pausa", type=float, default=1.0, help="pausa entre corridas (s)")
    return parser.parse_args()


def main() -> None:
    args = leer_argumentos()
    cpus = psutil.cpu_count(logical=True)
    memoria_total = round(psutil.virtual_memory().total / MB)
    print(f"CPUs lógicas: {cpus} | Memoria total: {memoria_total} MB")
    print(
        f"Parámetros: usuarios={args.usuarios} solicitudes/usuario={args.solicitudes} "
        f"workers={args.workers} demora={args.demora} ms asientos_disputa={args.asientos_disputa} "
        f"semilla={args.semilla} repeticiones={args.repeticiones} intervalo={args.intervalo} s\n"
    )

    corridas: list[Corrida] = []
    agregados: dict[str, dict[str, float]] = {}
    for escenario, nombre in ESCENARIOS:
        propias = []
        for repeticion in range(1, args.repeticiones + 1):
            corrida = medir_corrida(args, escenario, repeticion)
            propias.append(corrida)
            print(
                f"{nombre} #{repeticion}: CPU prom {corrida.cpu_promedio:.1f}% | pico {corrida.cpu_pico:.1f}% | "
                f"RSS pico {corrida.memoria_pico_mb:.1f} MB | ventana {corrida.duracion_s:.3f} s "
                f"({corrida.muestras} muestras) | {corrida.procesadas} procesadas, "
                f"{corrida.exitosas} exitosas, {corrida.rechazadas} rechazadas | {corrida.verificacion}",
                flush=True,
            )
            time.sleep(args.pausa)
        corridas.extend(propias)
        agregados[escenario] = {
            "cpu_promedio": statistics.mean(c.cpu_promedio for c in propias),
            "cpu_pico": max(c.cpu_pico for c in propias),
            "memoria": statistics.mean(c.memoria_pico_mb for c in propias),
        }

    (esc1, nombre1), (esc2, nombre2) = ESCENARIOS
    fila = {
        "Codigo": args.codigo,
        "Método medición": "psutil",
        "Modo CPU": "100%=1 núcleo",
        "CPUs lógicas": cpus,
        "Acción 1": nombre1,
        "CPU Acc1": f"{agregados[esc1]['cpu_promedio']:.1f}% (Pico {agregados[esc1]['cpu_pico']:.1f}%)",
        "Acción 2": nombre2,
        "CPU Acc2": f"{agregados[esc2]['cpu_promedio']:.1f}% (Pico {agregados[esc2]['cpu_pico']:.1f}%)",
        "Memoria Acc1 (MB)": f"{agregados[esc1]['memoria']:.2f}",
        "Memoria Acc2 (MB)": f"{agregados[esc2]['memoria']:.2f}",
        "Memoria Total (MB)": memoria_total,
    }

    print("\n=== Fila para la tabla ===")
    for clave, valor in fila.items():
        print(f"{clave:<20} {valor}")

    archivo_tabla = CARPETA / f"metricas_{args.codigo}.csv"
    with archivo_tabla.open("w", newline="", encoding="utf-8-sig") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=list(fila), delimiter=";")
        escritor.writeheader()
        escritor.writerow(fila)

    archivo_detalle = CARPETA / f"metricas_{args.codigo}_detalle.csv"
    with archivo_detalle.open("w", newline="", encoding="utf-8-sig") as archivo:
        escritor = csv.writer(archivo, delimiter=";")
        escritor.writerow(
            [
                "Escenario", "Repetición", "CPU promedio (%)", "CPU pico (%)", "RSS pico (MB)",
                "RSS promedio (MB)", "Ventana (s)", "Muestras", "Procesadas", "Exitosas",
                "Rechazadas", "Tiempo app (s)", "Verificación",
            ]
        )
        for c in corridas:
            escritor.writerow(
                [
                    c.escenario, c.repeticion, f"{c.cpu_promedio:.2f}", f"{c.cpu_pico:.2f}",
                    f"{c.memoria_pico_mb:.2f}", f"{c.memoria_promedio_mb:.2f}", f"{c.duracion_s:.3f}",
                    c.muestras, c.procesadas, c.exitosas, c.rechazadas, c.tiempo_app_s, c.verificacion,
                ]
            )
    print(f"\nGuardado en {archivo_tabla.name} y {archivo_detalle.name}")


if __name__ == "__main__":
    main()
