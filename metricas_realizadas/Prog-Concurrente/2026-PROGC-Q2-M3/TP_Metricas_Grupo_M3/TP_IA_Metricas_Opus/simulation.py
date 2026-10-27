"""Productores, escenarios de carga y prueba de conflicto."""

from __future__ import annotations

import random
import threading
import time
from dataclasses import dataclass
from typing import Callable

from cinema import Cine
from models import Funcion
from reservation_system import LoteSolicitudes, SistemaReservas

# Plan de una simulación: para cada usuario, la lista de (funcion_id, asiento) que solicitará.
Plan = list[tuple[str, list[tuple[str, str]]]]

CANTIDAD_MINIMA_CONFLICTO = 20
ASIENTO_CONFLICTO = "A1"


@dataclass
class ResumenSimulacion:
    nombre: str
    parametros: str
    generadas: int
    procesadas: int
    exitosas: int
    rechazadas: int
    tiempo_total: float
    promedio_procesamiento: float
    promedio_espera: float
    asientos_duplicados: int
    aprobada: bool
    detalle_verificacion: str
    es_prueba_conflicto: bool = False

    @property
    def por_segundo(self) -> float:
        return self.procesadas / self.tiempo_total if self.tiempo_total > 0 else 0.0

    def texto(self) -> str:
        estado = "APROBADA" if self.aprobada else "FALLIDA"
        return "\n".join(
            [
                f"=== {self.nombre} ===",
                self.parametros,
                f"Solicitudes generadas:      {self.generadas}",
                f"Solicitudes procesadas:     {self.procesadas}",
                f"Reservas exitosas:          {self.exitosas}",
                f"Solicitudes rechazadas:     {self.rechazadas}",
                f"Tiempo total de procesamiento:           {self.tiempo_total:.3f} s",
                f"Tiempo promedio de procesamiento/solic.: {self.promedio_procesamiento * 1000:.2f} ms",
                f"Tiempo promedio de espera en cola:       {self.promedio_espera * 1000:.2f} ms",
                f"Solicitudes procesadas por segundo:      {self.por_segundo:.1f}",
                f"Asientos vendidos más de una vez:        {self.asientos_duplicados}",
                f"Verificación: {estado} - {self.detalle_verificacion}",
            ]
        )


class Productor(threading.Thread):
    """Usuario simulado: genera solicitudes y las encola, sin tocar nunca los asientos."""

    def __init__(
        self,
        usuario: str,
        objetivos: list[tuple[str, str]],
        sistema: SistemaReservas,
        lote: LoteSolicitudes,
        barrera: threading.Barrier,
        detener: threading.Event,
    ) -> None:
        super().__init__(name=f"Productor-{usuario}", daemon=True)
        self.usuario = usuario
        self._objetivos = objetivos
        self._sistema = sistema
        self._lote = lote
        self._barrera = barrera
        self._detener = detener

    def run(self) -> None:
        try:
            # Todos los productores arrancan a la vez para maximizar la concurrencia.
            self._barrera.wait()
        except threading.BrokenBarrierError:
            return
        for funcion_id, asiento in self._objetivos:
            if self._detener.is_set():
                return
            if self._sistema.enviar(self.usuario, funcion_id, asiento, self._lote) is None:
                return


