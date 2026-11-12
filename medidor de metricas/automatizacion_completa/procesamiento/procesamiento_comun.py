from reportes.comparaciones import GeneradorComparaciones
from reportes.gestor_libros import GestorLibrosExcel
"""Orquesta el análisis concurrente de métricas y la generación de reportes."""

import logging

from configuracion.configuracion import cargar_configuracion
from configuracion.lector_proyectos_excel import LectorProyectosExcel
from constantes import definiciones
from util.archivos import crear_directorio
from util.logging_config import configurar_logger
from procesamiento.procesamiento import gestionar_procesamiento_proyectos
from util.recursos import limitar_cpu


SEPARADOR_FIN_PROCESO = "========================"


class PerfilAnalisis:
    """Selecciona rutas y el formato de salida del perfil solicitado (Strategy)."""

    def __init__(self, tipo_proyecto):
        self.tipo_proyecto = tipo_proyecto
        rutas = {
            definiciones.TIPO_PROYECTO_PC: definiciones.CONFIGURACION_JSON_PC,
            definiciones.TIPO_PROYECTO_ESP32_ANDROID: (
                definiciones.CONFIGURACION_JSON_ESP32_ANDROID
            ),
        }
        if tipo_proyecto not in rutas:
            raise ValueError(f"Tipo de proyecto no admitido: {tipo_proyecto}")
        self.ruta_configuracion = rutas[tipo_proyecto]
        self.hojas_reporte = definiciones.PERFILES_REPORTE[tipo_proyecto]
        self.porcentaje_cpu = definiciones.PORCENTAJE_MAX_CPU


class AplicacionMetricas:
    """Fachada de alto nivel: adquisición, procesamiento y reporte."""

    def __init__(self, tipo_proyecto):
        self.tipo_proyecto = tipo_proyecto
        self.logger = logging.getLogger(__name__)
        self.libro_salida = None
        self.libro_entrada = None

    def ejecutar(self):
        try:
            perfil = PerfilAnalisis(self.tipo_proyecto)
            configuracion = cargar_configuracion(perfil.ruta_configuracion)
            inicializar_directorios(configuracion)
            self.logger = configurar_logger(configuracion[definiciones.TEXTO_CAMPO_CARPETA_LOGS])
            self.logger.info("Inicio del análisis de métricas")
            limitar_cpu(perfil.porcentaje_cpu, self.logger)
          
            self.libro_entrada = GestorLibrosExcel().abrir_entrada(
                configuracion[definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_ENTRADA]
            )
            proyectos = LectorProyectosExcel().cargar(self.libro_entrada)
            self.libro_salida = GestorLibrosExcel().crear_o_abrir_salida(
                configuracion[definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_SALIDA],
                hojas_reporte=perfil.hojas_reporte,
            )
            gestionar_procesamiento_proyectos(
                proyectos, configuracion, self.libro_salida, self.libro_entrada,
                self.logger,
            )
            # El perfil explícito conserva la comparación Manual/Vibecoding
            # y sus cuatro gráficos originales. La configuración por columnas
            # sigue disponible para llamadas históricas directas a finalizar_libro.
            opciones_graficos = {
                "incluir_graficos": True,
                "tipo_proyecto": self.tipo_proyecto,
            }
            GestorLibrosExcel().finalizar(
                self.libro_salida,
                configuracion[definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_SALIDA],
                **opciones_graficos,
            )
            informar_resultados_finales(self.logger, configuracion)
        except PermissionError:
            self.logger.error(
                "No se pudo guardar el informe. Cierre el archivo Excel si está abierto."
            )
        except Exception as error:
            self.logger.error(f"Error en la ejecución: {error}")
        finally:
            if self.libro_salida is not None:
                self.libro_salida.close()
            if self.libro_entrada is not None:
                self.libro_entrada.close()


def ejecutar_analisis(tipo_proyecto):
    """Punto de entrada estable que delega a la aplicación orientada a objetos."""
    return AplicacionMetricas(tipo_proyecto).ejecutar()


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

