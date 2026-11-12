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
from util.exception import DatosProyectoNoEncontrados
from reportes.excel_utilidades import UtilidadesExcel
from reportes.comparaciones import GeneradorComparaciones
from reportes.formateador import FormateadorExcel
from reportes.formateador import FormateadorExcel
from reportes.excel_utilidades import UtilidadesExcel

class GestorLibrosExcel:
    """Gestiona el ciclo de vida y la persistencia de libros."""

    def crear_o_abrir_salida(self, archivo_excel, hojas_reporte=HOJAS_REPORTE):
        """Abre o crea un libro con las hojas del perfil seleccionado.

        Args:
            archivo_excel: Ruta del libro de resultados.
            hojas_reporte: Nombres y encabezados de las hojas requeridas.
                Por defecto conserva la configuración original de PC.

        Returns:
            El libro preparado para recibir datos.
        """
        if Path(archivo_excel).exists():
            libro = load_workbook(archivo_excel)
        else:
            libro = Workbook()
            libro.remove(libro.active)
        self.crear_hojas_si_no_existen(libro, hojas_reporte)
        return libro

    def abrir_entrada(self, archivo_excel):
        """Abre un libro existente para lectura de datos.

        Args:
            archivo_excel: Ruta del libro de entrada.

        Returns:
            El libro abierto en modo lectura.

        Raises:
            FileNotFoundError: Si el archivo de entrada no existe.
        """
        if not Path(archivo_excel).exists():
            raise FileNotFoundError(f'No se encontró el archivo de entrada: {archivo_excel}')
        return load_workbook(archivo_excel, data_only=True, read_only=True)

    def finalizar(self, libro_salida, archivo_excel, incluir_graficos=True, configuracion_graficos=None, tipo_proyecto=None):
        """Aplica estilos y, opcionalmente, regenera los gráficos del perfil."""

        FormateadorExcel().aplicar_estilos_basicos(libro_salida)
        if isinstance(incluir_graficos, str) and tipo_proyecto is None:
            tipo_proyecto, incluir_graficos = (incluir_graficos, True)
        if incluir_graficos:
            if tipo_proyecto == TIPO_PROYECTO_ESP32_ANDROID:
                GeneradorComparaciones().generar(libro_salida)
                generar_graficos_comparativo_dispositivos(libro_salida)
                configuracion_graficos = None
            if tipo_proyecto != TIPO_PROYECTO_ESP32_ANDROID:
                self._generar_graficos_estandar(libro_salida, configuracion_graficos)
        ruta_salida = Path(archivo_excel)
        ruta_salida.parent.mkdir(parents=True, exist_ok=True)
        ruta_temporal = ruta_salida.with_name(f'.{ruta_salida.stem}.tmp{ruta_salida.suffix}')
        libro_salida.save(ruta_temporal)
        ruta_temporal.replace(ruta_salida)

    def _generar_graficos_estandar(self, libro_salida, configuracion_graficos):
        """Regenera los gráficos definidos por columnas o por encabezados."""
        if configuracion_graficos is None:
            configuracion_graficos = CONFIGURACION_GRAFICOS
        graficos = self.resolver_columnas_graficos(libro_salida, configuracion_graficos)
        generar_graficos_pc(libro_salida, graficos)

    def asegurar_encabezados(self, hoja, encabezados):
        """Crea o actualiza encabezados sin borrar datos existentes.

    Args:
        hoja: Hoja cuyos encabezados deben actualizarse.
        encabezados: Encabezados que deben estar presentes.
    """
        if hoja.max_row == 1 and all((celda.value is None for celda in hoja[1])):
            hoja.append(encabezados)
            hoja.delete_rows(1)
            return
        encabezados_actuales = [hoja.cell(row=1, column=col).value for col in range(1, hoja.max_column + 1)]
        for encabezado in encabezados:
            if encabezado not in encabezados_actuales:
                hoja.cell(row=1, column=hoja.max_column + 1).value = encabezado
                encabezados_actuales.append(encabezado)

    def eliminar_columnas_obsoletas(self, hoja, encabezados_validos):
        """Elimina las columnas ajenas al diseño esperado.

    Args:
        hoja: Hoja que se desea depurar.
        encabezados_validos: Encabezados que deben conservarse.
    """
        encabezados_validos = set(encabezados_validos)
        for columna in range(hoja.max_column, 0, -1):
            encabezado = hoja.cell(row=1, column=columna).value
            if encabezado is not None and encabezado not in encabezados_validos:
                hoja.delete_cols(columna)

    def migrar_columnas_cpu_memoria(self, hoja, encabezados):
        """Ordena columnas anteriores por significado y conserva sus mediciones."""
        actuales = [celda.value for celda in hoja[1]]
        if actuales == list(encabezados):
            return
        alias = {'CPU promedio (%)': TEXTO_ENCABEZADO_CPU_PROMEDIO_NORMALIZADA, 'CPU máxima (%)': TEXTO_ENCABEZADO_CPU_MAXIMA_NORMALIZADA}
        if actuales and actuales[0] != TEXTO_ENCABEZADO_CODIGO:
            return
        if not any((c in actuales for c in alias)):
            return
        filas = []
        for fila in hoja.iter_rows(min_row=2, values_only=True):
            datos = dict(zip(actuales, fila))
            normalizados = {alias.get(c, c): v for c, v in datos.items()}
            filas.append([normalizados.get(c) for c in encabezados])
        hoja.delete_rows(1, hoja.max_row)
        hoja.append(list(encabezados))
        for fila in filas:
            hoja.append(fila)
        for fila in filas:
            if fila and fila[0] is not None:
                FormateadorExcel().aplicar_formato_cpu_memoria(hoja, fila[0])

    def crear_hojas_si_no_existen(self, libro, hojas_reporte=HOJAS_REPORTE):
        """Crea las hojas del reporte y verifica sus encabezados.
        Args:
            libro: Libro de Excel que se desea preparar.
        """
        nombres_antiguos = ('CPU-Memoria PC', 'CPU-Memoria Dispositivos')
        hojas_antiguas = [nombre for nombre in nombres_antiguos if nombre in libro.sheetnames]
       
        if HOJA_CPU_MEMORIA_SALIDA in hojas_reporte and hojas_antiguas:
            if len(hojas_antiguas) > 1 or HOJA_CPU_MEMORIA_SALIDA in libro.sheetnames:
                raise ValueError('Existen varias hojas CPU-Memoria; revise las mediciones antes de migrar')
            libro[hojas_antiguas[0]].title = HOJA_CPU_MEMORIA_SALIDA
       
        for nombre, encabezados in hojas_reporte.items():
            if nombre not in libro.sheetnames:
                libro.create_sheet(nombre).append(encabezados)
                continue
            hoja = libro[nombre]
       
            if nombre == HOJA_CPU_MEMORIA_SALIDA:
                self.migrar_columnas_cpu_memoria(hoja, encabezados)
            self.asegurar_encabezados(hoja, encabezados)
       
            if nombre == HOJA_BUGS_SMELLS:
                self.eliminar_columnas_obsoletas(hoja, encabezados)

    def resolver_columnas_graficos(self, libro, configuracion):
        """Resuelve encabezados de gráficos a columnas reales del Excel.

        Args:
            libro: Libro con las hojas que proporcionan los datos.
            configuracion: Tuplas con columna numérica (PC) o encabezado (dispositivos).

        Returns:
            Lista de configuraciones con columnas numéricas.

        Raises:
            ValueError: Si un encabezado requerido no existe en su hoja.
        """
        resultado = []
        for nombre_hoja, columna, titulo, eje_y, posicion in configuracion:
            if isinstance(columna, str):
                columnas = UtilidadesExcel().obtener_mapa_encabezados(libro[nombre_hoja])
                if columna not in columnas:
                    raise ValueError(f"Falta la columna '{columna}' en la solapa {nombre_hoja}")
                columna = columnas[columna]
            resultado.append((nombre_hoja, columna, titulo, eje_y, posicion))
        return resultado

    def leer_fila_por_codigo(self, libro_entrada, nombre_hoja, codigo, campos, incluir_formato=False):
        """Lee los campos de una única fila identificada por código."""
        if nombre_hoja not in libro_entrada.sheetnames:
            raise ValueError(f"No existe la solapa '{nombre_hoja} 'en el archivo excel con los datos de entrada.")
        hoja = libro_entrada[nombre_hoja]
        encabezados = UtilidadesExcel().obtener_mapa_encabezados(hoja, normalizar=True)
        columna_codigo = UtilidadesExcel().obtener_columna_encabezado(encabezados, TEXTO_ENCABEZADO_CODIGO, nombre_hoja)
        filas = UtilidadesExcel().buscar_filas_por_codigo(hoja, codigo, columna_codigo)
        if not filas:
            raise DatosProyectoNoEncontrados(f'No se encontró el código en {nombre_hoja}')
        if len(filas) > 1:
            raise ValueError(f'Código duplicado en {nombre_hoja}')
        valores = {}
        formatos = {}
        for campo in campos:
            columna = UtilidadesExcel().obtener_columna_encabezado(encabezados, campo, nombre_hoja)
            celda = hoja.cell(row=filas[0], column=columna)
            valores[campo] = celda.value
            if incluir_formato:
                formatos[campo] = celda.number_format
        return (valores, formatos) if incluir_formato else valores
