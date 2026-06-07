#!/usr/bin/env python3
"""
Calcula un Índice de Mantenibilidad estimado a partir de un CSV generado por Lizard.

Uso:
    python calculate_mi.py lizard-report.csv
    python calculate_mi.py lizard-report.csv mi-report.txt

El CSV esperado es el generado con:
    lizard . --languages java --csv > lizard-report.csv

Fórmula utilizada:
    MI = (171 - 5.2*ln(tokens) - 0.23*avgCCN - 16.2*ln(nloc)) * 100 / 171

Nota: se usa la columna tokens de Lizard como aproximación del volumen de Halstead.
"""

import csv
import math
import os
import sys
from collections import defaultdict
from dataclasses import dataclass


@dataclass
class FileMetrics:
    nloc: int = 0
    functions: int = 0
    ccn_sum: float = 0.0
    tokens: int = 0

    @property
    def avg_ccn(self) -> float:
        return self.ccn_sum / self.functions if self.functions else 0.0

    @property
    def mi(self) -> float:
        if self.nloc <= 0 or self.tokens <= 0:
            return 100.0
        raw_mi = 171 - 5.2 * math.log(self.tokens) - 0.23 * self.avg_ccn - 16.2 * math.log(self.nloc)
        mi_0_100 = raw_mi * 100 / 171
        return max(0.0, min(100.0, mi_0_100))


def parse_lizard_csv(csv_path: str) -> dict[str, FileMetrics]:
    metrics_by_file: dict[str, FileMetrics] = defaultdict(FileMetrics)

    with open(csv_path, newline="", encoding="utf-8-sig") as f:
        reader = csv.reader(f)
        for row in reader:
            if not row:
                continue

            # Lizard puede generar CSV sin encabezado. Si hay encabezado, lo salteamos.
            if row[0].strip().lower() in {"nloc", "nloc"}:
                continue

            try:
                nloc = int(row[0])
                ccn = float(row[1])
                tokens = int(row[2])
                file_path = row[6]
            except (ValueError, IndexError):
                # Ignora líneas que no tengan el formato esperado.
                continue

            file_name = os.path.basename(file_path)
            fm = metrics_by_file[file_name]
            fm.nloc += nloc
            fm.functions += 1
            fm.ccn_sum += ccn
            fm.tokens += tokens

    return dict(metrics_by_file)


def format_avg_ccn(value: float) -> str:
    rounded = round(value, 2)
    if rounded.is_integer():
        return f"{rounded:.1f}"
    return f"{rounded:.2f}"


def build_report(metrics_by_file: dict[str, FileMetrics]) -> str:
    files = sorted(metrics_by_file.items(), key=lambda item: item[1].mi)

    total_nloc = sum(m.nloc for _, m in files)
    total_functions = sum(m.functions for _, m in files)
    total_tokens = sum(m.tokens for _, m in files)
    avg_mi = sum(m.mi for _, m in files) / len(files) if files else 0.0

    lines: list[str] = []
    lines.append(f"{'archivo':<45} {'nloc':>6} {'fns':>5} {'avgCCN':>7} {'tokens':>7} {'MI':>6}")
    lines.append("-" * 78)

    for file_name, m in files:
        lines.append(
            f"{file_name:<45} "
            f"{m.nloc:>6} "
            f"{m.functions:>5} "
            f"{format_avg_ccn(m.avg_ccn):>7} "
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
    lines.append("")
    lines.append("Bandas Microsoft:  0-9 = inmantenible  |  10-19 = moderado  |  >=20 = bueno")
    return "\n".join(lines)


def main() -> int:
    if len(sys.argv) not in {2, 3}:
        print("Uso: python calculate_mi.py lizard-report.csv [mi-report.txt]")
        return 1

    input_csv = sys.argv[1]
    output_txt = sys.argv[2] if len(sys.argv) == 3 else "mi-report.txt"

    if not os.path.isfile(input_csv):
        print(f"Error: no existe el archivo de entrada: {input_csv}")
        return 1

    metrics_by_file = parse_lizard_csv(input_csv)
    if not metrics_by_file:
        print("Error: no se encontraron métricas válidas en el CSV de Lizard.")
        return 1

    report = build_report(metrics_by_file)

    with open(output_txt, "w", encoding="utf-8") as f:
        f.write(report + "\n")

    print(f"Reporte generado: {output_txt}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
