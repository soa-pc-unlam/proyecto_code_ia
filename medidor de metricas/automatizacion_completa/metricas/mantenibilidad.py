"""Cálculo y reporte del índice de mantenibilidad."""

import csv
import math
import os
from collections import defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path, PureWindowsPath

from configuracion.configuracion import clasificar_por_umbrales
from modelos.modelos import MetricaMantenibilidad


@dataclass
class FileMetricsMI:
    """Acumula las métricas necesarias para calcular el MI de un archivo.

    Attributes:
        nloc: Cantidad de líneas de código del archivo.
        functions: Cantidad de funciones encontradas.
        ccn_sum: Suma de la complejidad ciclomática de las funciones.
        tokens: Cantidad de tokens de código del archivo.
    """
    nloc: int = 0
    functions: int = 0
    ccn_sum: float = 0.0
    tokens: int = 0

    @property
    def avg_ccn(self) -> float:
        """Devuelve la complejidad ciclomática promedio del archivo.

        Returns:
            La complejidad promedio o cero cuando no hay funciones.
        """
        return self.ccn_sum / self.functions if self.functions else 0.0

    @property
    def mi(self) -> float:
        """Calcula el índice de mantenibilidad normalizado entre 0 y 100.

        Returns:
            El índice de mantenibilidad del archivo.

        Notes:
            Usa la fórmula ``(171 - 5.2*ln(tokens) - 0.23*avgCCN -
            16.2*ln(nloc)) * 100 / 171``.
        """
        if self.nloc <= 0 or self.tokens <= 0:
            return 100.0

        raw_mi = (
            171
            - 5.2 * math.log(self.tokens)
            - 0.23 * self.avg_ccn
            - 16.2 * math.log(self.nloc)
        )
        mi_0_100 = raw_mi * 100 / 171
        return max(0.0, min(100.0, mi_0_100))


def parse_lizard_csv_mi(csv_path):
    """Procesa un CSV de Lizard y agrega sus métricas por archivo.

    Args:
        csv_path: Ruta del CSV generado por Lizard.

    Returns:
        Un diccionario que asocia cada archivo con sus métricas.
    """
    metrics_by_file = defaultdict(FileMetricsMI)

    with open(csv_path, newline="", encoding="utf-8", errors="replace") as archivo:
        reader = csv.reader(archivo)

        for row in reader:
            if not row:
                continue

            if row[0].strip().lower() == "nloc":
                continue

            try:
                nloc = int(row[0])
                ccn = float(row[1])
                tokens = int(row[2])
                file_path = row[6]
            except (ValueError, IndexError):
                continue

            file_name = os.path.basename(file_path)
            if file_name == file_path:
                file_name = PureWindowsPath(file_path).name

            fm = metrics_by_file[file_name]
            fm.nloc += nloc
            fm.functions += 1
            fm.ccn_sum += ccn
            fm.tokens += tokens

    return dict(metrics_by_file)


def clasificar_mi(mi, umbrales):
    """Clasifica un índice de mantenibilidad según los umbrales.

    Args:
        mi: Índice de mantenibilidad.
        umbrales: Intervalos de clasificación configurados.

    Returns:
        Una tupla con el nivel y su interpretación.
    """
    return clasificar_por_umbrales(mi, umbrales)


def calcular_metricas_mi(metrics_by_file, umbrales_mi):
    """Agrega las métricas por archivo en una métrica de proyecto.

    Args:
        metrics_by_file: Métricas de mantenibilidad por archivo.
        umbrales_mi: Intervalos de clasificación del MI.

    Returns:
        Las métricas de mantenibilidad agregadas.
    """
    files = sorted(metrics_by_file.items(), key=lambda item: item[1].mi)

    total_nloc = sum(m.nloc for _, m in files)
    total_functions = sum(m.functions for _, m in files)
    total_tokens = sum(m.tokens for _, m in files)
    avg_mi = round(sum(m.mi for _, m in files) / len(files), 2) if files else 0.0

    nivel_mi, interpretacion_mi = clasificar_mi(avg_mi, umbrales_mi)

    archivos = {
        file_name: {
            "nloc": m.nloc,
            "funciones": m.functions,
            "avg_ccn": round(m.avg_ccn, 2),
            "tokens": m.tokens,
            "mi": round(m.mi, 2),
        }
        for file_name, m in files
    }

    return MetricaMantenibilidad(
        nloc_mi=total_nloc,
        cantidad_funciones_mi=total_functions,
        tokens_codigo=total_tokens,
        mi=avg_mi,
        nivel_mi=nivel_mi,
        interpretacion_mi=interpretacion_mi,
        archivos=archivos,
    )


