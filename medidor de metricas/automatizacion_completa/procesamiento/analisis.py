"""Ejecuta los análisis de métricas y controla sus posibles errores."""

from configuracion.configuracion import clasificar_ccn
from metricas.bugs_smells import analizar_bugs_smells
from metricas.complejidad import ejecutar_lizard
from metricas.concurrencia import analizar_concurrencia
from metricas.mantenibilidad import analizar_mantenibilidad
from metricas.tokens import analizar_tokens
from metricas.cpu_memoria import analizar_cpu_memoria
from constantes.definiciones import(
    TEXTO_CAMPO_CARPETA_RESULTADOS,
    TEXTO_CAMPO_COEFICIENTE_PENALIZACION_TOKENS,
    TEXTO_CAMPO_PONDERACION_CONCURRENCIA,
    TEXTO_CAMPO_UMBRALES_CC,
    TEXTO_CAMPO_UMBRALES_CONCURRENCIA,
    TEXTO_CAMPO_UMBRALES_ISI,
    TEXTO_CAMPO_UMBRALES_MI,
    TEXTO_CAMPO_UMBRALES_CPU,
    TEXTO_CAMPO_UMBRALES_MEM,
    TEXTO_CAMPO_UMBRALES_TOKEN
)



def analizar_complejidad(proyecto, configuracion, logger, contexto):
    """Analiza y clasifica la complejidad de un proyecto de forma segura.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de rutas y umbrales.
        logger: Registrador de eventos de la ejecución.
        contexto: Contexto exclusivo del proyecto analizado.

    Returns:
        Las métricas de complejidad o ``None`` si el análisis falla.
    """
    try:
        metricas, archivo_csv = ejecutar_lizard(
            proyecto=proyecto,
            carpeta_resultados=configuracion[TEXTO_CAMPO_CARPETA_RESULTADOS],
            logger=logger,
        )

        contexto.archivo_csv_lizard = archivo_csv

        nivel_cc, interpretacion_cc = clasificar_ccn(
            metricas.ccn_promedio,
            configuracion[TEXTO_CAMPO_UMBRALES_CC],
        )
        metricas.nivel_cc = nivel_cc
        metricas.interpretacion_cc = interpretacion_cc
        return metricas
    except Exception as error:
        mensaje_error = f"Análisis complejidad: {error}"
        logger.error(f"[{proyecto.codigo}] {mensaje_error}")
        contexto.errores.append(mensaje_error)
        return None



def analizar_mi(proyecto, configuracion, logger, contexto):
    """Calcula el índice de mantenibilidad de un proyecto de forma segura.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de rutas y umbrales.
        logger: Registrador de eventos de la ejecución.
        contexto: Contexto exclusivo del proyecto analizado.

    Returns:
        Las métricas de mantenibilidad o ``None`` si el análisis falla.
    """
    try:
        if contexto.archivo_csv_lizard is None:
            raise ValueError("No se generó el CSV de Lizard en esta ejecución")

        return analizar_mantenibilidad(
            proyecto=proyecto,
            archivo_csv_lizard=contexto.archivo_csv_lizard,
            carpeta_resultados=configuracion[TEXTO_CAMPO_CARPETA_RESULTADOS],
            umbrales_mi=configuracion[TEXTO_CAMPO_UMBRALES_MI],
            logger=logger,
        )
    except Exception as error:
        mensaje_error = f"Análisis mantenibilidad: {error}"
        logger.error(f"[{proyecto.codigo}] {mensaje_error}")
        contexto.errores.append(mensaje_error)
        return None



def analizar_bugs_smells_seguro(
    proyecto,
    configuracion,
    logger,
    metricas_mi,
    contexto,
):
    """Analiza bugs y code smells, registrando los errores producidos.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de rutas y umbrales.
        logger: Registrador de eventos de la ejecución.
        metricas_mi: Métricas de mantenibilidad usadas para obtener NLOC.
        contexto: Contexto exclusivo del proyecto analizado.

    Returns:
        Las métricas de incidencias o ``None`` si faltan datos o el análisis
        falla.
    """
    if metricas_mi is None:
        return None

    try:
        return analizar_bugs_smells(
            proyecto=proyecto,
            carpeta_resultados=configuracion[TEXTO_CAMPO_CARPETA_RESULTADOS],
            umbrales_isi=configuracion[TEXTO_CAMPO_UMBRALES_ISI],
            logger=logger,
            loc_codigo=metricas_mi.nloc_mi,
        )
    except Exception as error:
        mensaje_error = f"Análisis bugs/smells: {error}"
        logger.error(f"[{proyecto.codigo}] {mensaje_error}")
        contexto.errores.append(mensaje_error)
        return None



def analizar_concurrencia_seguro(proyecto, configuracion, logger, libro_entrada, contexto):
    """Analiza las métricas de concurrencia y registra posibles errores.

    Args:
        libro_entrada: Libro abierto que contiene la rúbrica de concurrencia.
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de la rúbrica y sus umbrales.
        logger: Registrador de eventos de la ejecución.
        contexto: Contexto exclusivo del proyecto analizado.

    Returns:
        Las métricas de concurrencia o ``None`` si el análisis falla.
    """
    try:
        return analizar_concurrencia(
            proyecto=proyecto,
            ponderacion=configuracion[TEXTO_CAMPO_PONDERACION_CONCURRENCIA],
            umbrales=configuracion[TEXTO_CAMPO_UMBRALES_CONCURRENCIA],
            logger=logger,
            libro_entrada=libro_entrada
        )
    except Exception as error:
        mensaje_error = f"Análisis concurrencia: {error}"
        logger.error(f"[{proyecto.codigo}] {mensaje_error}")
        contexto.errores.append(mensaje_error)
        return None


def analizar_tokens_seguro(
    proyecto, configuracion, logger, libro_entrada, metricas_cc, contexto
):
    """Analiza tokens y registra el error sin interrumpir el proyecto."""
    try:
        if metricas_cc is None:
            raise ValueError("No se obtuvo NlocTotal del análisis de Lizard")
        return analizar_tokens(
            proyecto=proyecto,
            libro_entrada=libro_entrada,
            nloc_total=metricas_cc.nloc_total,
            umbrales=configuracion[TEXTO_CAMPO_UMBRALES_TOKEN],
            coeficiente=configuracion[TEXTO_CAMPO_COEFICIENTE_PENALIZACION_TOKENS],
            logger=logger,
        )
    except Exception as error:
        mensaje_error = f"Análisis tokens: {error}"
        logger.error(f"[{proyecto.codigo}] {mensaje_error}")
        contexto.errores.append(mensaje_error)
        return None

def analizar_cpu_memoria_seguro(proyecto, configuracion, logger, libro_entrada, contexto):
    """Analiza CPU/memoria y registra el error sin interrumpir el proyecto."""
    try:
        return analizar_cpu_memoria(
            proyecto=proyecto,
            libro_entrada=libro_entrada,
            configuracion_cpu=configuracion[TEXTO_CAMPO_UMBRALES_CPU],
            configuracion_memoria=configuracion[TEXTO_CAMPO_UMBRALES_MEM],
            logger=logger,
        )
    except Exception as error:
        mensaje_error = f"Análisis CPU/memoria: {error}"
        logger.error(f"[{proyecto.codigo}] {mensaje_error}")
        contexto.errores.append(mensaje_error)
        return None

