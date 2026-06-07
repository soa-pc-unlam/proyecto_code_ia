import argparse
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from collections import Counter, defaultdict


def contar_lineas_kotlin(path_proyecto: Path) -> int:
    total = 0

    for archivo in path_proyecto.rglob("*.kt"):
        if "build" in archivo.parts:
            continue

        try:
            with archivo.open("r", encoding="utf-8", errors="ignore") as f:
                total += sum(1 for _ in f)
        except Exception:
            pass

    return total


def clasificar_categoria(rule_id: str) -> str:
    rule = rule_id.lower()

    if any(x in rule for x in ["style", "naming", "spacing", "format"]):
        return "Estilo"

    if any(x in rule for x in ["complex", "long", "large", "nested", "many", "coupling"]):
        return "Diseño / mantenibilidad"

    if any(x in rule for x in ["empty", "unused", "null", "exception", "unsafe", "unreachable"]):
        return "Bug potencial"

    if any(x in rule for x in ["comment", "documentation", "kdoc"]):
        return "Documentación"

    return "Otros"


def clasificar_severidad(rule_id: str) -> str:
    rule = rule_id.lower()

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
        "unsafe"
    ]

    media = [
        "magicnumber",
        "unused",
        "empty",
        "returncount",
        "cyclomaticcomplexmethod",
        "longparameterlist"
    ]

    if any(x.lower() in rule for x in alta):
        return "Alta"

    if any(x.lower() in rule for x in media):
        return "Media"

    return "Baja"


def ejecutar_detekt(path_proyecto: Path, archivo_xml: Path):
    comando = [
        "detekt-cli.bat",
        "--input", str(path_proyecto),
        "--report", f"xml:{archivo_xml}"
    ]

    resultado = subprocess.run(
        comando,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )

    # Detekt puede devolver código distinto de 0 si encuentra issues.
    # Por eso no cortamos automáticamente.
    if not archivo_xml.exists():
        print("Error ejecutando Detekt.")
        print(resultado.stdout)
        print(resultado.stderr)
        raise RuntimeError("No se generó el archivo XML de Detekt.")


def analizar_xml_detekt(archivo_xml: Path):
    tree = ET.parse(archivo_xml)
    root = tree.getroot()

    issues = []

    for file_node in root.findall("file"):
        nombre_archivo = file_node.attrib.get("name", "")

        for error in file_node.findall("error"):
            rule_id = error.attrib.get("source", "Regla desconocida")
            mensaje = error.attrib.get("message", "")
            linea = error.attrib.get("line", "")

            issues.append({
                "archivo": nombre_archivo,
                "linea": linea,
                "regla": rule_id,
                "mensaje": mensaje,
                "severidad": clasificar_severidad(rule_id),
                "categoria": clasificar_categoria(rule_id)
            })

    return issues


def generar_reporte(path_proyecto: Path, archivo_salida: Path, issues: list, loc: int):
    total_issues = len(issues)
    kloc = loc / 1000 if loc > 0 else 0
    issues_kloc = total_issues / kloc if kloc > 0 else 0

    contador_severidad = Counter(i["severidad"] for i in issues)
    contador_categoria = Counter(i["categoria"] for i in issues)
    contador_reglas = Counter(i["regla"] for i in issues)

    issues_criticos = contador_severidad["Alta"]

    with archivo_salida.open("w", encoding="utf-8") as f:
        f.write("REPORTE DE ANÁLISIS DETEKT - KOTLIN\n")
        f.write("=" * 60 + "\n\n")

        f.write(f"Proyecto analizado: {path_proyecto}\n")
        f.write(f"Líneas Kotlin analizadas: {loc}\n")
        f.write(f"Total de issues: {total_issues}\n")
        f.write(f"Issues/KLOC: {issues_kloc:.2f}\n")
        f.write(f"Issues críticos: {issues_criticos}\n\n")

        f.write("SEVERIDAD\n")
        f.write("-" * 60 + "\n")
        f.write(f"Baja:  {contador_severidad['Baja']}\n")
        f.write(f"Media: {contador_severidad['Media']}\n")
        f.write(f"Alta:  {contador_severidad['Alta']}\n\n")

        f.write("CATEGORÍAS\n")
        f.write("-" * 60 + "\n")
        for categoria, cantidad in contador_categoria.most_common():
            f.write(f"{categoria}: {cantidad}\n")

        f.write("\nTOP REGLAS VIOLADAS\n")
        f.write("-" * 60 + "\n")
        for regla, cantidad in contador_reglas.most_common(10):
            f.write(f"{regla}: {cantidad}\n")

        f.write("\nDETALLE DE ISSUES\n")
        f.write("-" * 60 + "\n")
        for issue in issues:
            f.write(
                f"[{issue['severidad']}] "
                f"[{issue['categoria']}] "
                f"{issue['regla']} - "
                f"{issue['archivo']}:{issue['linea']}\n"
            )
            f.write(f"  {issue['mensaje']}\n\n")


def main():
    parser = argparse.ArgumentParser(
        description="Ejecuta Detekt sobre un proyecto Kotlin y genera un reporte TXT."
    )

    parser.add_argument(
        "path_proyecto",
        help="Ruta del proyecto o carpeta con código Kotlin"
    )

    parser.add_argument(
        "archivo_salida",
        help="Nombre del archivo TXT de salida"
    )

    args = parser.parse_args()

    path_proyecto = Path(args.path_proyecto).resolve()
    archivo_salida = Path(args.archivo_salida).resolve()
    archivo_xml = archivo_salida.with_suffix(".detekt.xml")

    if not path_proyecto.exists():
        raise FileNotFoundError(f"No existe la ruta: {path_proyecto}")

    print("Contando líneas Kotlin...")
    loc = contar_lineas_kotlin(path_proyecto)

    print("Ejecutando Detekt...")
    ejecutar_detekt(path_proyecto, archivo_xml)

    print("Analizando resultados...")
    issues = analizar_xml_detekt(archivo_xml)

    print("Generando reporte TXT...")
    generar_reporte(path_proyecto, archivo_salida, issues, loc)

    print(f"Reporte generado: {archivo_salida}")


if __name__ == "__main__":
    main()