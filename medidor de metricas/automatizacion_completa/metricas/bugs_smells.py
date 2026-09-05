"""Detección y clasificación de bugs y code smells por lenguaje."""

import csv
import os
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path, PureWindowsPath

from util.archivos import crear_directorio, validar_ruta_proyecto
from configuracion.configuracion import clasificar_issues, clasificar_isi
from util.lenguajes import normalizar_lenguaje
from modelos.modelos import MetricaBugsSmells


def analizar_bugs_smells(proyecto, carpeta_resultados, umbrales_issues, umbrales_isi, logger, loc_codigo=None):
    """Ejecuta el analizador adecuado y genera las métricas de incidencias.

    Args:
        proyecto: Proyecto que debe analizarse.
        carpeta_resultados: Directorio para los reportes generados.
        umbrales_issues: Umbrales de incidencias por KLOC.
        umbrales_isi: Umbrales del índice de severidad.
        logger: Logger utilizado para registrar la ejecución.
        loc_codigo: Líneas de código conocidas, si están disponibles.

    Returns:
        Las métricas agregadas de bugs y code smells.
    """
    ruta_proyecto = validar_ruta_proyecto(proyecto.ruta_codigo)
    crear_directorio(carpeta_resultados)

    lenguaje = normalizar_lenguaje(proyecto.lenguaje)
    archivo_txt = Path(carpeta_resultados) / f"{proyecto.codigo}_resumen_bugs_smells.txt"

    if lenguaje == "java":
        analizador = "PMD"
        issues = ejecutar_pmd(ruta_proyecto, logger)
    elif lenguaje == "python":
        analizador = "Pylint"
        issues = ejecutar_pylint(ruta_proyecto, logger)
    elif lenguaje == "kotlin":
        analizador = "Detekt"
        issues = ejecutar_detekt(ruta_proyecto, logger)
    else:
        raise ValueError(f"No hay analizador de bugs/smells para el lenguaje: {proyecto.lenguaje}")

    loc = loc_codigo if loc_codigo is not None else 0

    metricas = calcular_metricas_bugs_smells(analizador, issues, loc, umbrales_issues, umbrales_isi)
    generar_resumen_bugs_smells_txt(proyecto, metricas, loc, issues, archivo_txt)

    return metricas


def buscar_ejecutable(candidatos):
    """Busca el primer ejecutable disponible entre varios candidatos.

    Args:
        candidatos: Nombres de ejecutables aceptados.

    Returns:
        La ruta o el nombre del ejecutable encontrado.

    Raises:
        FileNotFoundError: Si ningún candidato está disponible.
    """
    for candidato in candidatos:
        ejecutable = shutil.which(candidato)
        if ejecutable:
            return ejecutable

    raise RuntimeError(f"No se encontro ninguno de estos ejecutables en PATH: {', '.join(candidatos)}")


def ejecutar_pmd(ruta_proyecto, logger):
    """Ejecuta PMD sobre un proyecto y devuelve sus incidencias.

    Args:
        ruta_proyecto: Ruta del código fuente.
        logger: Logger utilizado para registrar la ejecución.

    Returns:
        Lista de incidencias normalizadas.
    """
    ejecutable = buscar_ejecutable(["pmd", "pmd.bat", "pmd.cmd"])

    with tempfile.NamedTemporaryFile(delete=False, suffix=".csv") as temporal:
        archivo_csv = Path(temporal.name)

    comando = [
        ejecutable,
        "check",
        "-d",
        str(ruta_proyecto),
        "-R",
        "rulesets/java/quickstart.xml",
        "-f",
        "csv",
        "-r",
        str(archivo_csv),
    ]

    logger.info(f"Ejecutando PMD: {' '.join(comando)}")
    try:
        resultado = subprocess.run(
            comando,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=300,
        )
        if not archivo_csv.exists() or (
            resultado.returncode != 0 and archivo_csv.stat().st_size == 0
        ):
            raise RuntimeError(f"PMD no generó el archivo CSV. {resultado.stderr}")
        return parsear_csv_pmd(archivo_csv)
    finally:
        archivo_csv.unlink(missing_ok=True)


