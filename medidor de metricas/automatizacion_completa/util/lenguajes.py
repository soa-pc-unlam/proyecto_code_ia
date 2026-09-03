"""Normalización de los lenguajes de programación admitidos."""


def normalizar_lenguaje(lenguaje):
    """Convierte el nombre de un lenguaje a su identificador canónico.

    Args:
        lenguaje: Nombre o abreviatura del lenguaje.

    Returns:
        El identificador normalizado del lenguaje.

    Raises:
        ValueError: Si el lenguaje no está soportado.
    """
    lenguaje = lenguaje.strip().lower()

    if lenguaje in ["python", "py"]:
        return "python"

    if lenguaje in ["java", "jav"]:
        return "java"

    if lenguaje in ["kotlin", "kt"]:
        return "kotlin"

    raise ValueError(f"Lenguaje no soportado: {lenguaje}")
