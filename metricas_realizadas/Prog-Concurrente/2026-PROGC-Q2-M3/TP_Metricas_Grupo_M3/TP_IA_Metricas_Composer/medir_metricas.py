"""
Mide CPU y memoria del proceso con psutil durante dos escenarios del cine.

Modo CPU: 100% = 1 núcleo lógico (cpu_percent del proceso puede superar 100%).
"""

from __future__ import annotations

import argparse
import os
import random
import threading
import time
from dataclasses import dataclass

import psutil

from cinema import Cinema
from reservation_system import ReservationSystem
from simulation import (
    EscenarioSimulacion,
    ParametrosSimulacion,
    ResultadoSimulacion,
    ejecutar_simulacion,
)


@dataclass
class MetricasEscenario:
    nombre: str
    cpu_promedio: float
    cpu_pico: float
    memoria_mb: float
    resultado: ResultadoSimulacion


class MonitorProceso:
    """Muestrea CPU y RSS del proceso actual en un hilo auxiliar."""

    def __init__(self, intervalo_seg: float = 0.001) -> None:
        self._proceso = psutil.Process(os.getpid())
        self._intervalo = intervalo_seg
        self._muestras_cpu: list[float] = []
        self._muestras_mem_mb: list[float] = []
        self._detener = threading.Event()
        self._hilo: threading.Thread | None = None
        self._cpu_intervalo_total: float | None = None
        self._t_wall_inicio = 0.0
        self._t_wall_fin = 0.0
        self._t_cpu_inicio = None
        self._t_cpu_fin = None

    def iniciar(self) -> None:
        self._muestras_cpu.clear()
        self._muestras_mem_mb.clear()
        self._cpu_intervalo_total = None
        self._detener.clear()
        self._t_wall_inicio = time.perf_counter()
        self._t_cpu_inicio = self._proceso.cpu_times()
        self._proceso.cpu_percent(None)

        def _loop() -> None:
            while not self._detener.is_set():
                cpu = self._proceso.cpu_percent(interval=None)
                rss_mb = self._proceso.memory_info().rss / (1024 * 1024)
                self._muestras_cpu.append(cpu)
                self._muestras_mem_mb.append(rss_mb)
                time.sleep(self._intervalo)

        self._hilo = threading.Thread(target=_loop, name="MonitorPsutil", daemon=True)
        self._hilo.start()
        time.sleep(self._intervalo * 2)

    def detener(self) -> None:
        self._detener.set()
        if self._hilo is not None:
            self._hilo.join(timeout=max(0.5, self._intervalo * 10))
        self._t_wall_fin = time.perf_counter()
        self._t_cpu_fin = self._proceso.cpu_times()
        self._cpu_intervalo_total = self._proceso.cpu_percent(interval=None)

    def _cpu_promedio_por_tiempos(self) -> float:
        if self._t_cpu_inicio is None or self._t_cpu_fin is None:
            return 0.0
        wall = self._t_wall_fin - self._t_wall_inicio
        if wall <= 0:
            return 0.0
        uso_cpu = (
            (self._t_cpu_fin.user - self._t_cpu_inicio.user)
            + (self._t_cpu_fin.system - self._t_cpu_inicio.system)
        )
        return (uso_cpu / wall) * 100.0

    @property
    def cpu_promedio(self) -> float:
        por_tiempos = self._cpu_promedio_por_tiempos()
        if self._muestras_cpu:
            por_muestras = sum(self._muestras_cpu) / len(self._muestras_cpu)
            return max(por_tiempos, por_muestras)
        if self._cpu_intervalo_total is not None and self._cpu_intervalo_total > 0:
            return max(por_tiempos, self._cpu_intervalo_total)
        return por_tiempos

    @property
    def cpu_pico(self) -> float:
        pico = max(self._muestras_cpu) if self._muestras_cpu else 0.0
        if self._cpu_intervalo_total is not None:
            pico = max(pico, self._cpu_intervalo_total)
        return max(pico, self.cpu_promedio)

    @property
    def memoria_mb(self) -> float:
        if not self._muestras_mem_mb:
            return self._proceso.memory_info().rss / (1024 * 1024)
        return max(self._muestras_mem_mb)


def _ejecutar_con_metricas(
    nombre: str,
    params: ParametrosSimulacion,
    intervalo_muestreo: float,
    semilla: int | None,
    repeticiones: int,
) -> MetricasEscenario:
    if semilla is not None:
        random.seed(semilla)

    cinema = Cinema()
    sistema = ReservationSystem(cinema, num_workers=params.num_workers)
    sistema.start_workers(params.num_workers)

    if params.escenario == EscenarioSimulacion.ALTA_CONTENCION:
        pid = params.pelicula_id or "p1"
        fid = params.funcion_id or "f1"
        cinema.reset_funcion(pid, fid)

    monitor = MonitorProceso(intervalo_seg=intervalo_muestreo)
    monitor.iniciar()
    resultado: ResultadoSimulacion | None = None
    try:
        peliculas = cinema.listar_peliculas()
        for i in range(repeticiones):
            if params.escenario == EscenarioSimulacion.ALTA_CONTENCION and i > 0:
                cinema.reset_funcion(
                    params.pelicula_id or "p1",
                    params.funcion_id or "f1",
                )
            resultado = ejecutar_simulacion(
                sistema,
                peliculas,
                params,
                reiniciar_workers=False,
            )
    finally:
        monitor.detener()
        sistema.shutdown()

    assert resultado is not None
    return MetricasEscenario(
        nombre=nombre,
        cpu_promedio=monitor.cpu_promedio,
        cpu_pico=monitor.cpu_pico,
        memoria_mb=monitor.memoria_mb,
        resultado=resultado,
    )


