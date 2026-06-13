import json
from pathlib import Path
from models import Proyecto


def cargar_json(ruta_archivo):
    ruta = Path(ruta_archivo)

    if not ruta.exists():
        raise FileNotFoundError(f"No se encontró el archivo: {ruta_archivo}")

    with open(ruta, "r", encoding="utf-8") as archivo:
        return json.load(archivo)


def cargar_configuracion(ruta_archivo="configuracion.json"):
    configuracion = cargar_json(ruta_archivo)

    campos_obligatorios = [
        "archivo_excel",
        "carpeta_resultados",
        "carpeta_logs",
        "umbrales_cc"
    ]

    for campo in campos_obligatorios:
        if campo not in configuracion:
            raise ValueError(f"Falta el campo obligatorio en configuracion.json: {campo}")

    return configuracion


def cargar_proyectos(ruta_archivo="proyectos.json"):
    datos = cargar_json(ruta_archivo)

    if not isinstance(datos, list):
        raise ValueError("El archivo proyectos.json debe contener una lista de proyectos.")

    proyectos = []

    campos = [
        "codigo",
        "nombre_proyecto",
        "ruta_codigo",
        "herramienta_ia",
        "modelo_ia",
        "lenguaje"
    ]

    for item in datos:
        for campo in campos:
            if campo not in item:
                raise ValueError(f"Falta el campo '{campo}' en un proyecto del archivo proyectos.json")

        proyectos.append(
            Proyecto(
                codigo=item["codigo"],
                nombre_proyecto=item["nombre_proyecto"],
                ruta_codigo=item["ruta_codigo"],
                herramienta_ia=item["herramienta_ia"],
                modelo_ia=item["modelo_ia"],
                lenguaje=item["lenguaje"]
            )
        )

    return proyectos


def clasificar_ccn(ccn_promedio, umbrales):
    if ccn_promedio <= 0:
        return "Sin funciones", "No se detectaron funciones analizables"

    for umbral in umbrales:
        minimo = umbral["min"]
        maximo = umbral["max"]

        if maximo is None and ccn_promedio >= minimo:
            return umbral["nivel"], umbral["interpretacion"]

        if maximo is not None and minimo <= ccn_promedio <= maximo:
            return umbral["nivel"], umbral["interpretacion"]

    return "Sin clasificar", "No se encontró un criterio aplicable"