def parsear_csv_pmd(archivo_csv):
    """Convierte un reporte CSV de PMD en incidencias normalizadas.

    Args:
        archivo_csv: Ruta del reporte de PMD.

    Returns:
        Lista de incidencias detectadas.
    """
    issues = []

    with open(archivo_csv, "r", encoding="utf-8", errors="ignore", newline="") as archivo:
        reader = csv.DictReader(archivo)

        for row in reader:
            prioridad = obtener_campo(row, ["Priority", "priority"])
            regla = obtener_campo(row, ["Rule", "rule"]) or "Regla desconocida"
            categoria = obtener_campo(row, ["Rule set", "RuleSet", "ruleset", "Category"]) or "Sin categoria"

            issues.append(
                {
                    "archivo": obtener_campo(row, ["File", "file"]),
                    "linea": obtener_campo(row, ["Line", "line", "Begin Line"]),
                    "columna": obtener_campo(row, ["Column", "column", "Begin Column"]),
                    "regla": regla,
                    "mensaje": obtener_campo(row, ["Description", "description", "Problem"]),
                    "severidad": clasificar_severidad_pmd(prioridad),
                    "categoria": categoria,
                }
            )

    return issues


def obtener_campo(row, posibles_nombres):
    """Obtiene el primer campo disponible de una fila.

    Args:
        row: Fila representada como diccionario.
        posibles_nombres: Nombres alternativos del campo.

    Returns:
        El valor encontrado o una cadena vacía.
    """
    for nombre in posibles_nombres:
        if nombre in row:
            return row[nombre]
    return ""


def clasificar_severidad_pmd(prioridad):
    """Traduce una prioridad de PMD a una severidad normalizada.

    Args:
        prioridad: Prioridad informada por PMD.

    Returns:
        Severidad alta, media o baja.
    """
    try:
        prioridad = int(prioridad)
    except (TypeError, ValueError):
        return "Baja"

    if prioridad in {1, 2}:
        return "Alta"
    if prioridad == 3:
        return "Media"
    return "Baja"


def ejecutar_pylint(ruta_proyecto, logger):
    """Ejecuta Pylint y devuelve sus mensajes normalizados.

    Args:
        ruta_proyecto: Ruta del código fuente.
        logger: Logger utilizado para registrar la ejecución.

    Returns:
        Lista de incidencias detectadas.
    """
    ejecutable = buscar_ejecutable(["pylint"])
    comando = [ejecutable, str(ruta_proyecto), "--output-format=text"]

    logger.info(f"Ejecutando Pylint: {' '.join(comando)}")
    resultado = subprocess.run(
        comando,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=300,
    )
    if resultado.returncode >= 32:
        raise RuntimeError(f"Pylint no pudo ejecutar el análisis: {resultado.stderr}")
    return parsear_salida_pylint(resultado.stdout + "\n" + resultado.stderr)


def parsear_salida_pylint(salida):
    """Convierte la salida textual de Pylint en incidencias.

    Args:
        salida: Texto producido por Pylint.

    Returns:
        Lista de incidencias normalizadas.
    """
    patron = re.compile(r"^(.*?):(\d+):(\d+): ([CRWEF])(\d+): (.*?) \((.*?)\)")
    tipos = {
        "C": "convention",
        "R": "refactor",
        "W": "warning",
        "E": "error",
        "F": "fatal",
    }

    issues = []
    for linea in salida.splitlines():
        match = patron.match(linea)
        if not match:
            continue

        archivo, linea_num, columna, codigo_tipo, codigo, mensaje, regla = match.groups()
        tipo = tipos.get(codigo_tipo, "unknown")
        issues.append(
            {
                "archivo": archivo,
                "linea": linea_num,
                "columna": columna,
                "tipo": tipo,
                "codigo": codigo_tipo + codigo,
                "mensaje": mensaje,
                "regla": regla,
                "severidad": clasificar_severidad_pylint(tipo),
                "categoria": clasificar_categoria_pylint(tipo),
            }
        )

    return issues


def clasificar_severidad_pylint(tipo):
    """Asigna una severidad a una categoría de Pylint.

    Args:
        tipo: Categoría textual de Pylint.

    Returns:
        Severidad alta, media o baja.
    """
    if tipo in {"fatal", "error"}:
        return "Alta"
    if tipo in {"warning", "refactor"}:
        return "Media"
    return "Baja"


def clasificar_categoria_pylint(tipo):
    """Asigna una categoría funcional a un tipo de Pylint.

    Args:
        tipo: Categoría textual de Pylint.

    Returns:
        Categoría normalizada de la incidencia.
    """
    if tipo == "convention":
        return "Estilo"
    if tipo == "refactor":
        return "Diseno"
    if tipo in {"warning", "error", "fatal"}:
        return "Bug potencial"
    return "Documentacion"