def _formatear_cpu(promedio: float, pico: float) -> str:
    return f"{promedio:.1f}% (Pico {pico:.1f}%)"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Métricas psutil: carga distribuida y alta contención."
    )
    parser.add_argument(
        "--codigo",
        default="M3 - Implementación actual",
        help="Etiqueta para la fila de la tabla (ej. M3 - Cursor Composer)",
    )
    parser.add_argument("--productores", type=int, default=10)
    parser.add_argument("--solicitudes", type=int, default=5)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--pelicula", default="p1")
    parser.add_argument("--funcion", default="f1")
    parser.add_argument("--asiento", default="A1")
    parser.add_argument(
        "--semilla",
        type=int,
        default=42,
        help="Semilla para carga distribuida (repetibilidad).",
    )
    parser.add_argument(
        "--intervalo",
        type=float,
        default=0.001,
        help="Pausa entre muestras instantáneas de CPU (segundos).",
    )
    parser.add_argument(
        "--sin-pausa",
        action="store_true",
        help="No esperar entre escenarios.",
    )
    parser.add_argument(
        "--repeticiones",
        type=int,
        default=1,
        help="Veces que se repite cada escenario dentro de la misma medición.",
    )
    args = parser.parse_args()

    pid = os.getpid()
    cpus_logicas = os.cpu_count() or psutil.cpu_count(logical=True) or 1
    memoria_total_mb = psutil.virtual_memory().total / (1024 * 1024)

    print(f"PID del proceso: {pid}")
    print(f"CPUs lógicas detectadas: {cpus_logicas}")
    print(f"Workers configurados: {args.workers}")
    print(f"Método medición: psutil | Modo CPU: 100%=1 núcleo")
    print(f"Memoria total del sistema: {memoria_total_mb:.0f} MB")
    print()

    params_distribuida = ParametrosSimulacion(
        num_productores=args.productores,
        solicitudes_por_productor=args.solicitudes,
        num_workers=args.workers,
        escenario=EscenarioSimulacion.DISTRIBUIDA,
    )
    params_contencion = ParametrosSimulacion(
        num_productores=args.productores,
        solicitudes_por_productor=args.solicitudes,
        num_workers=args.workers,
        escenario=EscenarioSimulacion.ALTA_CONTENCION,
        pelicula_id=args.pelicula,
        funcion_id=args.funcion,
        asiento_objetivo=args.asiento,
    )

    print("Ejecutando Acción 1: Carga distribuida...")
    m1 = _ejecutar_con_metricas(
        "Carga Distribuida",
        params_distribuida,
        args.intervalo,
        args.semilla,
        args.repeticiones,
    )
    if not args.sin_pausa:
        time.sleep(0.5)

    print("Ejecutando Acción 2: Alta contención...")
    m2 = _ejecutar_con_metricas(
        "Alta Contención",
        params_contencion,
        args.intervalo,
        None,
        args.repeticiones,
    )

    print()
    print("=== Resultados para la tabla ===")
    print(f"Código: {args.codigo}")
    print(f"Método medición: psutil")
    print(f"Modo CPU: 100%=1 núcleo")
    print(f"CPUs lógicas: {cpus_logicas}")
    print(f"Acción 1: {m1.nombre}")
    print(f"CPU Acc1: {_formatear_cpu(m1.cpu_promedio, m1.cpu_pico)}")
    print(f"Acción 2: {m2.nombre}")
    print(f"CPU Acc2: {_formatear_cpu(m2.cpu_promedio, m2.cpu_pico)}")
    print(f"Memoria Acc1 (MB): {m1.memoria_mb:.2f}")
    print(f"Memoria Acc2 (MB): {m2.memoria_mb:.2f}")
    print(f"Memoria Total (MB): {memoria_total_mb:.0f}")
    print()
    print("=== Detalle de la simulación ===")
    for m in (m1, m2):
        r = m.resultado
        print(
            f"{m.nombre}: generadas={r.solicitudes_generadas} "
            f"procesadas={r.solicitudes_procesadas} exitosas={r.reservas_exitosas} "
            f"rechazadas={r.solicitudes_rechazadas} tiempo={r.tiempo_total_seg:.3f}s "
            f"({r.solicitudes_por_segundo:.1f} sol/s)"
        )

    # Fila TSV para pegar en Excel/Sheets
    fila = "\t".join(
        [
            args.codigo,
            "psutil",
            "100%=1 núcleo",
            str(cpus_logicas),
            m1.nombre,
            _formatear_cpu(m1.cpu_promedio, m1.cpu_pico),
            m2.nombre,
            _formatear_cpu(m2.cpu_promedio, m2.cpu_pico),
            f"{m1.memoria_mb:.2f}",
            f"{m2.memoria_mb:.2f}",
            f"{memoria_total_mb:.0f}",
        ]
    )
    print()
    print("=== Fila (TSV, copiar a la planilla) ===")
    print(fila)


if __name__ == "__main__":
    main()
