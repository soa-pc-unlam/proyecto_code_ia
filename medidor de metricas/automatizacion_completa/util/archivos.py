"""Utilidades para crear y validar rutas del sistema de archivos."""

from pathlib import Path


def crear_directorio(ruta):
    """Crea un directorio y todos sus directorios padre.

    Args:
        ruta: Ruta del directorio que se desea crear.
    """
    Path(ruta).mkdir(parents=True, exist_ok=True)


def validar_ruta_proyecto(ruta_codigo):
    """Valida que una ruta de proyecto exista y sea un directorio.

    Args:
        ruta_codigo: Ruta del código fuente del proyecto.

    Returns:
        La ruta validada como una instancia de ``Path``.

    Raises:
        FileNotFoundError: Si la ruta no existe.
        NotADirectoryError: Si la ruta no corresponde a un directorio.
    """
    ruta = Path(ruta_codigo)

    if not ruta.exists():
        raise FileNotFoundError(f"No existe la ruta del proyecto: {ruta_codigo}")

    if not ruta.is_dir():
        raise NotADirectoryError(f"La ruta no corresponde a una carpeta: {ruta_codigo}")

    return ruta
