import csv
import subprocess
from pathlib import Path

from models import MetricaComplejidad
from utils import crear_directorio, normalizar_lenguaje, validar_ruta_proyecto


def ejecutar_lizard(proyecto, carpeta_resultados, logger):
    validar_ruta_proyecto(proyecto.ruta_codigo)

    crear_directorio(carpeta_resultados)

    lenguaje = normalizar_lenguaje(proyecto.lenguaje)
    archivo_csv = Path(carpeta_resultados) / f"{proyecto.codigo}_lizard.csv"
    archivo_txt = Path(carpeta_resultados) / f"{proyecto.codigo}_resumen_lizard.txt"

    comando = [
        "lizard",
        proyecto.ruta_codigo,
        "--languages",
        lenguaje,
        "--csv"
    ]

    logger.info(f"Ejecutando Lizard para {proyecto.codigo}: {' '.join(comando)}")

    resultado = subprocess.run(
        comando,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace"
    )

    if resultado.returncode != 0:
        raise RuntimeError(f"Error ejecutando Lizard para {proyecto.codigo}: {resultado.stderr}")

    archivo_csv.write_text(resultado.stdout, encoding="utf-8")

    metricas = procesar_csv_lizard(archivo_csv)
    generar_resumen_txt(proyecto, metricas, archivo_txt)

    return metricas


def procesar_csv_lizard(archivo_csv):
    cantidad_funciones = 0
    ccn_total = 0
    nloc_total = 0

    with open(archivo_csv, "r", encoding="utf-8", errors="replace") as archivo:
        lector = csv.reader(archivo)

        for fila in lector:
            if len(fila) < 2:
                continue

            try:
                nloc = int(fila[0])
                ccn = int(fila[1])

                cantidad_funciones += 1
                nloc_total += nloc
                ccn_total += ccn

            except ValueError:
                continue

    ccn_promedio = round(ccn_total / cantidad_funciones, 2) if cantidad_funciones > 0 else 0
    nloc_promedio = round(nloc_total / cantidad_funciones, 2) if cantidad_funciones > 0 else 0

    return MetricaComplejidad(
        cantidad_funciones=cantidad_funciones,
        ccn_total=ccn_total,
        ccn_promedio=ccn_promedio,
        nloc_total=nloc_total,
        nloc_promedio=nloc_promedio
    )
    
def generar_resumen_txt(proyecto, metricas, archivo_txt):
    with open(archivo_txt, "w", encoding="utf-8") as archivo:
        archivo.write("RESUMEN DE COMPLEJIDAD CICLOMÁTICA\n")
        archivo.write("=" * 45 + "\n\n")

        archivo.write(f"Código: {proyecto.codigo}\n")
        archivo.write(f"Proyecto: {proyecto.nombre_proyecto}\n")
        archivo.write(f"Herramienta IA: {proyecto.herramienta_ia}\n")
        archivo.write(f"Modelo IA: {proyecto.modelo_ia}\n")
        archivo.write(f"Lenguaje: {proyecto.lenguaje}\n\n")

        archivo.write(f"Cantidad de funciones: {metricas.cantidad_funciones}\n")
        archivo.write(f"CCN total: {metricas.ccn_total}\n")
        archivo.write(f"CCN promedio: {metricas.ccn_promedio}\n")
        archivo.write(f"NLOC total: {metricas.nloc_total}\n")
        archivo.write(f"NLOC promedio: {metricas.nloc_promedio}\n")