def ejecutar_detekt(ruta_proyecto, logger):
    """Ejecuta Detekt y devuelve sus incidencias normalizadas.

    Args:
        ruta_proyecto: Ruta del código fuente.
        logger: Logger utilizado para registrar la ejecución.

    Returns:
        Lista de incidencias detectadas.

    Raises:
        RuntimeError: Si Detekt no genera un reporte XML válido.
    """
    ejecutable = buscar_ejecutable(["detekt-cli", "detekt-cli.bat", "detekt"])

    with tempfile.NamedTemporaryFile(delete=False, suffix=".detekt.xml") as temporal:
        archivo_xml = Path(temporal.name)

    comando = [
        ejecutable,
        "--input",
        str(ruta_proyecto),
        "--report",
        f"xml:{archivo_xml}",
    ]

    logger.info(f"Ejecutando Detekt: {' '.join(comando)}")
    try:
        resultado = subprocess.run(
            comando,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=300,
        )
        if not archivo_xml.exists() or archivo_xml.stat().st_size == 0:
            raise RuntimeError(f"Detekt no generó el archivo XML. {resultado.stderr}")
        return parsear_xml_detekt(archivo_xml)
    finally:
        archivo_xml.unlink(missing_ok=True)


def parsear_xml_detekt(archivo_xml):
    """Convierte un reporte XML de Detekt en incidencias.

    Args:
        archivo_xml: Ruta del reporte XML.

    Returns:
        Lista de incidencias normalizadas.
    """
    tree = ET.parse(archivo_xml)
    root = tree.getroot()
    issues = []

    for file_node in root.findall("file"):
        nombre_archivo = file_node.attrib.get("name", "")

        for error in file_node.findall("error"):
            regla = error.attrib.get("source", "Regla desconocida")
            issues.append(
                {
                    "archivo": nombre_archivo,
                    "linea": error.attrib.get("line", ""),
                    "columna": error.attrib.get("column", ""),
                    "regla": regla,
                    "mensaje": error.attrib.get("message", ""),
                    "severidad": clasificar_severidad_detekt(regla),
                    "categoria": clasificar_categoria_detekt(regla),
                }
            )

    return issues


def clasificar_severidad_detekt(regla):
    """Asigna una severidad a una regla de Detekt.

    Args:
        regla: Identificador de la regla.

    Returns:
        Severidad alta, media o baja.
    """
    regla = regla.lower()
    alta = [
        "complexmethod",
        "largeclass",
        "longmethod",
        "toomanyfunctions",
        "nestedblockdepth",
        "cognitivecomplexmethod",
        "throwscount",
        "swallowedexception",
        "toogenericexceptioncaught",
        "unsafe",
    ]
    media = [
        "magicnumber",
        "unused",
        "empty",
        "returncount",
        "cyclomaticcomplexmethod",
        "longparameterlist",
    ]

    if any(valor in regla for valor in alta):
        return "Alta"
    if any(valor in regla for valor in media):
        return "Media"
    return "Baja"


def clasificar_categoria_detekt(regla):
    """Asigna una categoría funcional a una regla de Detekt.

    Args:
        regla: Identificador de la regla.

    Returns:
        Categoría normalizada de la incidencia.
    """
    regla = regla.lower()

    if any(valor in regla for valor in ["style", "naming", "spacing", "format"]):
        return "Estilo"
    if any(valor in regla for valor in ["complex", "long", "large", "nested", "many", "coupling"]):
        return "Diseno / mantenibilidad"
    if any(valor in regla for valor in ["empty", "unused", "null", "exception", "unsafe", "unreachable"]):
        return "Bug potencial"
    if any(valor in regla for valor in ["comment", "documentation", "kdoc"]):
        return "Documentacion"
    return "Otros"


def calcular_metricas_bugs_smells(analizador, issues, loc, umbrales_issues, umbrales_isi):
    """Agrega incidencias en métricas comparables de calidad.

    Args:
        analizador: Nombre del analizador utilizado.
        issues: Incidencias detectadas.
        loc: Cantidad de líneas de código analizadas.
        umbrales_issues: Umbrales de incidencias por KLOC.
        umbrales_isi: Umbrales del índice de severidad.

    Returns:
        Las métricas agregadas de bugs y code smells.
    """
    total_issues = len(issues)
    issues_kloc = round((total_issues / loc) * 1000, 2) if loc > 0 else 0.0
    
    severidades = Counter(issue["severidad"] for issue in issues)
    reglas = Counter(issue["regla"] for issue in issues)

    cantidad_alta = severidades["Alta"]
    cantidad_media = severidades["Media"]
    cantidad_baja = severidades["Baja"]
    
    isi = calcular_isi(cantidad_alta, cantidad_media, cantidad_baja, total_issues)
    nivel_isi, interpretacion_isi = clasificar_isi(isi, umbrales_isi)

    if cantidad_alta > 0:
        observacion = "Contiene issues altos"
    elif cantidad_media > 0:
        observacion= "Contiene issues medios"
    else:
        observacion = "Solo issues bajos"
    
    return MetricaBugsSmells(
        analizador=analizador,
        total_issues=total_issues,
        issues_kloc =issues_kloc,
        isi=isi,
        nivel_isi=nivel_isi,
        interpretacion_isi=interpretacion_isi,
        observacion=observacion,
        cantidad_baja=cantidad_baja,
        cantidad_media=cantidad_media,
        cantidad_alta=cantidad_alta,
        top_reglas_violadas=reglas.most_common(10),
    )



