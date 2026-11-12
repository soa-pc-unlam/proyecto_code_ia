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


class FormateadorExcel:
    """Centraliza el formato general sin modificar los datos."""

    def aplicar_estilos_basicos(self, libro):
        """Aplica estilos y ajusta columnas de las hojas del reporte."""
        for hoja in libro.worksheets:
            if hoja.title == HOJA_COMPARACION:
                continue
            for celda in hoja[1]:
                celda.font = Font(bold=True)
                celda.fill = PatternFill('solid', fgColor='D9EAF7')
                celda.alignment = Alignment(horizontal='center')
            for indice, columna in enumerate(hoja.columns, start=1):
                max_largo = 0
                letra = get_column_letter(indice)
                limitar = hoja.title == HOJA_BUGS_SMELLS and columna[0].value in COLUMNAS_LARGAS_LIMITADAS
                for celda in columna:
                    if celda.value is not None:
                        max_largo = max(max_largo, len(str(celda.value)))
                    if limitar and celda.row > 1:
                        alineacion = copy(celda.alignment)
                        alineacion.wrap_text = False
                        alineacion.shrink_to_fit = False
                        celda.alignment = alineacion
                hoja.column_dimensions[letra].width = 50 if limitar else min(max_largo + 3, 45)

    def formatear_celdas_hoja_bug_smells(self, hoja, codigo):
        """Aplica formato a las celdas de la hoja de bugs y smells.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        codigo: Código del proyecto.
    """
        from reportes.excel_utilidades import UtilidadesExcel
        filas = UtilidadesExcel().buscar_filas_por_codigo(hoja, codigo)
        if len(filas) > 1:
            raise ValueError(f"Código duplicado '{codigo}' en {hoja.title}")
        if filas:
            fila = filas[0]
            columna_reglas = UtilidadesExcel().obtener_mapa_encabezados(hoja)[TEXTO_ENCABEZADO_REGLAS_INCUMPLIDAS]
            celda_top_reglas = hoja.cell(row=fila, column=columna_reglas)
            celda_top_reglas.alignment = Alignment(wrap_text=True, vertical='top')
            hoja.column_dimensions[celda_top_reglas.column_letter].width = 45

    def formatear_celdas_hoja_resumen(self, hoja, codigo):
        """Colorea datos e interpretaciones usando los encabezados reales."""
        from reportes.excel_utilidades import UtilidadesExcel
        filas = UtilidadesExcel().buscar_filas_por_codigo(hoja, codigo)
        if len(filas) > 1:
            raise ValueError(f"Código duplicado '{codigo}' en {hoja.title}")
        if not filas:
            return
        fila = filas[0]
        columnas = UtilidadesExcel().obtener_mapa_encabezados(hoja)
        self.formatear_encabezados(hoja)
        self.formatear_codigo_proyecto(hoja, fila, columnas)
        self.formatear_datos_proyecto(hoja, fila, columnas)
        self.formatear_interpretaciones(hoja, fila, columnas)
        self.formatear_porcentaje_cpu_mem(hoja, fila, columnas)
        self.mostrar_datos_segun_formato(hoja, fila, columnas)

    def mostrar_datos_segun_formato(self, hoja, fila, columnas):
        campos_identificacion = {TEXTO_ENCABEZADO_CODIGO, *ENCABEZADOS_DATOS_PROYECTO_RESUMEN, 'ID proyecto', 'Dispositivo', 'Implementación'}
        for encabezado, columna in columnas.items():
            if encabezado in campos_identificacion:
                continue
            celda = hoja.cell(row=fila, column=columna)
            fuente = copy(celda.font)
            if celda.value is None or celda.value == '' or celda.value == 'Sin datos':
                celda.value = 'Sin datos'
                fuente.color = '008000'
                fuente.bold = True
                alineacion = copy(celda.alignment)
                if encabezado in COLUMNAS_RESUMEN_ALINEADAS_DERECHA:
                    alineacion.horizontal = 'right'
                else:
                    alineacion.horizontal = 'left'
                celda.alignment = alineacion
            else:
                fuente.color = '000000'
                fuente.bold = False
            celda.font = fuente

    def formatear_porcentaje_cpu_mem(self, hoja, fila, columnas):
        for encabezado in (TEXTO_ENCABEZADO_CPU_PROMEDIO, TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN):
            columna = columnas.get(encabezado)
            if columna is not None:
                hoja.cell(row=fila, column=columna).number_format = '0.00\\%'

    def formatear_interpretaciones(self, hoja, fila, columnas):
        for encabezado in ENCABEZADOS_INTERPRETACION_RESUMEN:
            columna = columnas.get(encabezado)
            if columna is not None:
                hoja.cell(row=fila, column=columna).fill = PatternFill('solid', fgColor=COLOR_INTERPRETACION_RESUMEN)

    def formatear_datos_proyecto(self, hoja, fila, columnas):
        for encabezado in ENCABEZADOS_DATOS_PROYECTO_RESUMEN:
            columna = columnas.get(encabezado)
            if columna is not None:
                hoja.cell(row=fila, column=columna).fill = PatternFill('solid', fgColor=COLOR_DATOS_PROYECTO_RESUMEN)

    def formatear_codigo_proyecto(self, hoja, fila, columnas):
        hoja.cell(row=fila, column=columnas[TEXTO_ENCABEZADO_CODIGO]).fill = PatternFill('solid', fgColor=COLOR_CODIGO_RESUMEN)

    def formatear_encabezados(self, hoja):
        for celda in hoja[1]:
            celda.fill = PatternFill('solid', fgColor=COLOR_ENCABEZADO_RESUMEN)

    def aplicar_formato_cpu_memoria(self, hoja, codigo):
        """Aplica el mismo formato de CPU/memoria a todos los dispositivos."""
        from reportes.excel_utilidades import UtilidadesExcel
        filas = UtilidadesExcel().buscar_filas_por_codigo(hoja, codigo)
        if not filas:
            raise ValueError(f'No se encontró el código en {hoja.title}')
        if len(filas) > 1:
            raise ValueError(f'Código duplicado en {hoja.title}')
        fila = filas[0]
        for columna in (7, 9, 10, 11, 12, 13):
            hoja.cell(row=fila, column=columna).number_format = '0.00\\%'
        hoja.cell(row=fila, column=20).number_format = '0.00\\%'
        for columna in (16, 17, 18, 19, 21):
            hoja.cell(row=fila, column=columna).number_format = '0.00'
