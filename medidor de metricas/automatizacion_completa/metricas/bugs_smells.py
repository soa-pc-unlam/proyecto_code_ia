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
from util.configuracion import clasificar_issues, clasificar_isi
from util.lenguajes import normalizar_lenguaje
from util.modelos import MetricaBugsSmells


def analizar_bugs_smells(proyecto, carpeta_resultados, umbrales_issues, umbrales_isi, logger, loc_codigo=None):
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
    for candidato in candidatos:
        ejecutable = shutil.which(candidato)
        if ejecutable:
            return ejecutable

    raise RuntimeError(f"No se encontro ninguno de estos ejecutables en PATH: {', '.join(candidatos)}")


def ejecutar_pmd(ruta_proyecto, logger):
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
    resultado = subprocess.run(comando, capture_output=True, text=True, encoding="utf-8", errors="replace")

    if not archivo_csv.exists() or (resultado.returncode != 0 and archivo_csv.stat().st_size == 0):
        raise RuntimeError(f"PMD no genero el archivo CSV. {resultado.stderr}")

    issues = parsear_csv_pmd(archivo_csv)
    try:
        archivo_csv.unlink()
    except OSError:
        pass

    return issues


def parsear_csv_pmd(archivo_csv):
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
    for nombre in posibles_nombres:
        if nombre in row:
            return row[nombre]
    return ""


def clasificar_severidad_pmd(prioridad):
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
    ejecutable = buscar_ejecutable(["pylint"])
    comando = [ejecutable, str(ruta_proyecto), "--output-format=text"]

    logger.info(f"Ejecutando Pylint: {' '.join(comando)}")
    resultado = subprocess.run(comando, capture_output=True, text=True, encoding="utf-8", errors="replace")

    return parsear_salida_pylint(resultado.stdout + "\n" + resultado.stderr)


def parsear_salida_pylint(salida):
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
    if tipo in {"fatal", "error"}:
        return "Alta"
    if tipo in {"warning", "refactor"}:
        return "Media"
    return "Baja"


def clasificar_categoria_pylint(tipo):
    if tipo == "convention":
        return "Estilo"
    if tipo == "refactor":
        return "Diseno"
    if tipo in {"warning", "error", "fatal"}:
        return "Bug potencial"
    return "Documentacion"


def ejecutar_detekt(ruta_proyecto, logger):
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
    resultado = subprocess.run(comando, capture_output=True, text=True, encoding="utf-8", errors="replace")

    if not archivo_xml.exists() or archivo_xml.stat().st_size == 0:
        raise RuntimeError(f"Detekt no genero el archivo XML. {resultado.stderr}")

    issues = parsear_xml_detekt(archivo_xml)
    try:
        archivo_xml.unlink()
    except OSError:
        pass

    return issues


def parsear_xml_detekt(archivo_xml):
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
    if total_issues <= 0:
        return 0.0

    puntaje = (cantidad_alta * 5) + (cantidad_media * 2) + (cantidad_baja * 1)
    return round(puntaje / total_issues, 2)

def generar_resumen_bugs_smells_txt(proyecto, metricas, loc, issues, archivo_txt):
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
    nombre = os.path.basename(ruta)
    if nombre == ruta:
        nombre = PureWindowsPath(ruta).name
    return nombre or ruta
