"""Ejecuta los análisis de métricas y controla sus posibles errores."""

from configuracion.configuracion import clasificar_ccn
from metricas.bugs_smells import analizar_bugs_smells
from metricas.complejidad import ejecutar_lizard
from metricas.concurrencia import analizar_concurrencia
from metricas.mantenibilidad import analizar_mantenibilidad


def analizar_complejidad(proyecto, configuracion, logger, contexto):
    """Analiza y clasifica la complejidad de un proyecto de forma segura.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de rutas y umbrales.
        logger: Registrador de eventos de la ejecución.
        contexto: Datos auxiliares compartidos durante el análisis.

    Returns:
        Las métricas de complejidad o ``None`` si el análisis falla.
    """
    try:
        metricas, archivo_csv = ejecutar_lizard(
            proyecto=proyecto,
            carpeta_resultados=configuracion["carpeta_resultados"],
            logger=logger,
        )

        contexto.archivo_csv_lizard = archivo_csv

        nivel_cc, interpretacion_cc = clasificar_ccn(
            metricas.ccn_promedio,
            configuracion["umbrales_cc"],
        )
        metricas.nivel_cc = nivel_cc
        metricas.interpretacion_cc = interpretacion_cc
        return metricas
    except Exception as error:
        mensaje_error = f"Error en análisis de complejidad: {error}"
        logger.error(f"{proyecto.codigo}: {mensaje_error}")
        contexto.errores.append(mensaje_error)
        return None


def analizar_mi(proyecto, configuracion, logger, contexto):
    """Calcula el índice de mantenibilidad de un proyecto de forma segura.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de rutas y umbrales.
        logger: Registrador de eventos de la ejecución.
        contexto: Datos auxiliares compartidos durante el análisis.

    Returns:
        Las métricas de mantenibilidad o ``None`` si el análisis falla.
    """
    try:
        if contexto.archivo_csv_lizard is None:
            raise ValueError("No se generó el CSV de Lizard en esta ejecución")

        return analizar_mantenibilidad(
            proyecto=proyecto,
            archivo_csv_lizard=contexto.archivo_csv_lizard,
            carpeta_resultados=configuracion["carpeta_resultados"],
            umbrales_mi=configuracion["umbrales_mi"],
            logger=logger,
        )
    except Exception as error:
        mensaje_error = f"Error en análisis de mantenibilidad: {error}"
        logger.error(f"{proyecto.codigo}: {mensaje_error}")
        contexto.errores.append(mensaje_error)
        return None


def analizar_bugs_smells_seguro(proyecto, configuracion, logger, metricas_mi, contexto):
    """Analiza bugs y code smells, registrando los errores producidos.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de rutas y umbrales.
        logger: Registrador de eventos de la ejecución.
        metricas_mi: Métricas de mantenibilidad usadas para obtener NLOC.
        contexto: Datos auxiliares compartidos durante el análisis.

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
        logger.error(f"{proyecto.codigo}: {mensaje_error}")
        contexto.errores.append(mensaje_error)
        return None


def analizar_concurrencia_seguro(proyecto, configuracion, logger, contexto):
    """Analiza las métricas de concurrencia y registra posibles errores.

    Args:
        proyecto: Proyecto que se desea analizar.
        configuracion: Configuración de la rúbrica y sus umbrales.
        logger: Registrador de eventos de la ejecución.
        contexto: Datos auxiliares compartidos durante el análisis.

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
        logger.error(f"{proyecto.codigo}: {mensaje_error}")
        contexto.errores.append(mensaje_error)
        return None