def generar_resumen_mi_txt(proyecto, metricas_mi, archivo_txt):
    """Escribe un resumen de mantenibilidad en texto plano.

    Args:
        proyecto: Proyecto al que pertenecen las métricas.
        metricas_mi: Métricas de mantenibilidad calculadas.
        archivo_txt: Ruta del archivo de salida.
    """
    with open(archivo_txt, "w", encoding="utf-8") as archivo:
        archivo.write("RESUMEN DE MANTENIBILIDAD\n")
        archivo.write("=" * 45 + "\n\n")

        archivo.write(f"Código: {proyecto.codigo}\n")
        archivo.write(f"Proyecto: {proyecto.nombre_proyecto}\n")
        archivo.write(f"Herramienta IA: {proyecto.herramienta_ia}\n")
        archivo.write(f"Modelo IA: {proyecto.modelo_ia}\n")
        archivo.write(f"Lenguaje: {proyecto.lenguaje}\n\n")

        archivo.write(f"NLOC MI: {metricas_mi.nloc_mi}\n")
        archivo.write(f"Cantidad de funciones MI: {metricas_mi.cantidad_funciones_mi}\n")
        archivo.write(f"Tokens código: {metricas_mi.tokens_codigo}\n")
        archivo.write(f"MI: {metricas_mi.mi}\n")
        archivo.write(f"Nivel de MI: {metricas_mi.nivel_mi}\n")
        archivo.write(f"Interpretación MI: {metricas_mi.interpretacion_mi}\n\n")

        archivo.write("Detalle por archivo\n")
        archivo.write("-" * 78 + "\n")
        archivo.write(f"{'archivo':<45} {'nloc':>6} {'fns':>5} {'tokens':>8} {'MI':>7}\n")
        archivo.write("-" * 78 + "\n")

        for file_name, datos in metricas_mi.archivos.items():
            archivo.write(
                f"{file_name:<45} "
                f"{datos['nloc']:>6} "
                f"{datos['funciones']:>5} "
                f"{datos['tokens']:>8} "
                f"{datos['mi']:>7}\n"
            )


def analizar_mantenibilidad(proyecto, archivo_csv_lizard, carpeta_resultados, umbrales_mi, logger):
    """Calcula y reporta la mantenibilidad de un proyecto.

    Args:
        proyecto: Proyecto que debe analizarse.
        archivo_csv_lizard: CSV de métricas generado por Lizard.
        carpeta_resultados: Directorio para los resultados.
        umbrales_mi: Intervalos de clasificación del MI.
        logger: Logger utilizado para registrar el proceso.

    Returns:
        Las métricas de mantenibilidad calculadas.

    Raises:
        RuntimeError: Si el CSV no contiene métricas válidas.
    """
    archivo_txt = Path(carpeta_resultados) / f"{proyecto.codigo}_resumen_mi.txt"

    logger.info(f"Calculando índice de mantenibilidad para {proyecto.codigo}")

    metrics_by_file = parse_lizard_csv_mi(archivo_csv_lizard)
    if not metrics_by_file:
        raise RuntimeError("No se encontraron métricas válidas para calcular el MI.")

    metricas_mi = calcular_metricas_mi(metrics_by_file, umbrales_mi)
    generar_resumen_mi_txt(proyecto, metricas_mi, archivo_txt)

    return metricas_mi


def metrica_mantenibilidad_a_dict(metricas_mi):
    """Convierte una métrica de mantenibilidad en un diccionario.

    Args:
        metricas_mi: Instancia que se desea convertir.

    Returns:
        Un diccionario con todos los campos de la métrica.
    """
    return asdict(metricas_mi)
