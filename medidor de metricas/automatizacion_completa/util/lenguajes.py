def normalizar_lenguaje(lenguaje):
    lenguaje = lenguaje.strip().lower()

    if lenguaje in ["python", "py"]:
        return "python"

    if lenguaje in ["java", "jav"]:
        return "java"

    if lenguaje in ["kotlin", "kt"]:
        return "kotlin"

    raise ValueError(f"Lenguaje no soportado: {lenguaje}")
