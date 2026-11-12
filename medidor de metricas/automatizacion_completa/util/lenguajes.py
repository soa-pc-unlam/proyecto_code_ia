"""Normalización de los lenguajes de programación admitidos."""



from util.exception import LenguajeNoSoportadoError
from constantes.definiciones import LENGUAJE_WIRING


def normalizar_lenguaje(lenguaje):
    """Convierte el nombre de un lenguaje a su identificador canónico.

    Args:
        lenguaje: Nombre o abreviatura del lenguaje.

    Returns:
        El identificador normalizado del lenguaje.

    Raises:
        LenguajeNoSoportadoError: Si el lenguaje no está soportado.
    """
    lenguaje = lenguaje.strip().lower()

    if lenguaje in ["python", "py"]:
        return "python"

    if lenguaje in ["java", "jav"]:
        return "java"

    if lenguaje in ["kotlin", "kt"]:
        return "kotlin"

    if lenguaje == LENGUAJE_WIRING:
        return LENGUAJE_WIRING

    raise LenguajeNoSoportadoError(f"Lenguaje no soportado: {lenguaje}")
