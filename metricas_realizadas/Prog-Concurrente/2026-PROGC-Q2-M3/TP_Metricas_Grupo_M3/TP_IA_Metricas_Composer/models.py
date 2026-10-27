"""Modelos de dominio: películas, funciones, asientos y solicitudes."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from threading import Lock
from typing import Optional


class EstadoAsiento(Enum):
    DISPONIBLE = "disponible"
    RESERVADO = "reservado"


class ResultadoSolicitud(Enum):
    EXITOSA = "EXITOSA"
    RECHAZADA = "RECHAZADA - ASIENTO YA RESERVADO"
    PENDIENTE = "PENDIENTE"


@dataclass
class Asiento:
    etiqueta: str
    estado: EstadoAsiento = EstadoAsiento.DISPONIBLE
    lock: Lock = field(default_factory=Lock)

    def reset(self) -> None:
        with self.lock:
            self.estado = EstadoAsiento.DISPONIBLE


@dataclass
class Funcion:
    identificador: str
    horario: str
    asientos: dict[str, Asiento]

    def reset_asientos(self) -> None:
        for asiento in self.asientos.values():
            asiento.reset()


@dataclass
class Pelicula:
    identificador: str
    titulo: str
    funciones: dict[str, Funcion]


@dataclass
class SolicitudReserva:
    solicitud_id: int
    usuario: str
    pelicula_id: str
    pelicula_titulo: str
    funcion_id: str
    funcion_horario: str
    asiento: str
    creada_en: datetime = field(default_factory=datetime.now)
    procesada_en: Optional[datetime] = None
    inicio_proceso: Optional[datetime] = None
    resultado: ResultadoSolicitud = ResultadoSolicitud.PENDIENTE


@dataclass
class ResultadoProcesamiento:
    solicitud: SolicitudReserva
