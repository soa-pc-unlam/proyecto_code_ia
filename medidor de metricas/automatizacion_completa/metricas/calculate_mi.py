#!/usr/bin/env python3
"""
Calcula un Índice de Mantenibilidad estimado a partir de un CSV generado por Lizard.

Este script queda disponible para ejecución independiente. El programa principal
usa `metricas.mantenibilidad` para trabajar los resultados como diccionario/objeto.
"""

import sys
from pathlib import Path

from metricas.mantenibilidad import parse_lizard_csv_mi


def build_report(metrics_by_file):
    """Construye un reporte de mantenibilidad en texto plano.

    Args:
        metrics_by_file: Mapeo entre nombres de archivo y sus métricas.

    Returns:
        El reporte tabular listo para escribirse en un archivo.
    """
    files = sorted(metrics_by_file.items(), key=lambda item: item[1].mi)
    total_nloc = sum(m.nloc for _, m in files)
    total_functions = sum(m.functions for _, m in files)
    total_tokens = sum(m.tokens for _, m in files)
    avg_mi = sum(m.mi for _, m in files) / len(files) if files else 0.0

    lines = []
    lines.append(f"{'archivo':<45} {'nloc':>6} {'fns':>5} {'avgCCN':>7} {'tokens':>7} {'MI':>6}")
    lines.append("-" * 78)

    for file_name, m in files:
        lines.append(
            f"{file_name:<45} "
            f"{m.nloc:>6} "
            f"{m.functions:>5} "
            f"{m.avg_ccn:>7.2f} "
            f"{m.tokens:>7} "
            f"{m.mi:>6.1f}"
        )

    lines.append("-" * 78)
    lines.append(
        f"{'TOTAL/PROMEDIO':<45} "
        f"{total_nloc:>6} "
        f"{total_functions:>5} "
        f"{'':>7} "
        f"{total_tokens:>7} "
        f"{avg_mi:>6.1f}"
    )
    return "\n".join(lines)


def main():
    """Procesa los argumentos de línea de comandos y genera el reporte.

    Returns:
        Código de salida: cero si finaliza correctamente y uno ante errores.
    """
    if len(sys.argv) not in {2, 3}:
        print("Uso: python calculate_mi.py lizard-report.csv [mi-report.txt]")
        return 1

    input_csv = Path(sys.argv[1])
    output_txt = Path(sys.argv[2]) if len(sys.argv) == 3 else Path("mi-report.txt")

    if not input_csv.is_file():
        print(f"Error: no existe el archivo de entrada: {input_csv}")
        return 1

    metrics_by_file = parse_lizard_csv_mi(input_csv)
    if not metrics_by_file:
        print("Error: no se encontraron métricas válidas en el CSV de Lizard.")
        return 1

    output_txt.write_text(build_report(metrics_by_file) + "\n", encoding="utf-8")
    print(f"Reporte generado: {output_txt}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
