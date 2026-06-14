from datetime import datetime
from pathlib import Path
import logging

from util.archivos import crear_directorio


def configurar_logger(carpeta_logs):
    crear_directorio(carpeta_logs)

    fecha = datetime.now().strftime("%Y%m%d_%H%M%S")
    archivo_log = Path(carpeta_logs) / f"ejecucion_{fecha}.log"

    logger = logging.getLogger("evaluador_metricas")
    logger.setLevel(logging.INFO)
    logger.handlers.clear()

    formato = logging.Formatter("%(asctime)s | %(levelname)s | %(message)s")

    manejador_archivo = logging.FileHandler(archivo_log, encoding="utf-8")
    manejador_archivo.setFormatter(formato)

    manejador_consola = logging.StreamHandler()
    manejador_consola.setFormatter(formato)

    logger.addHandler(manejador_archivo)
    logger.addHandler(manejador_consola)

    return logger
