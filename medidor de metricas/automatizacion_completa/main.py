"""Orquesta el análisis concurrente de métricas y la generación de reportes."""

from configuracion.configuracion import cargar_configuracion, cargar_proyectos
from constantes import definiciones
from reportes.excel import crear_o_abrir_excel, finalizar_libro
from util.archivos import crear_directorio
from util.logging_config import configurar_logger
from procesamiento.procesamiento import gestionar_procesamiento_proyectos
from util.recursos import limitar_cpu


def main():
    """Ejecuta concurrentemente el análisis de los proyectos configurados."""
    configuracion = cargar_configuracion(definiciones.CONFIGURACION_JSON)
    inicializar_directorios(configuracion)

    logger = configurar_logger(configuracion["carpeta_logs"])
    logger.info("Inicio del análisis de métricas")

    limitar_cpu(definiciones.PORCENTAJE_MAX_CPU, logger)

    proyectos = cargar_proyectos(definiciones.DATOS_PROYECTOS_JSON)
    libro = crear_o_abrir_excel(configuracion["archivo_excel"])

    gestionar_procesamiento_proyectos(proyectos, configuracion, libro, logger)

    finalizar_libro(
        libro,
        configuracion["archivo_excel"],
        incluir_graficos=True,
    )

    informar_resultados_finales(logger, configuracion)


def inicializar_directorios(configuracion):
    """Crea los directorios requeridos por la aplicación."""
    crear_directorio(configuracion["carpeta_resultados"])
    crear_directorio(configuracion["carpeta_logs"])


def informar_resultados_finales(logger, configuracion):
    """Informa la finalización del proceso y la ubicación del reporte."""
    logger.info("")
    logger.info("=" * 24)
    logger.info("== Proceso finalizado ==")
    logger.info("=" * 24)
    logger.info(
        "Informe creado en el archivo Excel: "
        + str(configuracion["archivo_excel"])
    )


if __name__ == "__main__":
    main()
