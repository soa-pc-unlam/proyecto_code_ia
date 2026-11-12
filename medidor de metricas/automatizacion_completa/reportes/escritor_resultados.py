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
from reportes.excel_utilidades import UtilidadesExcel
from reportes.excel_utilidades import UtilidadesExcel
from reportes.formateador import FormateadorExcel
from reportes.excel_utilidades import UtilidadesExcel
from reportes.formateador import FormateadorExcel
from reportes.excel_utilidades import UtilidadesExcel
from reportes.excel_utilidades import UtilidadesExcel
from reportes.formateador import FormateadorExcel
from reportes.excel_utilidades import UtilidadesExcel
from reportes.excel_utilidades import UtilidadesExcel


class EscritorResultadosExcel:
    """Persiste resultados y errores por proyecto."""

    def guardar_resultado(self, libro, proyecto, metricas_cc, metricas_mi, metricas_bugs_smells=None, metricas_concurrencia=None, metricas_tokens=None, metricas_cpu_memoria=None, omitir_metricas_codigo=False):
        """Guarda todas las métricas de un proyecto en Excel.

        Args:
            libro: Libro de Excel en el que se escriben las métricas.
            proyecto: Proyecto analizado.
            metricas_cc: Métricas de complejidad.
            metricas_mi: Métricas de mantenibilidad.
            metricas_bugs_smells: Métricas de incidencias, si existen.
            metricas_concurrencia: Métricas de concurrencia, si existen.
        """
        hoja_resumen = libro[HOJA_RESUMEN]
        hoja_complejidad = libro[HOJA_COMPLEJIDAD]
        hoja_mantenibilidad = libro[HOJA_MANTENIBILIDAD]
        hoja_bugs_smells = libro[HOJA_BUGS_SMELLS]
        hoja_concurrencia = libro[HOJA_CONCURRENCIA_SALIDA]
        hoja_tokens = libro[HOJA_TOKENS_SALIDA]
        hoja_cpu_memoria = libro[HOJA_CPU_MEMORIA_SALIDA]
        self.escribir_hoja_resumen(hoja_resumen, proyecto, metricas_cc, metricas_mi, metricas_bugs_smells, metricas_concurrencia, metricas_tokens, metricas_cpu_memoria)
        if not omitir_metricas_codigo:
            self.escribir_hoja_complejidad(hoja_complejidad, proyecto.codigo, metricas_cc)
            self.escribir_hoja_mantenibilidad(hoja_mantenibilidad, proyecto.codigo, metricas_mi)
            self.escribir_hoja_bugs_smells(hoja_bugs_smells, proyecto, metricas_bugs_smells)
        self.escribir_hoja_concurrencia(hoja_concurrencia, proyecto.codigo, metricas_concurrencia)
        self.escribir_hoja_tokens(hoja_tokens, proyecto.codigo, metricas_tokens)
        self.escribir_hoja_cpu_memoria(hoja_cpu_memoria, proyecto.codigo, metricas_cpu_memoria)

    def guardar_error(self, libro, proyecto, mensaje_error):
        """Registra en el libro un error asociado con un proyecto.

        Args:
            libro: Libro de Excel en el que se registra el error.
            proyecto: Proyecto cuyo análisis falló.
            mensaje_error: Descripción del error producido.
        """
        hoja = libro[HOJA_ERRORES]
        hoja.append([proyecto.codigo, proyecto.nombre_proyecto, mensaje_error])

    def escribir_hoja_resumen(self, hoja, proyecto, metricas_cc, metricas_mi, metricas_bugs_smells=None, metricas_concurrencia=None, metricas_tokens=None, metricas_cpu_memoria=None):
        """Escribe las métricas de un proyecto en la hoja de resumen.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        proyecto: Proyecto analizado.
        metricas_cc: Métricas de complejidad.
        metricas_mi: Métricas de mantenibilidad.
        metricas_bugs_smells: Métricas de incidencias, si existen.
        metricas_concurrencia: Métricas de concurrencia, si existen.
    """
      
        ccn_promedio = metricas_cc.ccn_promedio if metricas_cc else ''
        nivel_cc = metricas_cc.nivel_cc if metricas_cc else ''
        mi = metricas_mi.mi if metricas_mi else ''
        nivel_mi = metricas_mi.nivel_mi if metricas_mi else ''
        issues_kloc = metricas_bugs_smells.issues_kloc if metricas_bugs_smells else ''
        isi = metricas_bugs_smells.isi if metricas_bugs_smells else ''
        interpretacion_isi = metricas_bugs_smells.interpretacion_isi if metricas_bugs_smells else ''
        promedio_concurrencia = metricas_concurrencia.promedio if metricas_concurrencia else ''
        interpretacion_concurrencia = metricas_concurrencia.interpretacion if metricas_concurrencia else ''
        eficiencia_ponderada = metricas_tokens.eficiencia_ponderada if metricas_tokens else ''
        interpretacion_tokens = metricas_tokens.interpretacion if metricas_tokens else ''
        cpu_promedio = getattr(metricas_cpu_memoria, 'cpu_promedio_normalizada', '') if metricas_cpu_memoria else ''
        interpretacion_cpu = getattr(metricas_cpu_memoria, 'interpretacion_cpu', '') if metricas_cpu_memoria else ''
        memoria_promedio = getattr(metricas_cpu_memoria, 'memoria_promedio_normalizada', '') if metricas_cpu_memoria else ''
        interpretacion_memoria = getattr(metricas_cpu_memoria, 'interpretacion_memoria', '') if metricas_cpu_memoria else ''
        valores_resumen = [proyecto.codigo, proyecto.nombre_proyecto, proyecto.herramienta_ia, proyecto.modelo_ia, proyecto.lenguaje, ccn_promedio, nivel_cc, mi, nivel_mi, issues_kloc, isi, interpretacion_isi, promedio_concurrencia, interpretacion_concurrencia, eficiencia_ponderada, interpretacion_tokens, cpu_promedio, interpretacion_cpu, memoria_promedio, interpretacion_memoria]
        if TEXTO_ENCABEZADO_ID_PROYECTO in UtilidadesExcel().obtener_mapa_encabezados(hoja):
            valores = dict(zip(ENCABEZADOS_RESUMEN, valores_resumen))
            valores.update({TEXTO_ENCABEZADO_ID_PROYECTO: getattr(proyecto, 'id_proyecto', ''), TEXTO_ENCABEZADO_DISPOSITIVO: getattr(proyecto, 'dispositivo', ''), TEXTO_ENCABEZADO_IMPLEMENTACION: getattr(proyecto, 'implementacion', '')})
            UtilidadesExcel().escribir_o_actualizar_fila(hoja, proyecto.codigo, valores)
        else:
            UtilidadesExcel().escribir_o_actualizar_fila(hoja, proyecto.codigo, valores_resumen)
        FormateadorExcel().formatear_celdas_hoja_resumen(hoja, proyecto.codigo)

    def escribir_hoja_complejidad(self, hoja, codigo, metricas_cc):
        """Escribe las métricas de complejidad en la hoja correspondiente.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        codigo: Código del proyecto.
        metricas_cc: Métricas de complejidad.
    """
        if metricas_cc is None:
            UtilidadesExcel().escribir_o_actualizar_fila(hoja, codigo, [codigo] + [''] * (hoja.max_column - 1))
            return
        if metricas_cc is not None:
            UtilidadesExcel().escribir_o_actualizar_fila(hoja, codigo, [codigo, metricas_cc.cantidad_funciones, metricas_cc.ccn_total, metricas_cc.ccn_promedio, metricas_cc.nloc_total, metricas_cc.nivel_cc, metricas_cc.interpretacion_cc, metricas_cc.nloc_promedio])

    def escribir_hoja_mantenibilidad(self, hoja, proyecto_codigo, metricas_mi):
        """Escribe las métricas de mantenibilidad en la hoja correspondiente.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        proyecto_codigo: Código del proyecto.
        metricas_mi: Métricas de mantenibilidad.
    """
        if metricas_mi is None:
            UtilidadesExcel().escribir_o_actualizar_fila(hoja, proyecto_codigo, [proyecto_codigo] + [''] * (hoja.max_column - 1))
            return
        if metricas_mi is not None:
            UtilidadesExcel().escribir_o_actualizar_fila(hoja, proyecto_codigo, [proyecto_codigo, metricas_mi.nloc_mi, metricas_mi.cantidad_funciones_mi, metricas_mi.tokens_codigo, metricas_mi.mi, metricas_mi.nivel_mi, metricas_mi.interpretacion_mi])

    def escribir_hoja_bugs_smells(self, hoja, proyecto, metricas_bugs_smells):
        """Escribe las métricas de bugs y smells en la hoja correspondiente.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        proyecto: Proyecto analizado.
        metricas_bugs_smells: Métricas de bugs y smells.
    """
   
        if metricas_bugs_smells is None:
            UtilidadesExcel().escribir_o_actualizar_fila(hoja, proyecto.codigo, [proyecto.codigo] + [''] * (hoja.max_column - 1))
            return
        if metricas_bugs_smells is not None:
            UtilidadesExcel().escribir_o_actualizar_fila(hoja, proyecto.codigo, [proyecto.codigo, metricas_bugs_smells.analizador, metricas_bugs_smells.total_issues, metricas_bugs_smells.issues_kloc, metricas_bugs_smells.isi, metricas_bugs_smells.nivel_isi, metricas_bugs_smells.interpretacion_isi, metricas_bugs_smells.observacion, metricas_bugs_smells.cantidad_alta, metricas_bugs_smells.cantidad_media, metricas_bugs_smells.cantidad_baja, UtilidadesExcel().formatear_top_reglas(metricas_bugs_smells.top_reglas_violadas)])
        FormateadorExcel().formatear_celdas_hoja_bug_smells(hoja, proyecto.codigo)

    def escribir_hoja_concurrencia(self, hoja, codigo, metricas_concurrencia):
        """Escribe las métricas de concurrencia en la hoja correspondiente.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        codigo: Código del proyecto.
        metricas_concurrencia: Métricas de concurrencia.
    """
        if metricas_concurrencia is None:
            return
        if metricas_concurrencia is not None:
            UtilidadesExcel().escribir_o_actualizar_fila(hoja, codigo, [codigo, metricas_concurrencia.sincronizacion_correcta, metricas_concurrencia.ausencia_de_deadlocks, metricas_concurrencia.ausencia_de_condicion_de_carrera, metricas_concurrencia.uso_correcto_de_exclusion_mutua, metricas_concurrencia.promedio, metricas_concurrencia.interpretacion])

    def escribir_hoja_tokens(self, hoja, proyecto_codigo, metricas_tokens):
        """Agrega o actualiza un resultado válido de eficiencia en tokens."""
        if metricas_tokens is None:
            return
        if metricas_tokens is not None:
            valores = [metricas_tokens.codigo, metricas_tokens.metodo_utilizado, metricas_tokens.nloc_total, metricas_tokens.refinamientos, metricas_tokens.tokens_registrados, metricas_tokens.eficiencia_generacion, metricas_tokens.eficiencia_ponderada, metricas_tokens.nivel_eficiencia, metricas_tokens.interpretacion]
            UtilidadesExcel().escribir_o_actualizar_fila(hoja, proyecto_codigo, valores)

    def escribir_hoja_cpu_memoria(self, hoja, proyecto_codigo, metricas):
        """Escribe las mismas métricas de CPU/memoria en PC y Android/ESP32."""
    
        if metricas is None:
            return
        if metricas is not None:
            valores = UtilidadesExcel().obtener_valores_cpu_memoria(metricas)
            UtilidadesExcel().escribir_o_actualizar_fila(hoja, proyecto_codigo, valores)
            FormateadorExcel().aplicar_formato_cpu_memoria(hoja, proyecto_codigo)
