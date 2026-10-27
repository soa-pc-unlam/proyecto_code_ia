"""Prueba sin interfaz gráfica: 20 solicitudes al mismo asiento."""

from cinema import Cinema
from reservation_system import ReservationSystem
from simulation import ejecutar_prueba_conflicto


def main() -> None:
    cinema = Cinema()
    sistema = ReservationSystem(cinema, num_workers=8)
    sistema.start_workers(8)
    resultado = ejecutar_prueba_conflicto(
        sistema,
        cinema,
        cinema.listar_peliculas(),
        pelicula_id="p1",
        funcion_id="f1",
        asiento="A1",
        num_solicitudes=20,
        num_workers=8,
    )
    print(resultado.detalle)
    print(
        f"Exitosas={resultado.exitosas} Rechazadas={resultado.rechazadas} "
        f"Total={resultado.total} Aprobada={resultado.aprobada}"
    )
    sistema.shutdown()


if __name__ == "__main__":
    main()
