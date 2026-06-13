from dataclasses import dataclass


@dataclass
class Proyecto:
    codigo: str
    nombre_proyecto: str
    ruta_codigo: str
    herramienta_ia: str
    modelo_ia: str
    lenguaje: str


@dataclass
class MetricaComplejidad:
    cantidad_funciones: int
    ccn_total: int
    ccn_promedio: float
    nloc_total: int
    nloc_promedio: float
    nivel_cc: str = ""
    interpretacion_cc: str = ""
