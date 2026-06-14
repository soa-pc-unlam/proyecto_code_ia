from pathlib import Path


def crear_directorio(ruta):
    Path(ruta).mkdir(parents=True, exist_ok=True)


def validar_ruta_proyecto(ruta_codigo):
    ruta = Path(ruta_codigo)

    if not ruta.exists():
        raise FileNotFoundError(f"No existe la ruta del proyecto: {ruta_codigo}")

    if not ruta.is_dir():
        raise NotADirectoryError(f"La ruta no corresponde a una carpeta: {ruta_codigo}")

    return ruta
