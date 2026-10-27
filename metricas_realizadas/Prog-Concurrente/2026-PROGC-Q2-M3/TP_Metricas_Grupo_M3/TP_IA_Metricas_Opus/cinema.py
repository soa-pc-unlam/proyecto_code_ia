"""Estado del cine: catálogo en memoria y operaciones protegidas sobre los asientos."""

from __future__ import annotations

import time

from models import EstadoAsiento, Funcion, Pelicula, ResultadoReserva

FILAS = "ABCDE"
ASIENTOS_POR_FILA = 6

CATALOGO: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("Matrix", ("18:00", "21:00")),
    ("Interestelar", ("17:30", "20:30")),
    ("El Padrino", ("19:00", "22:30")),
)


class Cine:
    def __init__(self, catalogo: tuple[tuple[str, tuple[str, ...]], ...] = CATALOGO) -> None:
        self.peliculas: list[Pelicula] = []
        self._funciones: dict[str, Funcion] = {}
        for i, (titulo, horarios) in enumerate(catalogo, start=1):
            pelicula = Pelicula(f"P{i}", titulo)
            for j, horario in enumerate(horarios, start=1):
                funcion = Funcion(f"P{i}F{j}", pelicula, horario, FILAS, ASIENTOS_POR_FILA)
                pelicula.funciones.append(funcion)
                self._funciones[funcion.id] = funcion
            self.peliculas.append(pelicula)

    def funciones(self) -> list[Funcion]:
        return list(self._funciones.values())

    def obtener_funcion(self, funcion_id: str) -> Funcion:
        return self._funciones[funcion_id]

    def reservar(
        self, funcion_id: str, codigo: str, usuario: str, demora: float = 0.0
    ) -> ResultadoReserva:
        asiento = self.obtener_funcion(funcion_id).asientos[codigo]
        # Sección crítica de grano fino: sólo se toma el lock de este asiento, así que
        # reservas sobre asientos distintos avanzan en paralelo. La comprobación y la
        # modificación ocurren dentro del mismo `with`, por lo que son atómicas para
        # los demás workers, y el lock se libera siempre (incluso ante excepciones).
        with asiento.lock:
            if asiento.estado is EstadoAsiento.RESERVADO:
                return ResultadoReserva.RECHAZADA
            if demora > 0:
                # Simula el tiempo de confirmar la reserva; no cumple ningún rol de sincronización.
                time.sleep(demora)
            asiento.estado = EstadoAsiento.RESERVADO
            asiento.reservado_por = usuario
            return ResultadoReserva.EXITOSA

    def reiniciar_funcion(self, funcion_id: str) -> None:
        for asiento in self.obtener_funcion(funcion_id).asientos.values():
            # Un lock por vez: nunca se retienen dos locks de asiento simultáneamente.
            with asiento.lock:
                asiento.estado = EstadoAsiento.DISPONIBLE
                asiento.reservado_por = None

    def reiniciar_todo(self) -> None:
        for funcion_id in self._funciones:
            self.reiniciar_funcion(funcion_id)

    def contar_reservados(self, funcion_id: str) -> int:
        return sum(
            1
            for asiento in self.obtener_funcion(funcion_id).asientos.values()
            if asiento.estado is EstadoAsiento.RESERVADO
        )
