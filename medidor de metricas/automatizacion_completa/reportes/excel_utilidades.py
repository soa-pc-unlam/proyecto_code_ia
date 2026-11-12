from pathlib import Path
from copy import copy
import unicodedata
from openpyxl import Workbook, load_workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.formatting.rule import FormulaRule
from openpyxl.utils import get_column_letter
from constantes.definiciones import *
from reportes.graficos import generar_graficos_comparativo_dispositivos, generar_graficos_pc


class UtilidadesExcel:
    """Operaciones compartidas de búsqueda, encabezados y normalización."""

    def normalizar_texto(self, texto):
        """Convierte un valor a texto sin acentos, mayúsculas ni espacios externos.

    Args:
        texto: Valor que se desea normalizar; None se trata como una cadena vacía.

    Returns:
        str: Texto normalizado; conserva los espacios internos.
    """
        texto = '' if texto is None else str(texto).strip().lower()
        texto = unicodedata.normalize('NFKD', texto)
        return ''.join((caracter for caracter in texto if not unicodedata.combining(caracter)))

    def buscar_filas_por_codigo(self, hoja, codigo, columna_codigo=1, normalizar=True):
        """Devuelve todas las filas cuyo código coincide con el solicitado."""
        valor_buscado = self.normalizar_texto(codigo) if normalizar else codigo
        filas = []
        for fila in range(2, hoja.max_row + 1):
            valor_celda = hoja.cell(row=fila, column=columna_codigo).value
            if normalizar:
                valor_celda = self.normalizar_texto(valor_celda)
            if valor_celda == valor_buscado:
                filas.append(fila)
        return filas

    def escribir_o_actualizar_fila(self, hoja, codigo, valores):
        """Agrega o actualiza una fila por código, por posición o encabezado.

    Args:
        hoja: Hoja que se desea modificar.
        codigo: Código usado como clave de la fila.
        valores: Lista en el orden de columnas o diccionario por encabezado.
    """
        filas_existentes = self.buscar_filas_por_codigo(hoja, codigo)
        if len(filas_existentes) > 1:
            raise ValueError(f"Código duplicado '{codigo}' en {hoja.title}")
        if isinstance(valores, dict):
            if filas_existentes:
                fila = filas_existentes[0]
            else:
                hoja.append([codigo])
                fila = hoja.max_row
            self.escribir_valores_por_encabezado(hoja, fila, valores)
            return
        if not filas_existentes:
            hoja.append(valores)
        else:
            fila_existente = filas_existentes[0]
            for columna, valor in enumerate(valores, start=1):
                hoja.cell(row=fila_existente, column=columna).value = valor

    def escribir_valores_por_encabezado(self, hoja, fila, valores):
        """Escribe varios valores en columnas identificadas por sus encabezados.

    Args:
        hoja: Hoja que se desea modificar.
        fila: Número de fila de destino.
        valores: Pares formados por encabezado y valor.
    """
        encabezados = self.obtener_mapa_encabezados(hoja)
        for encabezado, valor in valores.items():
            columna = encabezados.get(encabezado)
            if columna is not None:
                hoja.cell(row=fila, column=columna).value = valor

    def obtener_mapa_encabezados(self, hoja, normalizar=False):
        """Crea un mapa entre encabezados y números de columna.

    Args:
        hoja: Hoja que se desea inspeccionar.
        normalizar: Si es True, normaliza el texto de los encabezados.
        normalizar: Si es True, normaliza el texto de los encabezados.

    Returns:
        Diccionario de encabezados a columnas.
    """
        primera_fila = next(hoja.iter_rows(min_row=1, max_row=1, values_only=True), ())
        return {self.normalizar_texto(valor) if normalizar else valor: columna for columna, valor in enumerate(primera_fila, start=1)}

    def obtener_columna_encabezado(self, encabezados, nombre, nombre_hoja):
        """Obtiene la columna de un encabezado obligatorio.

    Args:
        encabezados (dict): Mapa de encabezados normalizados a números de columna.
        nombre (str): Encabezado buscado, que se normaliza antes de consultar.
        nombre_hoja (str): Nombre de la hoja usado en el mensaje de error.

    Returns:
        int: Número de columna, contado desde uno.

    Raises:
        ValueError: Si el encabezado no está presente en el mapa.
    """
        columna = encabezados.get(self.normalizar_texto(nombre))
        if columna is None:
            raise ValueError(f"Falta la columna '{nombre}' en la solapa {nombre_hoja}")
        return columna

    def obtener_valores_cpu_memoria(self, metricas):
        """Devuelve los valores de CPU/memoria en el orden definido para la salida."""
        return [metricas.codigo, metricas.lenguaje, metricas.metodo_medicion, metricas.modo_cpu, metricas.cpus_logicas, metricas.accion_1, metricas.cpu_medida_1, metricas.accion_2, metricas.cpu_medida_2, metricas.cpu_normalizada_1, metricas.cpu_normalizada_2, metricas.cpu_promedio_normalizada, metricas.cpu_maxima_normalizada, metricas.nivel_cpu, metricas.interpretacion_cpu, metricas.memoria_acc1, metricas.memoria_acc2, metricas.memoria_total, metricas.memoria_promedio, metricas.memoria_promedio_normalizada, metricas.memoria_maxima, metricas.nivel_memoria, metricas.interpretacion_memoria]

    def formatear_top_reglas(self, top_reglas):
        """Convierte el ranking de reglas en texto legible.

    Args:
        top_reglas: Pares formados por regla y cantidad.

    Returns:
        Las reglas y cantidades separadas por punto y coma.
    """
        return '; '.join((f'{regla}: {cantidad}' for regla, cantidad in top_reglas))
