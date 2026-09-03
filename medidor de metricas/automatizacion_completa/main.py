"""Orquesta el análisis de métricas y la generación de reportes."""

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


def main():
    """Ejecuta el flujo completo de análisis para los proyectos configurados."""
    configuracion = cargar_configuracion(definiciones.CONFIGURACION_JSON)
    inicializar_directorios(configuracion)

    logger = configurar_logger(configuracion["carpeta_logs"])
    logger.info("Inicio del análisis de métricas")

    proyectos = cargar_proyectos(definiciones.DATOS_PROYECTOS_JSON)

    libro = crear_o_abrir_excel(configuracion["archivo_excel"])
    for proyecto in proyectos:
        procesar_proyecto(proyecto, configuracion, logger, libro)

    finalizar_libro(
        libro,
        configuracion["archivo_excel"],
        incluir_graficos=True,
    )

    informar_resultados_finales(logger, configuracion)


def inicializar_directorios(configuracion):
    """Crea los directorios requeridos por la aplicación.

    Args:
        configuracion: Configuración que contiene las rutas de resultados y logs.
    """
    crear_directorio(configuracion["carpeta_resultados"])
    crear_directorio(configuracion["carpeta_logs"])


def procesar_proyecto(proyecto, configuracion, logger, libro):
    """Ejecuta todos los análisis y guarda los resultados de un proyecto.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Parámetros y rutas usados por los analizadores.
        logger: Registrador de eventos de la ejecución.
        libro: Libro de Excel en el que se guardan los resultados.
    """
    try:
        logger.info(
            f"Analizando proyecto {proyecto.codigo} - "
            f"{proyecto.nombre_proyecto}"
        )

        metricas_cc, archivo_csv_lizard = analizar_complejidad(
            proyecto, configuracion, logger, libro
        )
        metricas_mi = analizar_mi(
            proyecto, configuracion, logger, libro, archivo_csv_lizard
        )
        metricas_bugs_smells = analizar_bugs_smells_seguro(
            proyecto=proyecto,
            configuracion=configuracion,
            logger=logger,
            metricas_mi=metricas_mi,
            libro=libro,
        )
        metricas_concurrencia = analizar_concurrencia_seguro(
            proyecto=proyecto,
            configuracion=configuracion,
            logger=logger,
            libro=libro,
        )

        if metricas_cc is None or metricas_mi is None:
            logger.warning(
                f"Se omite el reporte completo de {proyecto.codigo}: "
                "faltan métricas esenciales"
            )
            return

        guardar_resultado_excel(
            libro=libro,
            proyecto=proyecto,
            metricas_cc=metricas_cc,
            metricas_mi=metricas_mi,
            metricas_bugs_smells=metricas_bugs_smells,
            metricas_concurrencia=metricas_concurrencia,
        )

        registrar_fin_proyecto(
            logger=logger,
            proyecto=proyecto,
            metricas_cc=metricas_cc,
            metricas_mi=metricas_mi,
            metricas_bugs_smells=metricas_bugs_smells,
            metricas_concurrencia=metricas_concurrencia,
        )
    except Exception as error:
        mensaje_error = str(error)
        logger.error(f"Error procesando {proyecto.codigo}: {mensaje_error}")
        guardar_error_excel(
            libro=libro,
            proyecto=proyecto,
            mensaje_error=mensaje_error,
        )


def registrar_fin_proyecto(
    logger,
    proyecto,
    metricas_cc,
    metricas_mi,
    metricas_bugs_smells,
    metricas_concurrencia,
):
    """Registra un resumen de las métricas calculadas para un proyecto.

    Args:
        logger: Registrador de eventos de la ejecución.
        proyecto: Proyecto analizado.
        metricas_cc: Métricas de complejidad calculadas.
        metricas_mi: Métricas de mantenibilidad calculadas.
        metricas_bugs_smells: Métricas de incidencias o ``None``.
        metricas_concurrencia: Métricas de concurrencia o ``None``.
    """
    logger.info(
        f"Finalizado {proyecto.codigo}: "
        f"CCN promedio={metricas_cc.ccn_promedio}, "
        f"Nivel CC={metricas_cc.nivel_cc}, "
        f"MI={metricas_mi.mi}, "
        f"Nivel MI={metricas_mi.nivel_mi}, "
        f"Issues/KLOC="
        f"{metricas_bugs_smells.issues_kloc if metricas_bugs_smells else 'Sin datos'}, "
        f"ISI={metricas_bugs_smells.isi if metricas_bugs_smells else 'Sin datos'}, "
        f"Promedio concurrencia="
        f"{metricas_concurrencia.promedio if metricas_concurrencia else 'Sin datos'}"
    )


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
