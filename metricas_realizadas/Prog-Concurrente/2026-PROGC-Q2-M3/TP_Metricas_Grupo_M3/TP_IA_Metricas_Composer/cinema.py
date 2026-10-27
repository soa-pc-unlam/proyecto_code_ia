"""Catálogo del cine y reserva segura por asiento (un lock por asiento)."""

from __future__ import annotations

from models import (
    Asiento,
    EstadoAsiento,
    Funcion,
    Pelicula,
    ResultadoSolicitud,
    SolicitudReserva,
)


def _crear_grilla_asientos(filas: int, columnas: int) -> dict[str, Asiento]:
    asientos: dict[str, Asiento] = {}
    for fila in range(filas):
        letra = chr(ord("A") + fila)
        for col in range(1, columnas + 1):
            etiqueta = f"{letra}{col}"
            asientos[etiqueta] = Asiento(etiqueta=etiqueta)
    return asientos


class Cinema:
    """Mantiene películas, funciones y el estado independiente de cada asiento."""

    def __init__(self) -> None:
        self._peliculas: dict[str, Pelicula] = {}
        self._inicializar_catalogo()

    def _inicializar_catalogo(self) -> None:
        datos = [
            ("p1", "Matrix", [("f1", "18:00"), ("f2", "20:00")]),
            ("p2", "Inception", [("f1", "17:30"), ("f2", "21:00")]),
            ("p3", "Interstellar", [("f1", "19:00"), ("f2", "22:00")]),
        ]
        for pid, titulo, funciones in datos:
            funciones_map: dict[str, Funcion] = {}
            for fid, horario in funciones:
                funciones_map[fid] = Funcion(
                    identificador=fid,
                    horario=horario,
                    asientos=_crear_grilla_asientos(filas=5, columnas=6),
                )
            self._peliculas[pid] = Pelicula(
                identificador=pid, titulo=titulo, funciones=funciones_map
            )

    def listar_peliculas(self) -> list[Pelicula]:
        return list(self._peliculas.values())

    def obtener_pelicula(self, pelicula_id: str) -> Pelicula | None:
        return self._peliculas.get(pelicula_id)

    def obtener_funcion(self, pelicula_id: str, funcion_id: str) -> Funcion | None:
        pelicula = self._peliculas.get(pelicula_id)
        if pelicula is None:
            return None
        return pelicula.funciones.get(funcion_id)

    def procesar_reserva(self, solicitud: SolicitudReserva) -> ResultadoSolicitud:
        funcion = self.obtener_funcion(solicitud.pelicula_id, solicitud.funcion_id)
        if funcion is None:
            return ResultadoSolicitud.RECHAZADA

        asiento = funcion.asientos.get(solicitud.asiento)
        if asiento is None:
            return ResultadoSolicitud.RECHAZADA

        with asiento.lock:
            if asiento.estado == EstadoAsiento.DISPONIBLE:
                asiento.estado = EstadoAsiento.RESERVADO
                return ResultadoSolicitud.EXITOSA
            return ResultadoSolicitud.RECHAZADA

    def reset_funcion(self, pelicula_id: str, funcion_id: str) -> None:
        funcion = self.obtener_funcion(pelicula_id, funcion_id)
        if funcion is not None:
            funcion.reset_asientos()

    def estado_asientos(self, pelicula_id: str, funcion_id: str) -> dict[str, EstadoAsiento]:
        funcion = self.obtener_funcion(pelicula_id, funcion_id)
        if funcion is None:
            return {}
        resultado: dict[str, EstadoAsiento] = {}
        for etiqueta, asiento in funcion.asientos.items():
            with asiento.lock:
                resultado[etiqueta] = asiento.estado
        return resultado