class Simulacion(threading.Thread):
    """Coordina una ejecución: lanza los productores, espera el procesamiento y arma el resumen."""

    def __init__(
        self,
        nombre: str,
        parametros: str,
        plan: Plan,
        sistema: SistemaReservas,
        preparar: Callable[[], None] | None = None,
        verificar: Callable[[ResumenSimulacion], tuple[bool, str]] | None = None,
        es_prueba_conflicto: bool = False,
    ) -> None:
        super().__init__(name=f"Simulacion-{nombre}", daemon=True)
        self.nombre = nombre
        self._parametros = parametros
        self._plan = plan
        self._sistema = sistema
        self._preparar = preparar
        self._verificar = verificar
        self._es_prueba_conflicto = es_prueba_conflicto
        self._lote = LoteSolicitudes()
        self._detener = threading.Event()
        # +1 porque el coordinador también espera en la barrera y toma el tiempo inicial al liberarse.
        self._barrera = threading.Barrier(len(plan) + 1)

    def detener(self) -> None:
        self._detener.set()
        self._barrera.abort()  # libera a los productores que esperan el arranque
        self._lote.completado.set()  # despierta al coordinador si espera el fin del lote

    def run(self) -> None:
        if self._preparar is not None:
            self._preparar()

        productores = [
            Productor(usuario, objetivos, self._sistema, self._lote, self._barrera, self._detener)
            for usuario, objetivos in self._plan
        ]
        for productor in productores:
            productor.start()

        try:
            self._barrera.wait()
        except threading.BrokenBarrierError:
            for productor in productores:
                productor.join()
            return

        inicio = time.perf_counter()
        # Marcadores de consola que usa medir_metricas.py para delimitar la ventana de medición.
        print(f"[SIMULACION] INICIO {self.nombre}", flush=True)
        for productor in productores:
            productor.join()
        self._lote.cerrar_generacion()
        # Espera bloqueante (sin busy waiting) hasta que los workers procesen todo el lote.
        self._lote.completado.wait()
        if self._detener.is_set():
            return
        print(f"[SIMULACION] FIN {self.nombre}", flush=True)

        resumen = self._armar_resumen(inicio)
        print(resumen.texto(), flush=True)
        self._sistema.resultados.put(resumen)

    def _armar_resumen(self, inicio: float) -> ResumenSimulacion:
        lote = self._lote
        procesadas = lote.procesadas
        duplicados = sum(1 for cantidad in lote.exitos_por_asiento.values() if cantidad > 1)
        aprobada = duplicados == 0 and procesadas == lote.generadas
        if duplicados:
            detalle = f"{duplicados} asiento(s) vendidos más de una vez."
        else:
            detalle = "Ningún asiento fue vendido dos veces."

        resumen = ResumenSimulacion(
            nombre=self.nombre,
            parametros=self._parametros,
            generadas=lote.generadas,
            procesadas=procesadas,
            exitosas=lote.exitosas,
            rechazadas=lote.rechazadas,
            tiempo_total=(lote.t_ultimo_fin - inicio) if procesadas else 0.0,
            promedio_procesamiento=lote.suma_procesamiento / procesadas if procesadas else 0.0,
            promedio_espera=lote.suma_espera / procesadas if procesadas else 0.0,
            asientos_duplicados=duplicados,
            aprobada=aprobada,
            detalle_verificacion=detalle,
            es_prueba_conflicto=self._es_prueba_conflicto,
        )
        if self._verificar is not None:
            correcta, detalle_extra = self._verificar(resumen)
            resumen.aprobada = resumen.aprobada and correcta
            resumen.detalle_verificacion = detalle_extra
        return resumen


def plan_carga_distribuida(cine: Cine, usuarios: int, por_usuario: int, semilla: int) -> Plan:
    """Reparte las solicitudes entre todas las funciones y asientos, minimizando los choques."""
    lugares = [(f.id, codigo) for f in cine.funciones() for codigo in f.asientos]
    random.Random(semilla).shuffle(lugares)
    plan: Plan = []
    for u in range(usuarios):
        objetivos = [lugares[(u * por_usuario + k) % len(lugares)] for k in range(por_usuario)]
        plan.append((f"Usuario {u + 1}", objetivos))
    return plan


def plan_alta_contencion(
    funcion: Funcion, usuarios: int, por_usuario: int, asientos_en_disputa: int, semilla: int
) -> Plan:
    """Todos los usuarios piden un conjunto muy reducido de asientos de la misma función."""
    codigos = list(funcion.asientos)[:asientos_en_disputa]
    rng = random.Random(semilla)
    return [
        (f"Usuario {u + 1}", [(funcion.id, rng.choice(codigos)) for _ in range(por_usuario)])
        for u in range(usuarios)
    ]


