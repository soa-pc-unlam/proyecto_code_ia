import sys
import subprocess
import re
from pathlib import Path
from collections import Counter


def contar_nloc_cpp(path_codigo):
    extensiones = [".ino", ".cpp", ".c", ".h", ".hpp"]
    nloc = 0
    dentro_comentario = False

    for archivo in Path(path_codigo).rglob("*"):
        if archivo.suffix.lower() not in extensiones:
            continue

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

                nloc += 1

    return nloc


def clasificar_severidad(severity):
    severity = severity.lower()

    if severity in ["error"]:
        return "Alta"
    elif severity in ["warning"]:
        return "Media"
    elif severity in ["style", "performance", "portability", "information"]:
        return "Baja"
    else:
        return "Baja"


def clasificar_categoria(severity):
    severity = severity.lower()

    if severity == "style":
        return "estilo"
    elif severity == "performance":
        return "rendimiento"
    elif severity == "portability":
        return "portabilidad"
    elif severity in ["warning", "error"]:
        return "bug potencial"
    elif severity == "information":
        return "documentación"
    else:
        return "otros"


def analizar_salida_cppcheck(texto):
    severidades = Counter()
    categorias = Counter()
    reglas = Counter()

    # Formato típico:
    # archivo.cpp:10:5: style: mensaje [idRegla]
    patron = re.compile(r":\d+:\d+:\s+(\w+):.*\[(.*?)\]")

    total_issues = 0
    issues_criticos = 0

    for linea in texto.splitlines():
        match = patron.search(linea)

        if match:
            severity = match.group(1)
            regla = match.group(2)

            total_issues += 1

            severidad = clasificar_severidad(severity)
            categoria = clasificar_categoria(severity)

            severidades[severidad] += 1
            categorias[categoria] += 1
            reglas[regla] += 1

            if severity.lower() in ["error", "warning"]:
                issues_criticos += 1

    return total_issues, severidades, categorias, reglas, issues_criticos


def main():

    if len(sys.argv) != 3:
        print("Uso:")
        print("python ejecutar_cppcheck.py <path_codigo> <archivo_salida>")
        sys.exit(1)

    path_codigo = sys.argv[1]
    archivo_salida = sys.argv[2]

    script_dir = Path(__file__).resolve().parent
    freertos_cfg = script_dir / "freertos.cfg"

    comando = [
        "cppcheck",
        "--enable=warning,style,performance,portability",
        "--std=c++11",
        "--force",
        f"--library={freertos_cfg}",
        "--suppress=missingInclude",
        "--suppress=missingIncludeSystem",
        "--suppress=unusedFunction",
        path_codigo
    ]

    print("Ejecutando:")
    print(" ".join(comando))

    try:
        resultado = subprocess.run(
            comando,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True
        )

        salida_cppcheck = resultado.stdout

        nloc = contar_nloc_cpp(path_codigo)

        total_issues, severidades, categorias, reglas, issues_criticos = analizar_salida_cppcheck(
            salida_cppcheck
        )

        issues_kloc = (total_issues / nloc) * 1000 if nloc > 0 else 0

        with open(archivo_salida, "w", encoding="utf-8") as f:
            f.write("REPORTE DE ANÁLISIS CPPCHECK - ESP32 / C++\n")
            f.write("=" * 50 + "\n\n")

            f.write(f"Proyecto analizado: {path_codigo}\n")
            f.write(f"NLOC aproximadas: {nloc}\n")
            f.write(f"Total de issues: {total_issues}\n")
            f.write(f"Issues/KLOC: {issues_kloc:.2f}\n")
            f.write(f"Issues críticos: {issues_criticos}\n\n")

            f.write("SEVERIDAD\n")
            f.write("-" * 50 + "\n")
            for sev, cant in severidades.most_common():
                f.write(f"{sev}: {cant}\n")

            f.write("\nCATEGORÍAS\n")
            f.write("-" * 50 + "\n")
            for cat, cant in categorias.most_common():
                f.write(f"{cat}: {cant}\n")

            f.write("\nTOP REGLAS VIOLADAS\n")
            f.write("-" * 50 + "\n")
            for regla, cant in reglas.most_common(10):
                f.write(f"{regla}: {cant}\n")

            f.write("\nOBSERVACIÓN METODOLÓGICA\n")
            f.write("-" * 50 + "\n")
            f.write(
                "Cppcheck se utilizó para identificar problemas de calidad estática "
                "del código, incluyendo posibles defectos, problemas de estilo, "
                "rendimiento y portabilidad. La complejidad ciclomática y el tamaño "
                "estructural del software pueden complementarse con Lizard.\n"
            )

            f.write("\nSALIDA COMPLETA DE CPPCHECK\n")
            f.write("-" * 50 + "\n")
            f.write(salida_cppcheck)

        print("Análisis finalizado.")
        print(f"Código de salida: {resultado.returncode}")
        print(f"Reporte guardado en: {Path(archivo_salida).resolve()}")

    except FileNotFoundError:
        print("Error: cppcheck no está instalado o no está en el PATH.")
    except Exception as e:
        print(f"Error: {e}")


if __name__ == "__main__":
    main()