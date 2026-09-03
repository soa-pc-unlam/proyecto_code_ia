"""Carga y validación de la configuración y los proyectos del análisis."""

import json
from pathlib import Path

from util.modelos import Proyecto


def cargar_json(ruta_archivo):
    """Carga el contenido de un archivo JSON.

    Args:
        ruta_archivo: Ruta del archivo que se desea leer.

    Returns:
        El objeto Python obtenido al deserializar el JSON.

    Raises:
        FileNotFoundError: Si el archivo indicado no existe.
        json.JSONDecodeError: Si el contenido no es un JSON válido.
    """
    ruta = Path(ruta_archivo)

    if not ruta.exists():
        raise FileNotFoundError(f"No se encontró el archivo: {ruta_archivo}")

    with open(ruta, "r", encoding="utf-8") as archivo:
        return json.load(archivo)


def cargar_configuracion(ruta_archivo="configuracion.json"):
    """Carga la configuración y comprueba sus campos obligatorios.

    Args:
        ruta_archivo: Ruta del archivo de configuración.

    Returns:
        Diccionario con la configuración validada.

    Raises:
        ValueError: Si falta algún campo obligatorio.
    """
    configuracion = cargar_json(ruta_archivo)

    campos_obligatorios = [
        "archivo_excel",
        "carpeta_resultados",
        "carpeta_logs",
        "umbrales_cc",
        "umbrales_mi",
        "umbrales_issues",
        "umbrales_isi",
        "archivo_datos_entrada",
        "ponderacion_concurrencia",
        "umbrales_concurrencia",
    ]

    for campo in campos_obligatorios:
        if campo not in configuracion:
            raise ValueError(f"Falta el campo obligatorio en configuracion.json: {campo}")

    for nombre in ("umbrales_cc", "umbrales_mi", "umbrales_issues", "umbrales_isi", "umbrales_concurrencia"):
        validar_umbrales(configuracion[nombre], nombre)

    return configuracion


def validar_umbrales(umbrales, nombre):
    """Comprueba la forma y el orden básico de una lista de umbrales.

    Args:
        umbrales: Lista de intervalos que se desea validar.
        nombre: Nombre del grupo de umbrales usado en los mensajes de error.

    Raises:
        ValueError: Si la lista está vacía, un intervalo es inválido o sus
            límites están invertidos.
    """
    if not isinstance(umbrales, list) or not umbrales:
        raise ValueError(f"'{nombre}' debe ser una lista no vacía")
    for posicion, umbral in enumerate(umbrales, start=1):
        if not isinstance(umbral, dict) or "min" not in umbral or "max" not in umbral:
            raise ValueError(f"Umbral {posicion} inválido en '{nombre}'")
        if umbral["max"] is not None and umbral["min"] > umbral["max"]:
            raise ValueError(f"Rango invertido en el umbral {posicion} de '{nombre}'")

def cargar_proyectos(ruta_archivo="proyectos.json"):
    """Carga y valida los proyectos definidos en un archivo JSON.

    Args:
        ruta_archivo: Ruta del archivo de proyectos.

    Returns:
        Lista de instancias de ``Proyecto``.

    Raises:
        ValueError: Si la estructura o algún proyecto son inválidos.
    """
    datos = cargar_json(ruta_archivo)

    if not isinstance(datos, list):
        raise ValueError("El archivo proyectos.json debe contener una lista de proyectos.")

    proyectos = []
    codigos = set()
    campos = [
        "codigo",
        "nombre_proyecto",
        "ruta_codigo",
        "herramienta_ia",
        "modelo_ia",
        "lenguaje",
    ]

    for item in datos:
        for campo in campos:
            if campo not in item:
                raise ValueError(f"Falta el campo '{campo}' en un proyecto del archivo proyectos.json")

        if item["codigo"] in codigos:
            raise ValueError(f"Código de proyecto duplicado: {item['codigo']}")
        codigos.add(item["codigo"])

        proyectos.append(
            Proyecto(
                codigo=item["codigo"],
                nombre_proyecto=item["nombre_proyecto"],
                ruta_codigo=item["ruta_codigo"],
                herramienta_ia=item["herramienta_ia"],
                modelo_ia=item["modelo_ia"],
                lenguaje=item["lenguaje"],
            )
        )

    return proyectos


def clasificar_por_umbrales(valor, umbrales, nivel_sin_datos="Sin clasificar", interpretacion_sin_datos="No se encontró un criterio aplicable"):
    """Clasifica un valor mediante una colección de intervalos.

    Args:
        valor: Valor numérico que se desea clasificar.
        umbrales: Intervalos con nivel e interpretación asociados.
        nivel_sin_datos: Nivel usado cuando ningún intervalo coincide.
        interpretacion_sin_datos: Interpretación usada sin coincidencias.

    Returns:
        Una tupla con el nivel y la interpretación correspondientes.
    """
    for umbral in umbrales:
        minimo = umbral["min"]
        maximo = umbral["max"]

        if maximo is None and valor >= minimo:
            return umbral["nivel"], umbral["interpretacion"]

        if maximo is not None and minimo <= valor <= maximo:
            return umbral["nivel"], umbral["interpretacion"]

    return nivel_sin_datos, interpretacion_sin_datos


def clasificar_ccn(ccn_promedio, umbrales):
    """Clasifica una complejidad ciclomática promedio.

    Args:
        ccn_promedio: Complejidad ciclomática promedio.
        umbrales: Intervalos de clasificación configurados.

    Returns:
        Una tupla con el nivel y su interpretación.
    """
    if ccn_promedio <= 0:
        return "Sin funciones", "No se detectaron funciones analizables"

    return clasificar_por_umbrales(ccn_promedio, umbrales)


def clasificar_issues(issues_kloc, umbrales):
    """Clasifica la cantidad de incidencias por mil líneas de código.

    Args:
        issues_kloc: Cantidad de incidencias por KLOC.
        umbrales: Intervalos de clasificación configurados.

    Returns:
        Una tupla con el nivel y su interpretación.
    """
    return clasificar_por_umbrales(issues_kloc, umbrales)


def clasificar_isi(isi, umbrales):
    """Clasifica un índice de severidad de incidencias.

    Args:
        isi: Índice de severidad de incidencias.
        umbrales: Intervalos de clasificación configurados.

    Returns:
        Una tupla con el nivel y su interpretación.
    """
    return clasificar_por_umbrales(isi, umbrales)