class ControladorSimulaciones:
    """Punto de entrada de la interfaz para lanzar y detener simulaciones (una por vez)."""

    def __init__(self, cine: Cine, sistema: SistemaReservas) -> None:
        self._cine = cine
        self._sistema = sistema
        self._actual: Simulacion | None = None

    def en_curso(self) -> bool:
        return self._actual is not None and self._actual.is_alive()

    @property
    def nombre_actual(self) -> str | None:
        return self._actual.nombre if self.en_curso() else None

    def _describir(self, usuarios: int, por_usuario: int, extra: str = "") -> str:
        texto = (
            f"Usuarios: {usuarios} | Solicitudes/usuario: {por_usuario} | "
            f"Workers: {self._sistema.cantidad_workers} | "
            f"Demora: {self._sistema.demora_procesamiento * 1000:.0f} ms"
        )
        return f"{texto} | {extra}" if extra else texto

    def _iniciar(self, simulacion: Simulacion) -> None:
        if self.en_curso():
            raise RuntimeError("Ya hay una simulación en curso")
        self._actual = simulacion
        simulacion.start()

    def iniciar_carga_distribuida(
        self, usuarios: int, por_usuario: int, semilla: int, reiniciar: bool
    ) -> None:
        self._iniciar(
            Simulacion(
                "Carga distribuida",
                self._describir(usuarios, por_usuario, f"Semilla: {semilla}"),
                plan_carga_distribuida(self._cine, usuarios, por_usuario, semilla),
                self._sistema,
                preparar=self._cine.reiniciar_todo if reiniciar else None,
            )
        )

    def iniciar_alta_contencion(
        self,
        funcion_id: str,
        usuarios: int,
        por_usuario: int,
        asientos_en_disputa: int,
        semilla: int,
        reiniciar: bool,
    ) -> None:
        funcion = self._cine.obtener_funcion(funcion_id)
        extra = f"{funcion.descripcion} | Asientos en disputa: {asientos_en_disputa} | Semilla: {semilla}"
        self._iniciar(
            Simulacion(
                "Alta contención",
                self._describir(usuarios, por_usuario, extra),
                plan_alta_contencion(funcion, usuarios, por_usuario, asientos_en_disputa, semilla),
                self._sistema,
                preparar=self._cine.reiniciar_todo if reiniciar else None,
            )
        )

    def iniciar_prueba_conflicto(self, funcion_id: str, cantidad: int = CANTIDAD_MINIMA_CONFLICTO) -> None:
        cantidad = max(cantidad, CANTIDAD_MINIMA_CONFLICTO)
        funcion = self._cine.obtener_funcion(funcion_id)
        plan: Plan = [(f"Usuario {u + 1}", [(funcion_id, ASIENTO_CONFLICTO)]) for u in range(cantidad)]

        def verificar(resumen: ResumenSimulacion) -> tuple[bool, str]:
            reservados = self._cine.contar_reservados(funcion_id)
            correcta = (
                resumen.exitosas == 1
                and resumen.rechazadas == cantidad - 1
                and reservados == 1
            )
            return correcta, (
                f"Reservas exitosas: {resumen.exitosas} | "
                f"Reservas rechazadas: {resumen.rechazadas} (esperado: 1 y {cantidad - 1})"
            )

        self._iniciar(
            Simulacion(
                "Prueba de conflicto",
                self._describir(cantidad, 1, f"{funcion.descripcion} | Asiento: {ASIENTO_CONFLICTO}"),
                plan,
                self._sistema,
                preparar=lambda: self._cine.reiniciar_funcion(funcion_id),
                verificar=verificar,
                es_prueba_conflicto=True,
            )
        )

    def detener(self, timeout: float = 5.0) -> None:
        if self._actual is not None and self._actual.is_alive():
            self._actual.detener()
            self._actual.join(timeout)
