import argparse
import subprocess
import re
from collections import Counter, defaultdict
from pathlib import Path


def contar_lineas_python(path_proyecto):
    total = 0
    for archivo in Path(path_proyecto).rglob("*.py"):
        try:
            with open(archivo, "r", encoding="utf-8", errors="ignore") as f:
                total += sum(1 for line in f if line.strip())
        except Exception:
            pass
    return total


def clasificar_severidad(tipo):
    if tipo in ["fatal", "error"]:
        return "Alta"
    elif tipo in ["warning", "refactor"]:
        return "Media"
    else:
        return "Baja"


def clasificar_categoria(tipo):
    if tipo == "convention":
        return "estilo"
    elif tipo == "refactor":
        return "diseño"
    elif tipo in ["warning", "error", "fatal"]:
        return "bug potencial"
    else:
        return "documentación"


def analizar_salida_pylint(salida):
    issues = []

    patron = re.compile(
        r"^(.*?):(\d+):(\d+): ([CRWEF])(\d+): (.*?) \((.*?)\)"
    )

    tipos = {
        "C": "convention",
        "R": "refactor",
        "W": "warning",
        "E": "error",
        "F": "fatal"
    }

    for linea in salida.splitlines():
        match = patron.match(linea)
        if match:
            archivo, linea_num, columna, codigo_tipo, codigo, mensaje, regla = match.groups()
            tipo = tipos.get(codigo_tipo, "unknown")

            issues.append({
                "archivo": archivo,
                "linea": linea_num,
                "columna": columna,
                "tipo": tipo,
                "codigo": codigo_tipo + codigo,
                "mensaje": mensaje,
                "regla": regla,
                "severidad": clasificar_severidad(tipo),
                "categoria": clasificar_categoria(tipo)
            })

    return issues


def ejecutar_pylint(path_proyecto):
    comando = [
        "pylint",
        path_proyecto,
        "--output-format=text"
    ]

    resultado = subprocess.run(
        comando,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="ignore"
    )

    return resultado.stdout + "\n" + resultado.stderr


def generar_reporte(path_proyecto, archivo_salida):
    salida_pylint = ejecutar_pylint(path_proyecto)
    issues = analizar_salida_pylint(salida_pylint)

    loc = contar_lineas_python(path_proyecto)
    kloc = loc / 1000 if loc > 0 else 0
    issues_kloc = len(issues) / kloc if kloc > 0 else 0

    severidades = Counter(issue["severidad"] for issue in issues)
    categorias = Counter(issue["categoria"] for issue in issues)
    reglas = Counter(issue["regla"] for issue in issues)

    issues_criticos = sum(
        1 for issue in issues if issue["severidad"] == "Alta"
    )

    with open(archivo_salida, "w", encoding="utf-8") as f:
        f.write("REPORTE DE ANÁLISIS CON PYLINT\n")
        f.write("=" * 50 + "\n\n")

        f.write(f"Proyecto analizado: {path_proyecto}\n")
        f.write(f"Líneas Python analizadas: {loc}\n\n")

        f.write("MÉTRICAS COMPARABLES\n")
        f.write("-" * 50 + "\n")
        f.write(f"Total de issues: {len(issues)}\n")
        f.write(f"Issues/KLOC: {issues_kloc:.2f}\n")
        f.write(f"Issues críticos: {issues_criticos}\n\n")

        f.write("SEVERIDAD\n")
        f.write("-" * 50 + "\n")
        f.write(f"Baja: {severidades.get('Baja', 0)}\n")
        f.write(f"Media: {severidades.get('Media', 0)}\n")
        f.write(f"Alta: {severidades.get('Alta', 0)}\n\n")

        f.write("CATEGORÍAS\n")
        f.write("-" * 50 + "\n")
        for categoria, cantidad in categorias.most_common():
            f.write(f"{categoria}: {cantidad}\n")
        f.write("\n")

        f.write("TOP REGLAS VIOLADAS\n")
        f.write("-" * 50 + "\n")
        for regla, cantidad in reglas.most_common(10):
            f.write(f"{regla}: {cantidad}\n")
        f.write("\n")

        f.write("DETALLE DE ISSUES\n")
        f.write("-" * 50 + "\n")
        for issue in issues:
            f.write(
                f"[{issue['severidad']}] "
                f"[{issue['categoria']}] "
                f"{issue['archivo']}:{issue['linea']}:{issue['columna']} "
                f"{issue['codigo']} {issue['regla']} - {issue['mensaje']}\n"
            )

        f.write("\n\nSALIDA COMPLETA DE PYLINT\n")
        f.write("=" * 50 + "\n")
        f.write(salida_pylint)


def main():
    parser = argparse.ArgumentParser(
        description="Ejecuta Pylint y genera un reporte TXT con métricas comparables."
    )

    parser.add_argument(
        "path_proyecto",
        help="Ruta del proyecto Python a analizar"
    )

    parser.add_argument(
        "archivo_salida",
        help="Nombre del archivo TXT de salida"
    )

    args = parser.parse_args()

    generar_reporte(args.path_proyecto, args.archivo_salida)


if __name__ == "__main__":
    main()