"""Ejecuta los análisis de métricas y controla sus posibles errores."""

from configuracion.configuracion import clasificar_ccn
from metricas.bugs_smells import analizar_bugs_smells
from metricas.complejidad import ejecutar_lizard
from metricas.concurrencia import analizar_concurrencia
from metricas.mantenibilidad import analizar_mantenibilidad
from reportes.excel import guardar_error_excel


def guardar_error_analisis(proyecto, logger, libro, mensaje_error):
    """Registra en el log y en Excel un error producido durante un análisis.

    Args:
        proyecto: Proyecto cuyo análisis produjo el error.
        logger: Registrador de eventos de la ejecución.
        libro: Libro de Excel en el que se registra el error.
        mensaje_error: Descripción del error producido.
    """
    logger.error(f"{proyecto.codigo}: {mensaje_error}")
    guardar_error_excel(
        libro=libro,
        proyecto=proyecto,
        mensaje_error=mensaje_error,
    )


def analizar_complejidad(proyecto, configuracion, logger, libro):
    """Analiza y clasifica la complejidad de un proyecto de forma segura.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de rutas y umbrales.
        logger: Registrador de eventos de la ejecución.
        libro: Libro de Excel usado para registrar errores.

    Returns:
        Una tupla con las métricas y la ruta del CSV generado. Si el análisis
        falla, ambos elementos son ``None``.
    """
    try:
        metricas, archivo_csv = ejecutar_lizard(
            proyecto=proyecto,
            carpeta_resultados=configuracion["carpeta_resultados"],
            logger=logger,
        )

        nivel_cc, interpretacion_cc = clasificar_ccn(
            metricas.ccn_promedio,
            configuracion["umbrales_cc"],
        )
        metricas.nivel_cc = nivel_cc
        metricas.interpretacion_cc = interpretacion_cc
        return metricas, archivo_csv
    except Exception as error:
        mensaje_error = f"Error en análisis de complejidad: {error}"
        guardar_error_analisis(proyecto, logger, libro, mensaje_error)
        return None, None


def analizar_mi(proyecto, configuracion, logger, libro, archivo_csv_lizard):
    """Calcula el índice de mantenibilidad de un proyecto de forma segura.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de rutas y umbrales.
        logger: Registrador de eventos de la ejecución.
        libro: Libro de Excel usado para registrar errores.
        archivo_csv_lizard: Ruta del CSV con las métricas de Lizard.

    Returns:
        Las métricas de mantenibilidad o ``None`` si el análisis falla.
    """
    try:
        if archivo_csv_lizard is None:
            raise ValueError("No se generó el CSV de Lizard en esta ejecución")

        return analizar_mantenibilidad(
            proyecto=proyecto,
            archivo_csv_lizard=archivo_csv_lizard,
            carpeta_resultados=configuracion["carpeta_resultados"],
            umbrales_mi=configuracion["umbrales_mi"],
            logger=logger,
        )
    except Exception as error:
        mensaje_error = f"Error en análisis de mantenibilidad: {error}"
        guardar_error_analisis(proyecto, logger, libro, mensaje_error)
        return None


def analizar_bugs_smells_seguro(proyecto, configuracion, logger, metricas_mi, libro):
    """Analiza bugs y code smells, registrando los errores producidos.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de rutas y umbrales.
        logger: Registrador de eventos de la ejecución.
        metricas_mi: Métricas de mantenibilidad usadas para obtener NLOC.
        libro: Libro de Excel usado para registrar errores.

    Returns:
        Las métricas de incidencias o ``None`` si faltan datos o el análisis
        falla.
    """
    if metricas_mi is None:
        return None

    try:
        return analizar_bugs_smells(
            proyecto=proyecto,
            carpeta_resultados=configuracion["carpeta_resultados"],
            umbrales_issues=configuracion["umbrales_issues"],
            umbrales_isi=configuracion["umbrales_isi"],
            logger=logger,
            loc_codigo=metricas_mi.nloc_mi,
        )
    except Exception as error:
        mensaje_error = f"Error en análisis de bugs/smells: {error}"
        guardar_error_analisis(proyecto, logger, libro, mensaje_error)
        return None


def analizar_concurrencia_seguro(proyecto, configuracion, logger, libro):
    """Analiza las métricas de concurrencia y registra posibles errores.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de la rúbrica y sus umbrales.
        logger: Registrador de eventos de la ejecución.
        libro: Libro de Excel usado para registrar errores.

    Returns:
        Las métricas de concurrencia o ``None`` si el análisis falla.
    """
    try:
        return analizar_concurrencia(
            proyecto=proyecto,
            archivo_datos_entrada=configuracion["archivo_datos_entrada"],
            ponderacion=configuracion["ponderacion_concurrencia"],
            umbrales=configuracion["umbrales_concurrencia"],
            logger=logger,
        )
    except Exception as error:
        mensaje_error = f"Error en análisis de concurrencia: {error}"
        guardar_error_analisis(proyecto, logger, libro, mensaje_error)
        return None
