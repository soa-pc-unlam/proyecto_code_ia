"""Productores y escenarios de simulación concurrente."""

from __future__ import annotations

import random
import threading
import time
from dataclasses import dataclass
from typing import Callable, List, Optional

from models import Pelicula
from reservation_system import ReservationSystem


class EscenarioSimulacion:
    DISTRIBUIDA = "distribuida"
    ALTA_CONTENCION = "alta_contencion"


@dataclass
class ParametrosSimulacion:
    num_productores: int
    solicitudes_por_productor: int
    num_workers: int
    escenario: str
    usuario_prefijo: str = "SimUser"
    pelicula_id: Optional[str] = None
    funcion_id: Optional[str] = None
    asiento_objetivo: Optional[str] = None


@dataclass
class ResultadoSimulacion:
    solicitudes_generadas: int
    solicitudes_procesadas: int
    reservas_exitosas: int
    solicitudes_rechazadas: int
    tiempo_total_seg: float
    tiempo_promedio_seg: float
    solicitudes_por_segundo: float


class Productor(threading.Thread):
    def __init__(
        self,
        nombre: str,
        sistema: ReservationSystem,
        peliculas: List[Pelicula],
        cantidad: int,
        generador: Callable[[int], tuple],
    ) -> None:
        super().__init__(name=nombre, daemon=True)
        self._sistema = sistema
        self._peliculas = peliculas
        self._cantidad = cantidad
        self._generador = generador
        self._detener = threading.Event()

    def run(self) -> None:
        for i in range(self._cantidad):
            if self._detener.is_set():
                break
            usuario, pid, ptitulo, fid, fhor, asiento = self._generador(i)
            solicitud = self._sistema.crear_solicitud(
                usuario, pid, ptitulo, fid, fhor, asiento
            )
            self._sistema.encolar(solicitud)
            time.sleep(0.001)

    def detener(self) -> None:
        self._detener.set()


def _generador_distribuido(
    peliculas: List[Pelicula], prefijo: str, productor_idx: int
) -> Callable[[int], tuple]:
    def gen(solicitud_idx: int) -> tuple:
        peli = random.choice(peliculas)
        fid = random.choice(list(peli.funciones.keys()))
        funcion = peli.funciones[fid]
        asiento = random.choice(list(funcion.asientos.keys()))
        usuario = f"{prefijo}-{productor_idx + 1}"
        return (usuario, peli.identificador, peli.titulo, fid, funcion.horario, asiento)

    return gen


def _generador_contencion(
    peliculas: List[Pelicula],
    prefijo: str,
    productor_idx: int,
    pelicula_id: str,
    funcion_id: str,
    asiento: str,
) -> Callable[[int], tuple]:
    peli = next(p for p in peliculas if p.identificador == pelicula_id)
    funcion = peli.funciones[funcion_id]

    def gen(_: int) -> tuple:
        usuario = f"{prefijo}-{productor_idx + 1}"
        return (
            usuario,
            pelicula_id,
            peli.titulo,
            funcion_id,
            funcion.horario,
            asiento,
        )

    return gen


def ejecutar_simulacion(
    sistema: ReservationSystem,
    peliculas: List[Pelicula],
    params: ParametrosSimulacion,
    reiniciar_workers: bool = True,
) -> ResultadoSimulacion:
    if reiniciar_workers:
        sistema.shutdown()
        sistema.start_workers(params.num_workers)

    stats_inicio = sistema.estadisticas()
    gen_inicio = stats_inicio["generadas"]
    proc_inicio = stats_inicio["procesadas"]
    exit_inicio = stats_inicio["exitosas"]
    rech_inicio = stats_inicio["rechazadas"]

    productores: list[Productor] = []
    t0 = time.perf_counter()

    for p in range(params.num_productores):
        if params.escenario == EscenarioSimulacion.ALTA_CONTENCION:
            pid = params.pelicula_id or peliculas[0].identificador
            fid = params.funcion_id or list(peliculas[0].funciones.keys())[0]
            asiento = params.asiento_objetivo or "A1"
            gen = _generador_contencion(
                peliculas, params.usuario_prefijo, p, pid, fid, asiento
            )
        else:
            gen = _generador_distribuido(peliculas, params.usuario_prefijo, p)

        prod = Productor(
            f"Productor-{p + 1}",
            sistema,
            peliculas,
            params.solicitudes_por_productor,
            gen,
        )
        productores.append(prod)
        prod.start()

    for prod in productores:
        prod.join()

    sistema.esperar_cola_vacia()
    t1 = time.perf_counter()

    stats_fin = sistema.estadisticas()
    generadas = stats_fin["generadas"] - gen_inicio
    procesadas = stats_fin["procesadas"] - proc_inicio
    exitosas = stats_fin["exitosas"] - exit_inicio
    rechazadas = stats_fin["rechazadas"] - rech_inicio
    tiempo_total = t1 - t0
    promedio = tiempo_total / procesadas if procesadas else 0.0
    por_seg = procesadas / tiempo_total if tiempo_total > 0 else 0.0

    return ResultadoSimulacion(
        solicitudes_generadas=generadas,
        solicitudes_procesadas=procesadas,
        reservas_exitosas=exitosas,
        solicitudes_rechazadas=rechazadas,
        tiempo_total_seg=tiempo_total,
        tiempo_promedio_seg=promedio,
        solicitudes_por_segundo=por_seg,
    )


@dataclass
class ResultadoPruebaConflicto:
    exitosas: int
    rechazadas: int
    total: int
    aprobada: bool
    detalle: str


def ejecutar_prueba_conflicto(
    sistema: ReservationSystem,
    cinema,
    peliculas: List[Pelicula],
    pelicula_id: str,
    funcion_id: str,
    asiento: str,
    num_solicitudes: int = 20,
    num_workers: int = 4,
) -> ResultadoPruebaConflicto:
    cinema.reset_funcion(pelicula_id, funcion_id)
    sistema.shutdown()
    sistema.start_workers(num_workers)

    stats_antes = sistema.estadisticas()
    gen0 = stats_antes["generadas"]
    proc0 = stats_antes["procesadas"]
    ex0 = stats_antes["exitosas"]
    re0 = stats_antes["rechazadas"]

    params = ParametrosSimulacion(
        num_productores=num_solicitudes,
        solicitudes_por_productor=1,
        num_workers=num_workers,
        escenario=EscenarioSimulacion.ALTA_CONTENCION,
        usuario_prefijo="Conflicto",
        pelicula_id=pelicula_id,
        funcion_id=funcion_id,
        asiento_objetivo=asiento,
    )
    ejecutar_simulacion(sistema, peliculas, params, reiniciar_workers=False)

    stats = sistema.estadisticas()
    exitosas = stats["exitosas"] - ex0
    rechazadas = stats["rechazadas"] - re0
    total = stats["procesadas"] - proc0
    generadas = stats["generadas"] - gen0

    aprobada = exitosas == 1 and rechazadas == total - 1 and total == num_solicitudes
    if aprobada:
        detalle = "Prueba APROBADA: 1 exitosa, resto rechazadas."
    else:
        detalle = (
            f"Prueba FALLIDA: se esperaba 1 exitosa y {num_solicitudes - 1} rechazadas; "
            f"obtenido {exitosas} exitosas, {rechazadas} rechazadas "
            f"(generadas={generadas}, procesadas={total})."
        )

    return ResultadoPruebaConflicto(
        exitosas=exitosas,
        rechazadas=rechazadas,
        total=total,
        aprobada=aprobada,
        detalle=detalle,
    )
