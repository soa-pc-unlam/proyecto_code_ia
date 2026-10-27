"""Punto de entrada: sistema concurrente de reserva de entradas de cine."""

import os
import sys

from cinema import Cinema
from reservation_system import ReservationSystem
from ui import CinemaApp


def main() -> None:
    num_workers = 4
    if len(sys.argv) > 1:
        try:
            num_workers = max(1, int(sys.argv[1]))
        except ValueError:
            pass

    cpus = os.cpu_count() or 1
    pid = os.getpid()
    print(f"PID del proceso: {pid}")
    print(f"CPUs lógicas detectadas: {cpus}")
    print(f"Workers configurados: {num_workers}")

    cinema = Cinema()
    sistema = ReservationSystem(cinema, num_workers=num_workers)
    sistema.start_workers(num_workers)

    app = CinemaApp(cinema, sistema)
    try:
        app.run()
    finally:
        sistema.shutdown()


if __name__ == "__main__":
    main()
