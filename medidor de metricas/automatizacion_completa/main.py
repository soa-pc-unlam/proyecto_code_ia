"""Orquesta el análisis concurrente de métricas y la generación de reportes."""

from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass

from util.analisis import (
    analizar_bugs_smells_seguro,
    analizar_complejidad,
    analizar_concurrencia_seguro,
    analizar_mi,
)
from configuracion.configuracion import cargar_configuracion, cargar_proyectos
from constantes import definiciones
from reportes.excel import (
    crear_o_abrir_excel,
    finalizar_libro,
    guardar_error_excel,
    guardar_resultado_excel,
)
from util.archivos import crear_directorio
from util.logging_config import configurar_logger
from util.modelos import ContextoAnalisis
from util.modelos import ResultadoProyecto
from threading import Semaphore
from util.recursos import limitar_cpu


def main():
    """Ejecuta concurrentemente el análisis de los proyectos configurados."""
    configuracion = cargar_configuracion(definiciones.CONFIGURACION_JSON)
    inicializar_directorios(configuracion)

    logger = configurar_logger(configuracion["carpeta_logs"])
    logger.info("Inicio del análisis de métricas")

    limitar_cpu(definiciones.PORCENTAJE_MAX_CPU,logger)

    proyectos = cargar_proyectos(definiciones.DATOS_PROYECTOS_JSON)
    libro = crear_o_abrir_excel(configuracion["archivo_excel"])

    gestionar_procesamiento_proyectos(proyectos, configuracion, libro, logger)
   
    finalizar_libro(
        libro,
        configuracion["archivo_excel"],
        incluir_graficos=True,
    )

    informar_resultados_finales(logger, configuracion)

def gestionar_procesamiento_proyectos(proyectos, configuracion, libro, logger):
    """Gestiona la ejecución concurrente de los análisis de los proyectos.

    Args:
        proyectos: Lista de proyectos a analizar.
        configuracion: Configuración que contiene rutas y umbrales.
        libro: Libro de Excel donde se guardan los resultados.
        logger: Registrador de eventos de la ejecución.
    """
    semaforo_analizadores = Semaphore(definiciones.MAX_ANALIZADORES_PESADOS)

    with ThreadPoolExecutor(max_workers=definiciones.MAX_WORKERS) as executor:
        futuros = {
            executor.submit(
                procesar_proyecto, proyecto, configuracion, logger,semaforo_analizadores,
            ): proyecto for proyecto in proyectos
        }

        for futuro in as_completed(futuros):
            proyecto = futuros[futuro]
            try:
                resultado = futuro.result()
                guardar_resultado_proyecto(libro, resultado, logger)
            except Exception as error:
                mensaje_error = f"Error en el procesamiento del proyecto: {error}"
                logger.exception(f"{proyecto.codigo}: {mensaje_error}")
                guardar_error_excel(
                    libro=libro,
                    proyecto=proyecto,
                    mensaje_error=mensaje_error,
                )

                
def inicializar_directorios(configuracion):
    """Crea los directorios requeridos por la aplicación.

    Args:
        configuracion: Configuración que contiene las rutas de resultados y logs.
    """
    crear_directorio(configuracion["carpeta_resultados"])
    crear_directorio(configuracion["carpeta_logs"])

def procesar_proyecto(proyecto, configuracion, logger, semaforo_analizadores):
    """Ejecuta todos los análisis de un proyecto y devuelve sus resultados.

    Esta función puede ejecutarse desde un worker porque no modifica el libro
    de Excel. Cada proyecto crea su propio ``ContextoAnalisis``.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Parámetros y rutas usados por los analizadores.
        logger: Registrador de eventos de la ejecución.

    Returns:
        ResultadoProyecto con las métricas y errores del proyecto.
    """
    contexto = ContextoAnalisis()

    metricas_cc = None
    metricas_mi = None
    metricas_bugs_smells = None
    metricas_concurrencia = None

    try:
        logger.info(
            f"Analizando proyecto {proyecto.codigo} - "
            f"{proyecto.nombre_proyecto}"
        )

        #uso semaforo porque porque Lizard consume mucha CPU y memoria, 
        # y no quiero que se ejecuten varios a la vez
        with semaforo_analizadores:
            metricas_cc = analizar_complejidad(proyecto, configuracion, logger, contexto)

        metricas_mi = analizar_mi(proyecto, configuracion, logger, contexto)

        #uso semaforo porque porque PMD, Pylint o Detekt consume mucha CPU y memoria, 
        # y no quiero que se ejecuten varios a la vez
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
        logger.exception(f"{proyecto.codigo}: {mensaje_error}")
        contexto.errores.append(mensaje_error)

    return ResultadoProyecto(
        proyecto=proyecto,
        metricas_cc=metricas_cc,
        metricas_mi=metricas_mi,
        metricas_bugs_smells=metricas_bugs_smells,
        metricas_concurrencia=metricas_concurrencia,
        errores=list(contexto.errores),
    )

def guardar_resultado_proyecto(libro, resultado, logger):
    """Guarda en Excel las métricas y errores de un proyecto.

    Esta función se ejecuta únicamente desde el hilo principal, evitando que
    varios workers modifiquen simultáneamente el libro de Excel.

    Args:
        libro: Libro de Excel donde se guardan los resultados.
        resultado: ResultadoProyecto generado por un worker.
        logger: Registrador de eventos de la ejecución.
    """
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

def informar_resultados_finales(logger, configuracion):
    """Informa la finalización del proceso y la ubicación del reporte.

    Args:
        logger: Registrador de eventos de la ejecución.
        configuracion: Configuración que contiene la ruta del reporte.
    """
    logger.info("")
    logger.info("=" * 40)
    logger.info("== Proceso finalizado ==")
    logger.info("=" * 40)
    logger.info(
        "Informe creado en el archivo Excel: "
        + str(configuracion["archivo_excel"])
    )


if __name__ == "__main__":
    main()
