"""Orquesta el análisis concurrente de métricas y la generación de reportes."""

import logging

from configuracion.configuracion import cargar_configuracion, cargar_proyectos
from constantes import definiciones
from reportes.excel import abrir_excel_entrada, crear_o_abrir_excel_salida, finalizar_libro
from util.archivos import crear_directorio
from util.logging_config import configurar_logger
from procesamiento.procesamiento import gestionar_procesamiento_proyectos
from util.recursos import limitar_cpu


SEPARADOR_FIN_PROCESO = "========================"


def ejecutar_analisis(tipo_proyecto):
    """Ejecuta el análisis y cierra los libros, registrando los errores.

    Args:
        ruta_configuracion: Ruta del JSON de configuración base.
        ruta_proyectos: Ruta del JSON de proyectos que se procesará.
        ajustes_configuracion: Valores que reemplazan la configuración cargada
            en memoria, sin modificar el archivo original.
        porcentaje_cpu: Fracción de CPUs disponibles que se permite utilizar.
        incluir_graficos: Indica si se generan gráficos en el reporte.
    """
    logger = logging.getLogger(__name__)
    libro_salida = None
    libro_entrada = None
    
    try:

        if tipo_proyecto == definiciones.TIPO_PROYECTO_PC:
            ruta_configuracion = definiciones.CONFIGURACION_JSON_PC
            ruta_proyectos = definiciones.DATOS_PC_PROYECTOS_JSON
            porcentaje_cpu = 0.5
            incluir_graficos = True
        if tipo_proyecto == definiciones.TIPO_PROYECTO_ESP32_ANDROID:
            ruta_configuracion = definiciones.CONFIGURACION_JSON_ESP32_ANDROID
            ruta_proyectos = definiciones.DATOS_ESP32_ANDROID_PROYECTOS_JSON
            porcentaje_cpu = 0.5
            incluir_graficos = True
            
        configuracion = cargar_configuracion(ruta_configuracion)
        inicializar_directorios(configuracion)

        logger = configurar_logger(configuracion[definiciones.TEXTO_CAMPO_CARPETA_LOGS])
        logger.info("Inicio del análisis de métricas")

        limitar_cpu(porcentaje_cpu, logger)

        proyectos = cargar_proyectos(ruta_proyectos)
        libro_salida = crear_o_abrir_excel_salida(configuracion[definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_SALIDA])

        libro_entrada = abrir_excel_entrada(configuracion[definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_ENTRADA])

        gestionar_procesamiento_proyectos(proyectos, configuracion, libro_salida,libro_entrada, logger)

        finalizar_libro(
            libro_salida,
            configuracion[definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_SALIDA],
            incluir_graficos=incluir_graficos,
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
    logger.info(SEPARADOR_FIN_PROCESO)
    logger.info("== Proceso finalizado ==")
    logger.info(SEPARADOR_FIN_PROCESO)
    logger.info(
        "Informe creado en el archivo Excel: "
        + str(configuracion[definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_SALIDA])
    )

