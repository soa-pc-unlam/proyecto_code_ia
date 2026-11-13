"""Cálculo de complejidad ciclomática mediante Lizard."""

import csv
import subprocess
from contextlib import nullcontext
from io import StringIO
from pathlib import Path

from constantes.definiciones import LENGUAJE_WIRING, TIEMPO_MAXIMO_ANALIZADOR
from util.archivos import crear_directorio, validar_ruta_proyecto
from util.fuentes_wiring import preparar_fuentes_wiring
from modelos.modelos import FuncionCompleja, MetricaComplejidad
from metricas.herramientas import EjecutorHerramientas

carpetas_excluidas_lizard = {
       ".venv",
        "venv",
        "env",
        "site-packages",
        "__pycache__",
        "target",
        "build",
        ".gradle",
        "dist",
        "out",
}

class AnalizadorComplejidad:
    """Orquesta la medición de complejidad de un proyecto."""
    @staticmethod
    def ejecutar_lizard(
        proyecto, lenguaje, carpeta_resultados, logger, fuentes_wiring=None
    ):
        """Ejecuta Lizard y genera las métricas y el resumen de un proyecto.

        Args:
            proyecto: Proyecto que debe analizarse.
            lenguaje: Lenguaje de programación del proyecto.
            carpeta_resultados: Directorio para los archivos generados.
            logger: Logger utilizado para registrar la ejecución.

        Returns:
            tuple[MetricaComplejidad, Path]: Métricas de complejidad y ruta del CSV
                generado, en ese orden.
        """
        archivo_csv = EjecutorLizard.ejecutar_lizard_csv(
            proyecto, lenguaje, carpeta_resultados, logger, fuentes_wiring
        )
        archivo_txt = Path(carpeta_resultados) / f"{proyecto.codigo}_resumen_lizard.txt"

        metricas = ProcesadorComplejidad.procesar_csv_lizard(archivo_csv)
        GeneradorResumenComplejidad.generar_resumen_txt(proyecto, metricas, archivo_txt)

        return metricas, archivo_csv

class EjecutorLizard:
    """Ejecuta Lizard y adapta CSV de fuentes Wiring."""
    @staticmethod
    def ejecutar_lizard_csv(
        proyecto, lenguaje, carpeta_resultados, logger, fuentes_wiring=None
    ):
        """Ejecuta Lizard y guarda su salida en formato CSV.

        Args:
            proyecto: Proyecto que debe analizarse.
            lenguaje: Identificador normalizado; Wiring se analiza como C++.
            carpeta_resultados: Directorio para el CSV generado.
            logger: Logger utilizado para registrar la ejecución.

        Returns:
            Ruta del archivo CSV generado.

        Raises:
            RuntimeError: Si Lizard falla o no devuelve funciones Wiring válidas.
        """
        validar_ruta_proyecto(proyecto.ruta_codigo)
        crear_directorio(carpeta_resultados)

    
        archivo_csv = Path(carpeta_resultados) / f"{proyecto.codigo}_lizard.csv"

        preparacion = nullcontext((proyecto.ruta_codigo, {}))
        lenguaje_lizard = lenguaje

        if lenguaje == LENGUAJE_WIRING:
            preparacion = preparar_fuentes_wiring(proyecto.ruta_codigo, fuentes_wiring)
            lenguaje_lizard = "cpp"

        with preparacion as (ruta_fuentes, rutas_originales):
            comando = EjecutorLizard.obtener_comando_lizard(lenguaje, lenguaje_lizard, ruta_fuentes)
            logger.debug(f"[{proyecto.codigo}] Lizard: {' '.join(comando)}")
            resultado = EjecutorHerramientas().ejecutar( comando, timeout=TIEMPO_MAXIMO_ANALIZADOR)
        
            if resultado.returncode != 0:
                raise RuntimeError(f"Error ejecutando Lizard para {proyecto.codigo}: {resultado.stderr}")
            if lenguaje == LENGUAJE_WIRING:
                EjecutorLizard.guardar_csv_wiring(resultado.stdout, archivo_csv, rutas_originales)
            else:
                archivo_csv.write_text(resultado.stdout, encoding="utf-8")
        return archivo_csv

    @staticmethod
    def obtener_comando_lizard(lenguaje, lenguaje_lizard, ruta_fuentes):
        comando = ["lizard", str(ruta_fuentes), "--languages", lenguaje_lizard, "--csv"]

        
        
        if lenguaje != LENGUAJE_WIRING:
            for carpeta in (carpetas_excluidas_lizard):
                comando.extend(["-x", f"*/{carpeta}/*"])
        return comando

    @staticmethod
    def guardar_csv_wiring(salida, archivo_csv, rutas_originales):
        """Guarda el CSV de Lizard restaurando nombres y rutas de las fuentes.

        Args:
            salida: CSV devuelto por Lizard sobre las copias temporales.
            archivo_csv: Archivo de destino compatible con los cálculos existentes.
            rutas_originales: Mapa de rutas temporales a originales.

        Raises:
            RuntimeError: Si no hay funciones o el CSV contiene rutas desconocidas.
        """
        filas = list(csv.reader(StringIO(salida)))
        if not filas:
            raise RuntimeError("No se encontraron funciones Wiring para calcular CCN y MI")
        for fila in filas:
            if len(fila) < 8 or Path(fila[6]) not in rutas_originales:
                raise RuntimeError("Lizard devolvió un CSV Wiring incompleto")
            original = str(rutas_originales[Path(fila[6])])
            fila[5] = fila[5].replace(fila[6], original)
            fila[6] = original
        with archivo_csv.open("w", newline="", encoding="utf-8") as archivo:
            csv.writer(archivo).writerows(filas)

class ProcesadorComplejidad:
    """Calcula NLOC y complejidad desde la salida CSV."""
    @staticmethod
    def procesar_csv_lizard(archivo_csv, cantidad_funciones_complejas=10):
        """Calcula métricas de complejidad a partir de un CSV de Lizard.

        Args:
            archivo_csv: Ruta del archivo CSV de entrada.
            cantidad_funciones_complejas: Máximo de funciones destacadas.

        Returns:
            Las métricas de complejidad agregadas.
        """
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

                nombre_funcion = ProcesadorComplejidad.obtener_nombre_funcion(fila)
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

    @staticmethod
    def obtener_nombre_funcion(fila):
        """Obtiene el nombre de una función desde una fila de Lizard.

        Args:
            fila: Columnas de una fila del CSV.

        Returns:
            El nombre detectado o un texto sustituto.
        """
        if len(fila) > 7 and fila[7].strip():
            return fila[7].strip()

        if len(fila) > 5 and fila[5].strip():
            nombre_lizard = fila[5].split("@")[0].strip()
            return nombre_lizard

        return "Función sin nombre"

class GeneradorResumenComplejidad:
    """Escribe el informe textual de complejidad."""
    @staticmethod
    def generar_resumen_txt(proyecto, metricas, archivo_txt):
        """Escribe un resumen de complejidad en texto plano.

        Args:
            proyecto: Proyecto al que pertenecen las métricas.
            metricas: Métricas de complejidad calculadas.
            archivo_txt: Ruta del archivo de salida.
        """
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
