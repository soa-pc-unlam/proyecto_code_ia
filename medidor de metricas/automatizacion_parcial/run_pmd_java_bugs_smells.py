# run_pmd_java_bugs_smells.py
import argparse
import csv
import subprocess
import tempfile
from pathlib import Path
from collections import Counter, defaultdict


def contar_loc_java(path_proyecto):
    loc = 0
    dentro_comentario = False

    for archivo in Path(path_proyecto).rglob("*.java"):
        try:
            with open(archivo, "r", encoding="utf-8", errors="ignore") as f:
                for linea in f:
                    s = linea.strip()

                    if not s:
                        continue

                    if dentro_comentario:
                        if "*/" in s:
                            dentro_comentario = False
                        continue

                    if s.startswith("/*"):
                        dentro_comentario = True
                        continue

                    if s.startswith("//"):
                        continue

                    loc += 1
        except Exception:
            pass

    return loc


def clasificar_severidad(priority):
    try:
        p = int(priority)
    except:
        return "Desconocida"

    if p == 1:
        return "Crítica"
    elif p == 2:
        return "Alta"
    elif p == 3:
        return "Media"
    else:
        return "Baja"


def obtener_campo(row, posibles_nombres):
    for nombre in posibles_nombres:
        if nombre in row:
            return row[nombre]
    return ""


def ejecutar_pmd(path_proyecto, archivo_salida, ruleset):
    with tempfile.NamedTemporaryFile(delete=False, suffix=".csv") as tmp:
        archivo_csv = tmp.name

    comando = [
        "pmd.bat",
        "check",
        "-d", path_proyecto,
        "-R", ruleset,
        "-f", "csv",
        "-r", archivo_csv
    ]

    resultado = subprocess.run(
        comando,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )

    # PMD puede devolver código distinto de 0 si encuentra violaciones.
    # Por eso no cortamos automáticamente si el CSV fue generado.
    if not Path(archivo_csv).exists():
        raise RuntimeError("PMD no generó el archivo CSV.")

    loc = contar_loc_java(path_proyecto)

    total_issues = 0
    severidades = Counter()
    categorias = Counter()
    reglas = Counter()
    issues_criticos = 0

    with open(archivo_csv, "r", encoding="utf-8", errors="ignore") as f:
        reader = csv.DictReader(f)

        for row in reader:
            total_issues += 1

            prioridad = obtener_campo(row, ["Priority", "priority"])
            regla = obtener_campo(row, ["Rule", "rule"])
            categoria = obtener_campo(row, ["Rule set", "RuleSet", "ruleset", "Category"])

            severidad = clasificar_severidad(prioridad)

            severidades[severidad] += 1
            reglas[regla] += 1

            if categoria:
                categorias[categoria] += 1
            else:
                categorias["Sin categoría"] += 1

            try:
                if int(prioridad) <= 2:
                    issues_criticos += 1
            except:
                pass

    issues_kloc = (total_issues / loc) * 1000 if loc > 0 else 0

    with open(archivo_salida, "w", encoding="utf-8") as f:
        f.write("REPORTE DE ANÁLISIS PMD - JAVA\n")
        f.write("=" * 40 + "\n\n")

        f.write(f"Proyecto analizado: {path_proyecto}\n")
        f.write(f"LOC Java aproximadas: {loc}\n")
        f.write(f"Total de issues: {total_issues}\n")
        f.write(f"Issues/KLOC: {issues_kloc:.2f}\n")
        f.write(f"Issues críticos: {issues_criticos}\n\n")

        f.write("SEVERIDAD\n")
        f.write("-" * 40 + "\n")
        for sev, cant in severidades.most_common():
            f.write(f"{sev}: {cant}\n")

        f.write("\nCATEGORÍAS\n")
        f.write("-" * 40 + "\n")
        for cat, cant in categorias.most_common():
            f.write(f"{cat}: {cant}\n")

        f.write("\nTOP REGLAS VIOLADAS\n")
        f.write("-" * 40 + "\n")
        for regla, cant in reglas.most_common(10):
            f.write(f"{regla}: {cant}\n")

        f.write("\nOBSERVACIÓN METODOLÓGICA\n")
        f.write("-" * 40 + "\n")
        f.write(
            "La complejidad y el tamaño del código se analizan con Lizard. "
            "PMD se utiliza para identificar issues de calidad, severidad, "
            "categorías de reglas y reglas más violadas.\n"
        )

        if resultado.stderr.strip():
            f.write("\nMENSAJES DE PMD\n")
            f.write("-" * 40 + "\n")
            f.write(resultado.stderr)

    print(f"Reporte generado correctamente: {archivo_salida}")


def main():
    parser = argparse.ArgumentParser(description="Ejecutar PMD sobre un proyecto Java y generar reporte TXT.")
    parser.add_argument("path_proyecto", help="Path del proyecto Java a analizar")
    parser.add_argument("archivo_salida", help="Nombre del archivo TXT de salida")
    parser.add_argument(
        "--ruleset",
        default="rulesets/java/quickstart.xml",
        help="Ruleset de PMD a utilizar"
    )

    args = parser.parse_args()

    ejecutar_pmd(args.path_proyecto, args.archivo_salida, args.ruleset)


if __name__ == "__main__":
    main()