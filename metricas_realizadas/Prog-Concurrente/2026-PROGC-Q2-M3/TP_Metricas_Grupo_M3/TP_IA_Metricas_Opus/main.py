"""Punto de entrada.

Uso manual:      python main.py [--workers N] [--demora MS]
Uso automático:  python main.py --escenario distribuida --usuarios 50 --solicitudes 200 --salir-al-terminar
"""

from __future__ import annotations

import argparse
import os
import threading
import tkinter as tk

from cinema import Cine
from reservation_system import SistemaReservas
from simulation import ControladorSimulaciones
from ui import ESCENARIOS, AplicacionCine

WORKERS_INICIALES = 4
DEMORA_INICIAL_MS = 10
USUARIOS_INICIALES = 20
SOLICITUDES_INICIALES = 10
SEMILLA_INICIAL = 42


def leer_argumentos() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sistema concurrente de venta y reserva de entradas de cine")
    parser.add_argument(
        "--workers", type=int, default=WORKERS_INICIALES, help="cantidad inicial de workers consumidores"
    )
    parser.add_argument(
        "--demora", type=int, default=DEMORA_INICIAL_MS, help="tiempo simulado de confirmación de cada reserva (ms)"
    )
    parser.add_argument(
        "--escenario", choices=ESCENARIOS, help="inicia automáticamente este escenario al abrir la aplicación"
    )
    parser.add_argument("--usuarios", type=int, default=USUARIOS_INICIALES, help="usuarios productores")
    parser.add_argument(
        "--solicitudes", type=int, default=SOLICITUDES_INICIALES, help="solicitudes por usuario"
    )
    parser.add_argument(
        "--asientos-disputa", type=int, default=1, help="asientos en disputa (escenario de alta contención)"
    )
    parser.add_argument("--semilla", type=int, default=SEMILLA_INICIAL, help="semilla aleatoria")
    parser.add_argument(
        "--salir-al-terminar", action="store_true", help="cierra la aplicación al finalizar el escenario automático"
    )
    args = parser.parse_args()
    if args.workers < 1:
        parser.error("--workers debe ser al menos 1")
    if args.demora < 0:
        parser.error("--demora no puede ser negativa")
    if min(args.usuarios, args.solicitudes, args.asientos_disputa) < 1:
        parser.error("--usuarios, --solicitudes y --asientos-disputa deben ser al menos 1")
    return args


def main() -> None:
    args = leer_argumentos()
    print(f"PID del proceso: {os.getpid()}")
    print(f"CPUs lógicas detectadas: {os.cpu_count()}")
    print(f"Workers configurados: {args.workers}", flush=True)

    cine = Cine()
    sistema = SistemaReservas(cine, args.workers, args.demora / 1000)
    simulador = ControladorSimulaciones(cine, sistema)

    root = tk.Tk()
    app = AplicacionCine(root, cine, sistema, simulador)
    if args.escenario:
        app.programar_simulacion(
            args.escenario,
            args.usuarios,
            args.solicitudes,
            args.asientos_disputa,
            args.semilla,
            args.salir_al_terminar,
        )
    try:
        root.mainloop()
    finally:
        app.liberar_recursos()

    restantes = [t.name for t in threading.enumerate() if t is not threading.main_thread()]
    detalle = f" ({', '.join(restantes)})" if restantes else ""
    print(f"Threads secundarios activos al finalizar: {len(restantes)}{detalle}", flush=True)


if __name__ == "__main__":
    main()
