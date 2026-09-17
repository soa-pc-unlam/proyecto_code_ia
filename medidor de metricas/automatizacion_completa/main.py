"""Orquesta el análisis concurrente de métricas y la generación de reportes."""

import logging

from configuracion.configuracion import cargar_configuracion, cargar_proyectos
from constantes import definiciones
from reportes.excel import abrir_excel_entrada, crear_o_abrir_excel_salida, finalizar_libro
from util.archivos import crear_directorio
from util.logging_config import configurar_logger
from procesamiento.procesamiento import gestionar_procesamiento_proyectos
from util.recursos import limitar_cpu


def main():
    """Ejecuta concurrentemente el análisis de los proyectos configurados."""
    logger = logging.getLogger(__name__)
    libro_salida = None
    libro_entrada = None
    
    try:
        configuracion = cargar_configuracion(definiciones.CONFIGURACION_JSON)
        inicializar_directorios(configuracion)

        logger = configurar_logger(configuracion[definiciones.TEXTO_CAMPO_CARPETA_LOGS])
        logger.info("Inicio del análisis de métricas")

        limitar_cpu(definiciones.PORCENTAJE_MAX_CPU, logger)

        proyectos = cargar_proyectos(definiciones.DATOS_PROYECTOS_JSON)
        libro_salida = crear_o_abrir_excel_salida(configuracion[definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_SALIDA])

        libro_entrada = abrir_excel_entrada(configuracion[definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_ENTRADA])

        gestionar_procesamiento_proyectos(proyectos, configuracion, libro_salida,libro_entrada, logger)

        finalizar_libro(
            libro_salida,
            configuracion[definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_SALIDA],
            incluir_graficos=True,
        )
        informar_resultados_finales(logger, configuracion)

    except PermissionError:
        logger.error("No se pudo guardar el informe."
                     " Cierre el archivo Excel si está abierto.")
        
    except Exception as error:
        logger.error(f"Error en la ejecución: {error}")
    finally:
        if libro_salida is not None:
            libro_salida.close()

        if libro_entrada is not None:
            libro_entrada.close()


def inicializar_directorios(configuracion):
    """Crea los directorios de resultados y registros, incluidos sus padres.

    Args:
        configuracion (dict): Configuración con carpeta_resultados y carpeta_logs.

    Raises:
        OSError: Si no es posible crear alguno de los directorios.
    """
    crear_directorio(configuracion[definiciones.TEXTO_CAMPO_CARPETA_RESULTADOS])
    crear_directorio(configuracion[definiciones.TEXTO_CAMPO_CARPETA_LOGS])


def informar_resultados_finales(logger, configuracion):
    """Registra la finalización del proceso y la ubicación del reporte.

    Args:
        logger (logging.Logger): Registrador de eventos de la ejecución.
        configuracion (dict): Configuración que contiene archivo_excel_salida.
    """
    logger.info("")
    logger.info("=" * 24)
    logger.info("== Proceso finalizado ==")
    logger.info("=" * 24)
    logger.info(
        "Informe creado en el archivo Excel: "
        + str(configuracion[definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_SALIDA])
    )


if __name__ == "__main__":
    main()
