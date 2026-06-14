import csv
import subprocess
from pathlib import Path

from util.archivos import crear_directorio, validar_ruta_proyecto
from util.lenguajes import normalizar_lenguaje
from util.modelos import FuncionCompleja, MetricaComplejidad


def ejecutar_lizard(proyecto, carpeta_resultados, logger):
    archivo_csv = ejecutar_lizard_csv(proyecto, carpeta_resultados, logger)
    archivo_txt = Path(carpeta_resultados) / f"{proyecto.codigo}_resumen_lizard.txt"

    metricas = procesar_csv_lizard(archivo_csv)
    generar_resumen_txt(proyecto, metricas, archivo_txt)

    return metricas


def ejecutar_lizard_csv(proyecto, carpeta_resultados, logger):
    """Ejecuta Lizard y devuelve la ruta del CSV generado."""
    validar_ruta_proyecto(proyecto.ruta_codigo)
    crear_directorio(carpeta_resultados)

    lenguaje = normalizar_lenguaje(proyecto.lenguaje)
    archivo_csv = Path(carpeta_resultados) / f"{proyecto.codigo}_lizard.csv"

    comando = [
        "lizard",
        proyecto.ruta_codigo,
        "--languages",
        lenguaje,
        "--csv",
    ]

    logger.info(f"Ejecutando Lizard para {proyecto.codigo}: {' '.join(comando)}")

    resultado = subprocess.run(
        comando,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )

    if resultado.returncode != 0:
        raise RuntimeError(f"Error ejecutando Lizard para {proyecto.codigo}: {resultado.stderr}")

    archivo_csv.write_text(resultado.stdout, encoding="utf-8")
    return archivo_csv


def procesar_csv_lizard(archivo_csv, cantidad_funciones_complejas=10):
    cantidad_funciones = 0
    ccn_total = 0
    nloc_total = 0
    funciones = []

    with open(archivo_csv, "r", encoding="utf-8", errors="replace") as archivo:
        lector = csv.reader(archivo)

        for fila in lector:
            if len(fila) < 2:
                continue

            try:
                nloc = int(fila[0])
                ccn = int(fila[1])
            except ValueError:
                continue

            nombre_funcion = obtener_nombre_funcion(fila)
            archivo_funcion = fila[6] if len(fila) > 6 else ""

            cantidad_funciones += 1
            nloc_total += nloc
            ccn_total += ccn
            funciones.append(
                FuncionCompleja(
                    ccn=ccn,
                    nloc=nloc,
                    nombre=nombre_funcion,
                    archivo=archivo_funcion,
                )
            )

    ccn_promedio = round(ccn_total / cantidad_funciones, 2) if cantidad_funciones > 0 else 0
    nloc_promedio = round(nloc_total / cantidad_funciones, 2) if cantidad_funciones > 0 else 0

    funciones_complejas = sorted(
        funciones,
        key=lambda funcion: (funcion.ccn, funcion.nloc),
        reverse=True,
    )[:cantidad_funciones_complejas]

    return MetricaComplejidad(
        cantidad_funciones=cantidad_funciones,
        ccn_total=ccn_total,
        ccn_promedio=ccn_promedio,
        nloc_total=nloc_total,
        nloc_promedio=nloc_promedio,
        funciones_complejas=funciones_complejas,
    )


def obtener_nombre_funcion(fila):
    if len(fila) > 7 and fila[7].strip():
        return fila[7].strip()

    if len(fila) > 5 and fila[5].strip():
        nombre_lizard = fila[5].split("@")[0].strip()
        return nombre_lizard

    return "Función sin nombre"


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
        archivo.write(f"NLOC promedio: {metricas.nloc_promedio}\n\n")

        archivo.write("Funciones más complejas:\n")
        archivo.write("-" * 37 + "\n")

        if not metricas.funciones_complejas:
            archivo.write("No se detectaron funciones en el código analizado.\n")
        else:
            for funcion in metricas.funciones_complejas:
                archivo.write(
                    f"CCN={funcion.ccn} | "
                    f"NLOC={funcion.nloc} | "
                    f"{funcion.nombre} | "
                    f"{funcion.archivo}\n"
                )
