"""Modelos de datos utilizados por el análisis y los reportes."""

from dataclasses import dataclass, field


@dataclass
class Proyecto:
    """Describe un proyecto de código fuente que debe analizarse.

    Attributes:
        codigo: Identificador único del proyecto.
        nombre_proyecto: Nombre descriptivo del proyecto.
        ruta_codigo: Ruta al código fuente.
        herramienta_ia: Herramienta de IA empleada en el proyecto.
        modelo_ia: Modelo de IA empleado.
        lenguaje: Lenguaje de programación principal.
    """
    codigo: str
    nombre_proyecto: str
    ruta_codigo: str
    herramienta_ia: str
    modelo_ia: str
    lenguaje: str


@dataclass
class ContextoAnalisis:
    """Agrupa datos auxiliares compartidos durante el análisis de un proyecto.

    Attributes:
        errores: Mensajes de error generados por los distintos análisis.
        archivo_csv_lizard: Ruta del archivo CSV generado por Lizard.
    """

    errores: list[str] = field(default_factory=list)
    archivo_csv_lizard: str | None = None


@dataclass
class FuncionCompleja:
    """Representa una función destacada por su complejidad.

    Attributes:
        ccn: Complejidad ciclomática de la función.
        nloc: Cantidad de líneas de código de la función.
        nombre: Nombre de la función.
        archivo: Archivo donde está definida.
    """
    ccn: int
    nloc: int
    nombre: str
    archivo: str


@dataclass
class MetricaComplejidad:
    """Agrupa los resultados del análisis de complejidad ciclomática.

    Attributes:
        cantidad_funciones: Cantidad total de funciones analizadas.
        ccn_total: Suma de la complejidad ciclomática.
        ccn_promedio: Complejidad ciclomática promedio.
        nloc_total: Cantidad total de líneas de código.
        nloc_promedio: Promedio de líneas de código por función.
        nivel_cc: Nivel asignado a la complejidad promedio.
        interpretacion_cc: Explicación del nivel de complejidad.
        funciones_complejas: Funciones con mayor complejidad detectada.
    """
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
    """Agrupa los resultados del índice de mantenibilidad.

    Attributes:
        nloc_mi: Líneas de código consideradas para el cálculo.
        cantidad_funciones_mi: Cantidad de funciones consideradas.
        tokens_codigo: Cantidad total de tokens de código.
        mi: Índice de mantenibilidad promedio.
        nivel_mi: Nivel asignado al índice.
        interpretacion_mi: Explicación del nivel de mantenibilidad.
        archivos: Detalle de métricas por archivo, si está disponible.
    """
    nloc_mi: int
    cantidad_funciones_mi: int
    tokens_codigo: int
    mi: float
    nivel_mi: str = ""
    interpretacion_mi: str = ""
    archivos: dict | None = None


@dataclass
class MetricaBugsSmells:
    """Agrupa las incidencias y los indicadores de bugs y code smells.

    Attributes:
        analizador: Herramienta que detectó las incidencias.
        total_issues: Cantidad total de incidencias.
        issues_kloc: Incidencias por cada mil líneas de código.
        isi: Índice de severidad de incidencias.
        nivel_isi: Nivel asignado al índice de severidad.
        interpretacion_isi: Explicación del nivel de severidad.
        observacion: Comentario adicional sobre el análisis.
        cantidad_baja: Cantidad de incidencias de severidad baja.
        cantidad_media: Cantidad de incidencias de severidad media.
        cantidad_alta: Cantidad de incidencias de severidad alta.
        top_reglas_violadas: Reglas más incumplidas y sus frecuencias.
    """
    analizador: str
    total_issues: int
    issues_kloc: float
    isi: float
    nivel_isi: str
    interpretacion_isi: str
    observacion: str
    cantidad_baja: int
    cantidad_media: int
    cantidad_alta: int
    top_reglas_violadas: list[tuple[str, int]] = field(default_factory=list)


@dataclass
class MetricaConcurrencia:
    """Agrupa la evaluación de los criterios de concurrencia.

    Attributes:
        codigo: Código del proyecto evaluado.
        sincronizacion_correcta: Nivel de sincronización observado.
        ausencia_de_deadlocks: Nivel de ausencia de bloqueos mutuos.
        ausencia_de_condicion_de_carrera: Nivel de ausencia de carreras.
        uso_correcto_de_exclusion_mutua: Nivel de uso de exclusión mutua.
        promedio: Puntaje promedio de los criterios.
        interpretacion: Interpretación del promedio obtenido.
    """
    codigo: str
    sincronizacion_correcta: str
    ausencia_de_deadlocks: str
    ausencia_de_condicion_de_carrera: str
    uso_correcto_de_exclusion_mutua: str
    promedio: float
    interpretacion: str
