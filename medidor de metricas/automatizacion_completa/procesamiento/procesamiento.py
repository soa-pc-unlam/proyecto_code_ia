"""Gestiona el procesamiento concurrente de los proyectos."""

from asyncio.log import logger
from asyncio.log import logger
from concurrent.futures import ThreadPoolExecutor
from threading import Semaphore

from constantes import definiciones
from reportes.excel import guardar_error_excel, guardar_resultado_excel
from procesamiento.analisis import (
    analizar_bugs_smells_seguro,
    analizar_complejidad,
    analizar_concurrencia_seguro,
    analizar_mi,
)
from modelos.modelos import ContextoAnalisis, ResultadoProyecto


def gestionar_procesamiento_proyectos(proyectos, configuracion, libro, logger):
    """Gestiona la ejecución concurrente de los análisis de los proyectos."""
    semaforo_analizadores = Semaphore(definiciones.MAX_ANALIZADORES_PESADOS)

    with ThreadPoolExecutor(max_workers=definiciones.MAX_WORKERS) as executor:
        futuros = ejecutar_procesamiento_proyectos(
            executor,
            proyectos,
            configuracion,
            logger,
            semaforo_analizadores,
        )

        for futuro, proyecto in futuros.items():
            try:
                resultado = futuro.result()
                guardar_resultado_proyecto(libro, resultado, logger)
            except Exception as error:
                mensaje_error = f"Error en el procesamiento del proyecto: {error}"
                logger.exception(f"[{proyecto.codigo}] {mensaje_error}")
                guardar_error_excel(
                    libro=libro,
                    proyecto=proyecto,
                    mensaje_error=mensaje_error,
                )


def ejecutar_procesamiento_proyectos(
    executor, proyectos, configuracion, logger, semaforo_analizadores
):
    """Envía cada proyecto al pool de workers y devuelve sus Future asociados."""
    futuros = {}

    for proyecto in proyectos:
        futuro = executor.submit(
            procesar_proyecto,
            proyecto,
            configuracion,
            logger,
            semaforo_analizadores,
        )
        futuros[futuro] = proyecto

    return futuros


def procesar_proyecto(proyecto, configuracion, logger, semaforo_analizadores):
    """Ejecuta todos los análisis de un proyecto y devuelve sus resultados."""
    contexto = ContextoAnalisis()

    metricas_cc = None
    metricas_mi = None
    metricas_bugs_smells = None
    metricas_concurrencia = None

    try:
        logger.info(f"[{proyecto.codigo}] Inicio - {proyecto.nombre_proyecto}")

        # Lizard consume bastante CPU y memoria, por eso se limita su ejecución.
        with semaforo_analizadores:
            metricas_cc = analizar_complejidad(
                proyecto,
                configuracion,
                logger,
                contexto,
            )

        metricas_mi = analizar_mi(
            proyecto,
            configuracion,
            logger,
            contexto,
        )

        # PMD, Pylint y Detekt también pueden consumir muchos recursos.
        with semaforo_analizadores:
            metricas_bugs_smells = analizar_bugs_smells_seguro(
                proyecto=proyecto,
                configuracion=configuracion,
                logger=logger,
                metricas_mi=metricas_mi,
                contexto=contexto,
            )

        metricas_concurrencia = analizar_concurrencia_seguro(
            proyecto=proyecto,
            configuracion=configuracion,
            logger=logger,
            contexto=contexto,
        )

    except Exception as error:
        mensaje_error = f"Error procesando proyecto: {error}"
        logger.exception(f"[{proyecto.codigo}] {mensaje_error}")
        contexto.errores.append(mensaje_error)

    informar_fin_procesamiento(errores=contexto.errores,
                                logger=logger,
                                proyecto_codigo=proyecto.codigo,
                                proyecto_nombre=proyecto.nombre_proyecto
                            )

    return ResultadoProyecto(
        proyecto=proyecto,
        metricas_cc=metricas_cc,
        metricas_mi=metricas_mi,
        metricas_bugs_smells=metricas_bugs_smells,
        metricas_concurrencia=metricas_concurrencia,
        errores=list(contexto.errores),
    )

def informar_fin_procesamiento(errores, logger, proyecto_codigo=None, proyecto_nombre=None):
    if errores:
        logger.warning(f"[{proyecto_codigo}] Finalizado con {len(errores)} error(es)")
    else:
        logger.info(f"[{proyecto_codigo}] Finalizado correctamente")

def guardar_resultado_proyecto(libro, resultado, logger):
    """Guarda en Excel las métricas y errores de un proyecto."""
    proyecto = resultado.proyecto

    if resultado.metricas_cc is not None and resultado.metricas_mi is not None:
        guardar_resultado_excel(
            libro=libro,
            proyecto=proyecto,
            metricas_cc=resultado.metricas_cc,
            metricas_mi=resultado.metricas_mi,
            metricas_bugs_smells=resultado.metricas_bugs_smells,
            metricas_concurrencia=resultado.metricas_concurrencia,
        )

    for mensaje_error in resultado.errores or []:
        guardar_error_excel(
            libro=libro,
            proyecto=proyecto,
            mensaje_error=mensaje_error,
        )

    logger.debug(f"Resultados almacenados para {proyecto.codigo}")
