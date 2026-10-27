"""Entidades del dominio: películas, funciones, asientos y solicitudes de reserva."""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any


class EstadoAsiento(Enum):
    DISPONIBLE = "DISPONIBLE"
    RESERVADO = "RESERVADO"


class ResultadoReserva(Enum):
    EXITOSA = "RESERVA EXITOSA"
    RECHAZADA = "RESERVA RECHAZADA - ASIENTO YA RESERVADO"

    @property
    def etiqueta(self) -> str:
        return "EXITOSA" if self is ResultadoReserva.EXITOSA else "RECHAZADA"


class Asiento:
    def __init__(self, codigo: str) -> None:
        self.codigo = codigo
        self.estado = EstadoAsiento.DISPONIBLE
        self.reservado_por: str | None = None
        # Lock propio del asiento: sólo compiten entre sí los workers que piden ESTE asiento.
        self.lock = threading.Lock()


class Funcion:
    def __init__(
        self,
        id_funcion: str,
        pelicula: Pelicula,
        horario: str,
        filas: str,
        asientos_por_fila: int,
    ) -> None:
        self.id = id_funcion
        self.pelicula = pelicula
        self.horario = horario
        self.filas = filas
        self.asientos_por_fila = asientos_por_fila
        # El diccionario se crea una única vez y nunca cambia su estructura,
        # por lo que puede consultarse desde varios threads sin sincronización.
        self.asientos: dict[str, Asiento] = {
            f"{fila}{numero}": Asiento(f"{fila}{numero}")
            for fila in filas
            for numero in range(1, asientos_por_fila + 1)
        }

    @property
    def descripcion(self) -> str:
        return f"{self.pelicula.titulo} - {self.horario}"


class Pelicula:
    def __init__(self, id_pelicula: str, titulo: str) -> None:
        self.id = id_pelicula
        self.titulo = titulo
        self.funciones: list[Funcion] = []


@dataclass(eq=False)
class SolicitudReserva:
    id: int
    usuario: str
    pelicula: str
    funcion_id: str
    horario: str
    asiento: str
    creada: datetime = field(default_factory=datetime.now)
    t_creada: float = field(default_factory=time.perf_counter)
    t_inicio: float | None = None
    t_fin: float | None = None
    resultado: ResultadoReserva | None = None
    worker: str | None = None
    lote: Any = field(default=None, repr=False)

    @property
    def tiempo_procesamiento(self) -> float:
        if self.t_inicio is None or self.t_fin is None:
            return 0.0
        return self.t_fin - self.t_inicio

    @property
    def tiempo_en_cola(self) -> float:
        if self.t_inicio is None:
            return 0.0
        return self.t_inicio - self.t_creada
