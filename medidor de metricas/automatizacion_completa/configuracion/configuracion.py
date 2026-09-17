"""Carga y validación de la configuración y los proyectos del análisis."""

import json
import math
from numbers import Real
from pathlib import Path

from constantes import definiciones
from modelos.modelos import Proyecto


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


    for campo in definiciones.CAMPOS_OBLIGATORIOS_CONFIGURACION_JSON:
        if campo not in configuracion:
            raise ValueError(f"Falta el campo obligatorio en configuracion.json: {campo}")

    for nombre in (
        definiciones.TEXTO_CAMPO_UMBRALES_CC,
        definiciones.TEXTO_CAMPO_UMBRALES_MI,
        definiciones.TEXTO_CAMPO_UMBRALES_ISI,
        definiciones.TEXTO_CAMPO_UMBRALES_CONCURRENCIA,
    ):
        validar_umbrales(configuracion[nombre], nombre)        

    validar_coeficiente_penalizacion(
        configuracion["coeficiente_penalizacion_tokens"]
    )
    validar_umbrales(configuracion[definiciones.TEXTO_CAMPO_UMBRALES_CPU], definiciones.TEXTO_CAMPO_UMBRALES_CPU)
    validar_umbrales(configuracion[definiciones.TEXTO_CAMPO_UMBRALES_MEM], definiciones.TEXTO_CAMPO_UMBRALES_MEM)

    return configuracion


def validar_coeficiente_penalizacion(valor):
    """Valida el coeficiente usado para penalizar refinamientos."""
    if isinstance(valor, bool) or not isinstance(valor, Real):
        raise ValueError("'coeficiente_penalizacion_tokens' debe ser numérico")
    if not math.isfinite(valor) or valor < 0:
        raise ValueError(
            "'coeficiente_penalizacion_tokens' debe ser mayor o igual que cero"
        )


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
        if umbral[definiciones.TEXTO_CAMPO_MAX] is not None and umbral[definiciones.TEXTO_CAMPO_MIN] > umbral[definiciones.TEXTO_CAMPO_MAX]:
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


def clasificar_por_umbrales(valor, umbrales):
    """Retorna el umbral correspondiente al valor."""

    for umbral in umbrales:
        minimo = umbral[definiciones.TEXTO_CAMPO_MIN]
        maximo = umbral[definiciones.TEXTO_CAMPO_MAX]

        if (maximo is None) and (valor >= minimo) or (maximo is not None) and (minimo <= valor <= maximo):
            return umbral

    return None



def clasificar_mi(mi, umbrales):
    """Clasifica un índice de mantenibilidad según los umbrales.

    Args:
        mi: Índice de mantenibilidad.
        umbrales: Intervalos de clasificación configurados.

    Returns:
        Una tupla con el nivel y su interpretación.
    """
    resultado = clasificar_por_umbrales(mi, umbrales)
      
    return resultado[definiciones.TEXTO_CAMPO_NIVEL], resultado[definiciones.TEXTO_CAMPO_INTERPRETACION]
      

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

    resultado = clasificar_por_umbrales(ccn_promedio, umbrales)
    
    return resultado[definiciones.TEXTO_CAMPO_NIVEL], resultado[definiciones.TEXTO_CAMPO_INTERPRETACION]

def clasificar_uso_cpu(cpu_promedio, umbrales):
    resultado = clasificar_por_umbrales(cpu_promedio, umbrales)

    return resultado[definiciones.TEXTO_CAMPO_NIVEL], resultado[definiciones.TEXTO_CAMPO_INTERPRETACION]  

def clasificar_uso_mem(cpu_promedio, umbrales):
    resultado = clasificar_por_umbrales(cpu_promedio, umbrales)

    return resultado[definiciones.TEXTO_CAMPO_NIVEL], resultado[definiciones.TEXTO_CAMPO_INTERPRETACION]

def clasificar_isi(issues_kloc, umbrales):
    resultado = clasificar_por_umbrales(issues_kloc, umbrales)

    return resultado[definiciones.TEXTO_CAMPO_NIVEL], resultado[definiciones.TEXTO_CAMPO_INTERPRETACION], resultado[definiciones.TEXTO_CAMPO_OBSERVACION]


def clasificar_concurrencia(promedio, umbrales):
    resultado = clasificar_por_umbrales(promedio, umbrales)

    return resultado[definiciones.TEXTO_CAMPO_INTERPRETACION]

def clasificar_eficiencia_token(promedio_ponderada, umbrales):
    resultado = clasificar_por_umbrales(promedio_ponderada, umbrales)

    return resultado[definiciones.TEXTO_CAMPO_NIVEL], resultado[definiciones.TEXTO_CAMPO_INTERPRETACION]