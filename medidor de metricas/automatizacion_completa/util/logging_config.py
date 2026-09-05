"""Configuración del registro de eventos de la aplicación."""

from datetime import datetime
from pathlib import Path
import logging
from constantes import definiciones as constantes
from util.archivos import crear_directorio


def configurar_logger(carpeta_logs):
    """Configura el logger para escribir en consola y en un archivo fechado.

    Args:
        carpeta_logs: Directorio donde se almacenará el archivo de registro.

    Returns:
        El logger configurado para la ejecución actual.
    """
    crear_directorio(carpeta_logs)

    fecha = datetime.now().strftime("%Y%m%d_%H%M%S")
    archivo_log = Path(carpeta_logs) / f"ejecucion_{fecha}.log"

    logger = logging.getLogger("evaluador_metricas")
    logger.setLevel(constantes.MODO_LOGGING)
    logger.handlers.clear()

    formato = logging.Formatter("%(asctime)s | %(levelname)-5s | %(message)s",
              datefmt="%Y-%m-%d %H:%M:%S")

    manejador_archivo = logging.FileHandler(archivo_log, encoding="utf-8")
    manejador_archivo.setFormatter(formato)

    manejador_consola = logging.StreamHandler()
    manejador_consola.setFormatter(formato)

    logger.addHandler(manejador_archivo)
    logger.addHandler(manejador_consola)

    return logger
