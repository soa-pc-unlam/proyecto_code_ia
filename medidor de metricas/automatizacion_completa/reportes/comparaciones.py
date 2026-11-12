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


class GeneradorComparaciones:
    """Orquesta la comparación Manual/Vibecoding."""

    def generar(self, libro):
        """Genera la comparación Manual/Vibecoding por ID y dispositivo."""
        resumen = libro[HOJA_RESUMEN]
        columnas = self.validar_encabezados_comparacion(resumen)
        parejas = self.obtener_parejas_completas(resumen, columnas)
        hoja, seleccion_anterior = self.preparar_hoja_comparacion(libro)
        self.configurar_encabezado_comparacion(hoja, parejas, seleccion_anterior)
        self.configurar_tabla_auxiliar(hoja, parejas)
        self.generar_tabla_comparacion(hoja, resumen, columnas, parejas)
        self.finalizar_hoja_comparacion(hoja, libro)

    def validar_encabezados_comparacion(self, resumen):
        """Verifica que Resumen contenga las columnas requeridas."""
        from reportes.excel_utilidades import UtilidadesExcel
        columnas = UtilidadesExcel().obtener_mapa_encabezados(resumen)
        faltantes = [campo for campo in ENCABEZADOS_RESUMEN_DISPOSITIVOS if campo not in columnas]
        if faltantes:
            raise ValueError(f'Faltan columnas en Resumen: {faltantes}')
        return columnas

    def registrar_pareja_comparacion(self, parejas, identificador, dispositivo, implementacion, codigo):
        """Registra una implementación según el ID y el dispositivo."""
        from reportes.excel_utilidades import UtilidadesExcel
        tipo = UtilidadesExcel().normalizar_texto(implementacion)
        manual = UtilidadesExcel().normalizar_texto(TEXTO_MANUAL)
        vibecoding = UtilidadesExcel().normalizar_texto(TEXTO_VIBECODING)
        if tipo not in (manual, vibecoding):
            return
        if not dispositivo:
            raise ValueError(f"El proyecto '{identificador}' no tiene dispositivo.")
        identificador = str(identificador).strip()
        dispositivo = str(dispositivo).strip()
        clave = (identificador, UtilidadesExcel().normalizar_texto(dispositivo))
        pareja = parejas.setdefault(clave, {'dispositivo': dispositivo})
        if tipo in pareja:
            raise ValueError(f"La implementación '{implementacion}' está duplicada para el ID '{identificador}' y dispositivo '{dispositivo}'.")
        pareja[tipo] = codigo

    def obtener_parejas_completas(self, resumen, columnas):
        """Obtiene las parejas Manual/Vibecoding por ID y dispositivo."""
        from reportes.excel_utilidades import UtilidadesExcel
        parejas = {}
        for fila in range(2, resumen.max_row + 1):
            identificador = resumen.cell(fila, columnas[TEXTO_ENCABEZADO_ID_PROYECTO]).value
            implementacion = resumen.cell(fila, columnas[TEXTO_ENCABEZADO_IMPLEMENTACION]).value
            dispositivo = resumen.cell(fila, columnas[TEXTO_ENCABEZADO_DISPOSITIVO]).value
            codigo = resumen.cell(fila, columnas[TEXTO_ENCABEZADO_CODIGO]).value
            if not identificador or not codigo:
                continue
            self.registrar_pareja_comparacion(parejas, identificador, dispositivo, implementacion, codigo)
        manual = UtilidadesExcel().normalizar_texto(TEXTO_MANUAL)
        vibecoding = UtilidadesExcel().normalizar_texto(TEXTO_VIBECODING)
        completas = [(identificador, pareja['dispositivo'], pareja[manual], pareja[vibecoding]) for (identificador, _), pareja in parejas.items() if manual in pareja and vibecoding in pareja]
        return sorted(completas, key=lambda p: (p[0], UtilidadesExcel().normalizar_texto(p[1])))

    def preparar_hoja_comparacion(self, libro):
        """Crea la hoja conservando el ID y dispositivo seleccionados."""
        seleccion_anterior = (None, None)
        if HOJA_COMPARACION in libro.sheetnames:
            anterior = libro[HOJA_COMPARACION]
            seleccion_anterior = (anterior['B3'].value, anterior['B4'].value)
            del libro[HOJA_COMPARACION]
        hoja = libro.create_sheet(HOJA_COMPARACION, 1)
        hoja.sheet_view.showGridLines = False
        hoja.freeze_panes = 'B7'
        hoja.column_dimensions['A'].width = 37
        hoja.column_dimensions['B'].width = 43
        hoja.column_dimensions['C'].width = 43
        return (hoja, seleccion_anterior)

    def configurar_encabezado_comparacion(self, hoja, parejas, seleccion_anterior):
        """Configura el título, los dos selectores y los encabezados."""
        from reportes.excel_utilidades import UtilidadesExcel
        hoja.merge_cells('A1:C1')
        hoja['A1'] = 'COMPARACIÓN · MANUAL / VIBECODING'
        hoja['A1'].fill = PatternFill('solid', fgColor='1F4E78')
        hoja['A1'].font = Font(name='Calibri', size=14, color='FFFFFF', bold=True)
        hoja['A1'].alignment = Alignment(vertical='center')
        hoja.row_dimensions[1].height = 32
        id_anterior, dispositivo_anterior = seleccion_anterior
        identificadores = {p[0] for p in parejas}
        identificador = id_anterior if id_anterior in identificadores else parejas[0][0] if parejas else ''
        dispositivos = [p[1] for p in parejas if p[0] == identificador]
        dispositivo = next((d for d in dispositivos if dispositivo_anterior and UtilidadesExcel().normalizar_texto(d) == UtilidadesExcel().normalizar_texto(dispositivo_anterior)), dispositivos[0] if dispositivos else '')
        hoja['A3'] = 'ID proyecto'
        hoja['A3'].font = Font(bold=True)
        hoja['B3'] = identificador
        hoja['A4'] = 'Dispositivo'
        hoja['A4'].font = Font(bold=True)
        hoja['B4'] = dispositivo
        for direccion in ('B3', 'B4'):
            celda = hoja[direccion]
            celda.fill = PatternFill('solid', fgColor=COLOR_ENCABEZADO_RESUMEN)
            celda.font = Font(bold=True, color='000000')
            celda.alignment = Alignment(horizontal='center')
        hoja['A5'] = 'Seleccioná un ID y un dispositivo para comparar ambas implementaciones'
        titulos = ('Campo', TEXTO_MANUAL, TEXTO_VIBECODING)
        for columna, titulo in enumerate(titulos, 1):
            celda = hoja.cell(row=6, column=columna, value=titulo)
            celda.fill = PatternFill('solid', fgColor=COLOR_ENCABEZADO_RESUMEN)
            celda.font = Font(color='000000', bold=True)
            celda.alignment = Alignment(horizontal='center')
        hoja.row_dimensions[6].height = 25

    def configurar_tabla_auxiliar(self, hoja, parejas):
        """Configura los datos auxiliares y los dos desplegables."""
        from reportes.excel_utilidades import UtilidadesExcel
        for fila, (identificador, dispositivo, manual, vibecoding) in enumerate(parejas, 2):
            hoja.cell(fila, 5, identificador)
            hoja.cell(fila, 6, dispositivo)
            hoja.cell(fila, 7, manual)
            hoja.cell(fila, 8, vibecoding)
            clave = f'{identificador}|{UtilidadesExcel().normalizar_texto(dispositivo)}'
            hoja.cell(fila, 9, clave)
        identificadores = sorted({p[0] for p in parejas})
        dispositivos = sorted({p[1] for p in parejas}, key=UtilidadesExcel().normalizar_texto)
        for fila, identificador in enumerate(identificadores, 2):
            hoja.cell(fila, 10, identificador)
        for fila, dispositivo in enumerate(dispositivos, 2):
            hoja.cell(fila, 11, dispositivo)
        for letra in 'EFGHIJK':
            hoja.column_dimensions[letra].hidden = True
        if not parejas:
            return
        ultima_id = len(identificadores) + 1
        validacion_id = DataValidation(type='list', formula1=f'=$J$2:$J${ultima_id}', allow_blank=False)
        validacion_id.showErrorMessage = True
        validacion_id.error = 'Seleccioná un ID existente'
        hoja.add_data_validation(validacion_id)
        validacion_id.add(hoja['B3'])
        ultimo_dispositivo = len(dispositivos) + 1
        validacion_dispositivo = DataValidation(type='list', formula1=f'=$K$2:$K${ultimo_dispositivo}', allow_blank=False)
        validacion_dispositivo.showErrorMessage = True
        validacion_dispositivo.error = 'Seleccioná un dispositivo existente'
        hoja.add_data_validation(validacion_dispositivo)
        validacion_dispositivo.add(hoja['B4'])

    def generar_formula_codigo_comparacion(self, columna_auxiliar, ultima_fila):
        """Busca el código según el ID y dispositivo seleccionados."""
        clave = '$B$3&"|"&LOWER($B$4)'
        return f'=IF(OR($B$3="",$B$4=""),"Sin datos",IFERROR(INDEX(${columna_auxiliar}$2:${columna_auxiliar}${ultima_fila},MATCH({clave},$I$2:$I${ultima_fila},0)),"Sin datos"))'

    def generar_formula_valor_comparacion(self, campo, columna, fila_codigo, columnas, rango_codigos, ultima_resumen):
        """Busca una métrica del proyecto en la hoja Resumen."""
        columna_valor = get_column_letter(columnas[campo])
        rango_valor = f"'{HOJA_RESUMEN}'!${columna_valor}$2:${columna_valor}${ultima_resumen}"
        consulta = f'INDEX({rango_valor},MATCH({columna}${fila_codigo},{rango_codigos},0))'
        return f'=IF(OR($B$3="",$B$4="",{columna}${fila_codigo}="Sin datos"),"Sin datos",IFERROR(IF(COUNTBLANK({consulta})>0,"Sin datos",{consulta}),"Sin datos"))'

    def aplicar_estilo_celda_comparacion(self, celda, campo, borde):
        """Aplica los estilos correspondientes al tipo de campo."""
        celda.font = Font(name='Calibri', size=11, color='000000')
        celda.alignment = Alignment(horizontal='left', vertical='center', wrap_text=True)
        celda.border = borde
        if campo == TEXTO_ENCABEZADO_CODIGO:
            color = COLOR_CODIGO_RESUMEN
        elif campo in ENCABEZADOS_INTERPRETACION_RESUMEN:
            color = COLOR_INTERPRETACION_RESUMEN
        elif campo in ENCABEZADOS_DATOS_PROYECTO_RESUMEN:
            color = COLOR_DATOS_PROYECTO_RESUMEN
        else:
            color = None
        if color:
            celda.fill = PatternFill('solid', fgColor=color)
        if campo in (TEXTO_ENCABEZADO_CPU_PROMEDIO, TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN):
            celda.number_format = '0.00\\%'

    def generar_tabla_comparacion(self, hoja, resumen, columnas, parejas):
        """Genera las métricas Manual/Vibecoding y aplica sus estilos."""
        campos = ENCABEZADOS_RESUMEN_DISPOSITIVOS
        ultima_auxiliar = max(2, len(parejas) + 1)
        ultima_resumen = max(2, resumen.max_row)
        fila_codigo = 7 + campos.index(TEXTO_ENCABEZADO_CODIGO)
        columna_codigo = get_column_letter(columnas[TEXTO_ENCABEZADO_CODIGO])
        rango_codigos = f"'{HOJA_RESUMEN}'!${columna_codigo}$2:${columna_codigo}${ultima_resumen}"
        borde = Border(bottom=Side(style='hair', color='D9E2F3'))
        for fila, campo in enumerate(campos, 7):
            etiqueta = hoja.cell(fila, 1, campo)
            etiqueta.font = Font(color='000000', bold=True)
            etiqueta.fill = PatternFill('solid', fgColor='E7EEF4')
            etiqueta.border = borde
            etiqueta.alignment = Alignment(vertical='center', wrap_text=True)
            hoja.row_dimensions[fila].height = 30
            for columna, origen in (('B', 'G'), ('C', 'H')):
                celda = hoja[f'{columna}{fila}']
                if campo == TEXTO_ENCABEZADO_CODIGO:
                    celda.value = self.generar_formula_codigo_comparacion(origen, ultima_auxiliar)
                else:
                    celda.value = self.generar_formula_valor_comparacion(campo, columna, fila_codigo, columnas, rango_codigos, ultima_resumen)
                self.aplicar_estilo_celda_comparacion(celda, campo, borde)

    def finalizar_hoja_comparacion(self, hoja, libro):
        """Configura el formato condicional y recálculo automático."""
        ultima_fila = 6 + len(ENCABEZADOS_RESUMEN_DISPOSITIVOS)
        hoja.conditional_formatting.add(f'B7:C{ultima_fila}', FormulaRule(formula=['B7="Sin datos"'], font=Font(color='008000', bold=True)))
        libro.calculation.calcMode = 'auto'
        libro.calculation.fullCalcOnLoad = True
        libro.calculation.forceFullCalc = True
