from pathlib import Path
from datetime import datetime
import logging


def crear_directorio(ruta):
    Path(ruta).mkdir(parents=True, exist_ok=True)


def configurar_logger(carpeta_logs):
    crear_directorio(carpeta_logs)

    fecha = datetime.now().strftime("%Y%m%d_%H%M%S")
    archivo_log = Path(carpeta_logs) / f"ejecucion_{fecha}.log"

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
        handlers=[
            logging.FileHandler(archivo_log, encoding="utf-8"),
            logging.StreamHandler()
        ]
    )

    return logging.getLogger(__name__)


def normalizar_lenguaje(lenguaje):
    lenguaje = lenguaje.strip().lower()

    if lenguaje in ["python", "py"]:
        return "python"

    if lenguaje in ["java", "jav"]:
        return "java"

    raise ValueError(f"Lenguaje no soportado: {lenguaje}")


def validar_ruta_proyecto(ruta_codigo):
    ruta = Path(ruta_codigo)

    if not ruta.exists():
        raise FileNotFoundError(f"No existe la ruta del proyecto: {ruta_codigo}")

    if not ruta.is_dir():
        raise NotADirectoryError(f"La ruta no corresponde a una carpeta: {ruta_codigo}")

    return ruta