def calcular_isi(cantidad_alta, cantidad_media, cantidad_baja, total_issues):
    """Calcula el índice de severidad de incidencias.

    Args:
        cantidad_alta: Cantidad de incidencias de severidad alta.
        cantidad_media: Cantidad de incidencias de severidad media.
        cantidad_baja: Cantidad de incidencias de severidad baja.
        total_issues: Cantidad total de incidencias.

    Returns:
        El índice ponderado, o cero cuando no hay incidencias.
    """
    if total_issues <= 0:
        return 0.0

    puntaje = (cantidad_alta * 5) + (cantidad_media * 2) + (cantidad_baja * 1)
    return round(puntaje / total_issues, 2)

def generar_resumen_bugs_smells_txt(proyecto, metricas, loc, issues, archivo_txt):
    """Escribe el reporte detallado de bugs y code smells.

    Args:
        proyecto: Proyecto al que corresponde el reporte.
        metricas: Métricas agregadas de incidencias.
        loc: Cantidad de líneas analizadas.
        issues: Detalle de incidencias detectadas.
        archivo_txt: Ruta del archivo de salida.
    """
    severidades = Counter(issue["severidad"] for issue in issues)
    categorias = Counter(issue["categoria"] for issue in issues)

    with open(archivo_txt, "w", encoding="utf-8") as archivo:
        archivo.write(f"REPORTE DE BUGS Y SMELLS - {metricas.analizador}\n")
        archivo.write("=" * 60 + "\n\n")

        archivo.write(f"Codigo: {proyecto.codigo}\n")
        archivo.write(f"Proyecto: {proyecto.nombre_proyecto}\n")
        archivo.write(f"Herramienta IA: {proyecto.herramienta_ia}\n")
        archivo.write(f"Modelo IA: {proyecto.modelo_ia}\n")
        archivo.write(f"Lenguaje: {proyecto.lenguaje}\n")
        archivo.write(f"LOC analizadas: {loc}\n\n")

        archivo.write("METRICAS COMPARABLES\n")
        archivo.write("-" * 60 + "\n")
        archivo.write(f"Analizador: {metricas.analizador}\n")
        archivo.write(f"Total de issues: {metricas.total_issues}\n")
        archivo.write(f"Issues/KLOC: {metricas.issues_kloc}\n")
        archivo.write(f"ISI: {metricas.isi}\n")
        archivo.write(f"Nivel de ISI: {metricas.nivel_isi}\n")
        archivo.write(f"Interpretacion de ISI: {metricas.interpretacion_isi}\n")
        archivo.write(f"Observacion: {metricas.observacion}\n\n")

        archivo.write("SEVERIDAD\n")
        archivo.write("-" * 60 + "\n")
        archivo.write(f"Alta: {severidades['Alta']}\n")
        archivo.write(f"Media: {severidades['Media']}\n")
        archivo.write(f"Baja: {severidades['Baja']}\n\n")

        archivo.write("CATEGORIAS\n")
        archivo.write("-" * 60 + "\n")
        for categoria, cantidad in categorias.most_common():
            archivo.write(f"{categoria}: {cantidad}\n")

        archivo.write("\nTOP REGLAS VIOLADAS\n")
        archivo.write("-" * 60 + "\n")
        for regla, cantidad in metricas.top_reglas_violadas:
            archivo.write(f"{regla}: {cantidad}\n")

        archivo.write("\nDETALLE DE ISSUES\n")
        archivo.write("-" * 60 + "\n")
        if not issues:
            archivo.write("No se detectaron issues.\n")
        else:
            for issue in issues:
                archivo.write(
                    f"[{issue['severidad']}] "
                    f"[{issue['categoria']}] "
                    f"{normalizar_ruta_salida(issue.get('archivo', ''))}:"
                    f"{issue.get('linea', '')}:{issue.get('columna', '')} "
                    f"{issue.get('regla', '')} - {issue.get('mensaje', '')}\n"
                )


def normalizar_ruta_salida(ruta):
    """Reduce una ruta a un nombre de archivo portable.

    Args:
        ruta: Ruta producida por una herramienta de análisis.

    Returns:
        El nombre del archivo o la ruta original si no puede reducirse.
    """
    nombre = os.path.basename(ruta)
    if nombre == ruta:
        nombre = PureWindowsPath(ruta).name
    return nombre or ruta
