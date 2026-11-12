"""Estrategias del pipeline de métricas y sus dependencias.

Cada etapa solo conoce los datos necesarios para ejecutarse. El coordinador
conserva el orden de ejecución y no conoce las herramientas externas.
"""

from dataclasses import dataclass, field
from typing import Callable
from constantes.definiciones import CAMPOS_METRICAS, LENGUAJE_WIRING
from util.fuentes_wiring import FuentesProyecto, obtener_fuentes_proyecto


@dataclass
class EstadoPipeline:
    """Estado de una ejecución, aislado para cada proyecto."""

    proyecto: object
    lenguaje: str
    configuracion: object
    logger: object
    libro_entrada: object
    contexto: object
    fuentes_wiring: FuentesProyecto | None = None
    valores: dict = field(default_factory=lambda: dict.fromkeys(CAMPOS_METRICAS))


@dataclass(frozen=True)
class EtapaMetrica:
    """Strategy: ejecuta una métrica y almacena su resultado."""

    nombre: str
    ejecutar_funcion: Callable[[EstadoPipeline], object]
    requiere_semaforo: bool = False

    def ejecutar(self, estado: EstadoPipeline, semaforo):
        """Ejecuta una etapa, respetando los límites de analizadores pesados."""
        if self.requiere_semaforo:
            with semaforo:
                resultado = self.ejecutar_funcion(estado)
        else:
            resultado = self.ejecutar_funcion(estado)
        estado.valores[self.nombre] = resultado


class PipelineMetricas:
    """Pipeline configurable: etapas inyectadas y secuenciales por proyecto."""

    def __init__(self, etapas):
        self.etapas = tuple(etapas)
        nombres = [etapa.nombre for etapa in self.etapas]
        if len(nombres) != len(set(nombres)):
            raise ValueError("No se permiten etapas de métricas duplicadas")
        desconocidas = set(nombres) - set(CAMPOS_METRICAS)
        if desconocidas:
            raise ValueError(f"Métricas desconocidas: {sorted(desconocidas)}")

    def ejecutar(self, estado, semaforo):
        """Ejecuta todas las estrategias en el orden configurado."""
        for etapa in self.etapas:
            etapa.ejecutar(estado, semaforo)
        return estado.valores


class ValidadorFuentesProyecto:
    """Valida anticipadamente fuentes propias cuando el lenguaje lo requiere."""

    def validar(self, proyecto, lenguaje):
        if lenguaje.lower() == LENGUAJE_WIRING:
            return obtener_fuentes_proyecto(proyecto.ruta_codigo)
        return None
