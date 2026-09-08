"""Cálculo de métricas a partir de la rúbrica de concurrencia."""

from modelos.modelos import MetricaConcurrencia
from reportes.excel import leer_fila_por_codigo
from constantes.definiciones import (
    HOJA_CONCURRENCIA_ENTRADA,
    ENCABEZADOS_CONCURRENCIA_ENTRADA,
)

def obtener_puntaje(valor, ponderacion):
    """Convierte un nivel textual en su puntaje configurado.

    Args:
        valor: Nivel de concurrencia evaluado.
        ponderacion: Mapa de niveles a puntajes.

    Returns:
        Puntaje asociado con el nivel.

    Raises:
        ValueError: Si el nivel no está contemplado.
    """
    nivel = "" if valor is None else str(valor).strip().capitalize()
    if nivel not in ponderacion:
        raise ValueError(f"Nivel de concurrencia inválido: {valor}")
    return ponderacion[nivel]


def calcular_puntajes(valores, ponderacion):
    """Calcula los puntajes de todos los criterios de una rúbrica.

    Args:
        valores: Valores textuales de los criterios.
        ponderacion: Mapa de niveles a puntajes.

    Returns:
        Lista de puntajes calculados.
    """
    puntajes = []
    for valor in valores.values():
        puntajes.append(obtener_puntaje(valor, ponderacion))
    return puntajes


def calcular_promedio(puntajes):
    """Calcula el promedio de una colección de puntajes.

    Args:
        puntajes: Puntajes que se desean promediar.

    Returns:
        El promedio redondeado a dos decimales, o cero sin datos.
    """
    if not puntajes:
        return 0
    return round(sum(puntajes) / len(puntajes), 2)


def interpretar_concurrencia(promedio, umbrales):
    """Obtiene la interpretación correspondiente a un promedio.

    Args:
        promedio: Puntaje promedio de concurrencia.
        umbrales: Intervalos de interpretación configurados.

    Returns:
        La interpretación encontrada o un texto sustituto.
    """
    for umbral in umbrales:
        minimo = umbral["min"]
        maximo = umbral["max"]
        if maximo is None and promedio >= minimo:
            return umbral["interpretacion"]
        if maximo is not None and minimo <= promedio <= maximo:
            return umbral["interpretacion"]
    return "Sin interpretación"


def crear_metrica_concurrencia(codigo, valores, ponderacion, umbrales):
    """Construye la métrica de concurrencia de un proyecto.

    Args:
        codigo: Código del proyecto.
        valores: Valores obtenidos de la rúbrica.
        ponderacion: Mapa de niveles a puntajes.
        umbrales: Intervalos de interpretación.

    Returns:
        La métrica de concurrencia calculada.
    """
    puntajes = calcular_puntajes(valores, ponderacion)
    promedio = calcular_promedio(puntajes)
    interpretacion = interpretar_concurrencia(promedio, umbrales)
    return MetricaConcurrencia(
        codigo=codigo,
        sincronizacion_correcta=valores["Sincronización correcta"],
        ausencia_de_deadlocks=valores["Ausencia de deadlocks"],
        ausencia_de_condicion_de_carrera=valores["Ausencia de condición de carrera"],
        uso_correcto_de_exclusion_mutua=valores["Uso correcto de exclusión mutua"],
        promedio=promedio,
        interpretacion=interpretacion,
    )


def analizar_concurrencia(proyecto, libro_entrada, ponderacion, umbrales, logger):
    """Analiza la rúbrica de concurrencia de un proyecto.

    Args:
        proyecto: Proyecto que se desea evaluar.
        ponderacion: Mapa de niveles a puntajes.
        umbrales: Intervalos de interpretación.
        logger: Logger de la aplicación.

    Returns:
        La métrica de concurrencia calculada.

    Raises:
        ValueError: Si el proyecto no aparece en la rúbrica.
    """
    logger.debug(f"[{proyecto.codigo}] Leyendo rúbrica de concurrencia")

    valores = leer_fila_por_codigo(
        libro_entrada=libro_entrada,
        nombre_hoja=HOJA_CONCURRENCIA_ENTRADA,
        codigo=proyecto.codigo,
        campos=ENCABEZADOS_CONCURRENCIA_ENTRADA,
    )

    return crear_metrica_concurrencia(proyecto.codigo, valores, ponderacion, umbrales)
