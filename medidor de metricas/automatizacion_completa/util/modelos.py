from dataclasses import dataclass, field


@dataclass
class Proyecto:
    codigo: str
    nombre_proyecto: str
    ruta_codigo: str
    herramienta_ia: str
    modelo_ia: str
    lenguaje: str


@dataclass
class FuncionCompleja:
    ccn: int
    nloc: int
    nombre: str
    archivo: str


@dataclass
class MetricaComplejidad:
    cantidad_funciones: int
    ccn_total: int
    ccn_promedio: float
    nloc_total: int
    nloc_promedio: float
    nivel_cc: str = ""
    interpretacion_cc: str = ""
    funciones_complejas: list[FuncionCompleja] = field(default_factory=list)


@dataclass
class MetricaMantenibilidad:
    nloc_mi: int
    cantidad_funciones_mi: int
    tokens_codigo: int
    mi: float
    nivel_mi: str = ""
    interpretacion_mi: str = ""
    archivos: dict | None = None
