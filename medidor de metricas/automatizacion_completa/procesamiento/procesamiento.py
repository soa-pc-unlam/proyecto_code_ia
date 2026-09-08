"""Gestiona el procesamiento concurrente de los proyectos."""

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


def gestionar_procesamiento_proyectos(proyectos, configuracion, libro_salida, libro_entrada, logger):
    """Analiza proyectos en paralelo y escribe sus resultados en el hilo llamador.

    Args:
        proyectos: Colección de proyectos que se desean analizar.
        configuracion: Configuración de rutas, ponderaciones y umbrales.
        libro_salida: Libro abierto que recibe las métricas y los errores en memoria.
        libro_entrada: Libro abierto que contiene la rúbrica de concurrencia.
        logger: Registrador de eventos de la ejecución.
    """
    semaforo_analizadores = Semaphore(definiciones.MAX_ANALIZADORES_PESADOS)

    with ThreadPoolExecutor(max_workers=definiciones.MAX_WORKERS) as executor:
        futuros = ejecutar_procesamiento_proyectos(
            executor,
            proyectos,
            configuracion,
            logger,
            libro_entrada,
            semaforo_analizadores,
        )

        for futuro, proyecto in futuros.items():
            try:
                resultado = futuro.result()
                guardar_resultado_proyecto(libro_salida, resultado, logger)
            except Exception as error:
                mensaje_error = f"Error en el procesamiento del proyecto: {error}"
                logger.exception(f"[{proyecto.codigo}] {mensaje_error}")
                guardar_error_excel(
                    libro=libro_salida,
                    proyecto=proyecto,
                    mensaje_error=mensaje_error,
                )


def ejecutar_procesamiento_proyectos(
    executor, proyectos, configuracion, logger, libro_entrada, semaforo_analizadores
):
    """Envía cada proyecto al ejecutor para su procesamiento concurrente.

    Args:
        executor: ThreadPoolExecutor al que se envían las tareas.
        proyectos: Colección de proyectos que se desean analizar.
        configuracion: Configuración de rutas, ponderaciones y umbrales.
        logger: Registrador de eventos de la ejecución.
        libro_entrada: Libro abierto que contiene la rúbrica de concurrencia.
        semaforo_analizadores: Semáforo compartido que limita los analizadores pesados.

    Returns:
        dict: Mapa de cada Future enviado al proyecto que le corresponde.
    """
    futuros = {}

    for proyecto in proyectos:
        futuro = executor.submit(
            procesar_proyecto,
            proyecto,
            configuracion,
            logger,
            libro_entrada,
            semaforo_analizadores,
        )
        futuros[futuro] = proyecto

    return futuros


def procesar_proyecto(proyecto, configuracion, logger, libro_entrada, semaforo_analizadores):
    """Ejecuta los análisis de un proyecto y acumula los errores detectados.

    Args:
        proyecto: Proyecto cuyo código y rúbrica se desean analizar.
        configuracion: Configuración de rutas, ponderaciones y umbrales.
        logger: Registrador de eventos de la ejecución.
        libro_entrada: Libro abierto que contiene la rúbrica de concurrencia.
        semaforo_analizadores: Semáforo compartido que limita los analizadores pesados.

    Returns:
        ResultadoProyecto: Métricas calculadas y errores; los análisis fallidos
            se representan con None.
    """
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
            libro_entrada=libro_entrada,
            contexto=contexto,
        )

    except Exception as error:
        mensaje_error = f"Error procesando proyecto: {error}"
        logger.exception(f"[{proyecto.codigo}] {mensaje_error}")
        contexto.errores.append(mensaje_error)

    informar_fin_procesamiento(errores=contexto.errores,
                                logger=logger,
                                proyecto_codigo=proyecto.codigo
                            )

    return ResultadoProyecto(
        proyecto=proyecto,
        metricas_cc=metricas_cc,
        metricas_mi=metricas_mi,
        metricas_bugs_smells=metricas_bugs_smells,
        metricas_concurrencia=metricas_concurrencia,
        errores=list(contexto.errores),
    )

def informar_fin_procesamiento(errores, logger, proyecto_codigo=None):
    """Registra si el proyecto terminó correctamente o con errores.

    Args:
        errores: Colección de mensajes de error del proyecto.
        logger: Registrador de eventos de la ejecución.
        proyecto_codigo: Código del proyecto para identificar sus mensajes; puede ser None.
    """
    if errores:
        logger.warning(f"[{proyecto_codigo}] Finalizado con {len(errores)} error(es)")
    else:
        logger.info(f"[{proyecto_codigo}] Finalizado correctamente")

def guardar_resultado_proyecto(libro_salida, resultado, logger):
    """Escribe las métricas y los errores del proyecto en el libro en memoria.

    Args:
        libro_salida: Libro abierto que recibe las métricas y los errores en memoria.
        resultado: ResultadoProyecto con las métricas disponibles y los errores.
        logger: Registrador de eventos de la ejecución.
    """
    proyecto = resultado.proyecto

    guardar_resultado_excel(
        libro=libro_salida,
        proyecto=proyecto,
        metricas_cc=resultado.metricas_cc,
        metricas_mi=resultado.metricas_mi,
        metricas_bugs_smells=resultado.metricas_bugs_smells,
        metricas_concurrencia=resultado.metricas_concurrencia,
    )

    for mensaje_error in resultado.errores or []:
        guardar_error_excel(
            libro=libro_salida,
            proyecto=proyecto,
            mensaje_error=mensaje_error,
        )

    logger.debug(f"Resultados almacenados para {proyecto.codigo}")