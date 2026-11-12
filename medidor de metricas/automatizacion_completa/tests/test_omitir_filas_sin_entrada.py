"""No generar filas de las tres hojas cuando no existen métricas de entrada."""

from reportes.escritor_resultados import EscritorResultadosExcel
from reportes.gestor_libros import GestorLibrosExcel
from reportes.excel_utilidades import UtilidadesExcel
import unittest
from openpyxl import Workbook

from constantes import definiciones as d
from modelos.modelos import Proyecto
from reportes.escritor_resultados import EscritorResultadosExcel


class TestOmitirFilasSinEntrada(unittest.TestCase):
    def test_escritores_individuales_no_crean_filas(self):
        for nombre, escribir in (
            ('Concurrencia', EscritorResultadosExcel().escribir_hoja_concurrencia),
            ('Tokens', EscritorResultadosExcel().escribir_hoja_tokens),
            ('CPU_Memoria', EscritorResultadosExcel().escribir_hoja_cpu_memoria),
        ):
            with self.subTest(hoja=nombre):
                libro = Workbook()
                hoja = libro.active
                hoja.title = nombre
                hoja.append(['Código', 'Medición'])
                escribir(hoja, 'PROY-1', None)
                self.assertEqual(hoja.max_row, 1)
                self.assertEqual(UtilidadesExcel().buscar_filas_por_codigo(hoja, 'PROY-1'), [])

    def test_integracion_escritor_mantiene_resumen_sin_detalles_vacios(self):
        libro = Workbook()
        GestorLibrosExcel().crear_hojas_si_no_existen(libro)
        proyecto = Proyecto('PROY-1', 'Prueba', '/tmp/sin_codigo', '', '', 'Wiring')
        EscritorResultadosExcel().guardar_resultado(
            libro, proyecto, metricas_cc=None, metricas_mi=None,
            metricas_concurrencia=None, metricas_tokens=None, metricas_cpu_memoria=None,
        )
        self.assertEqual(len(UtilidadesExcel().buscar_filas_por_codigo(libro[d.HOJA_RESUMEN], proyecto.codigo)), 1)
        for nombre in (d.HOJA_CONCURRENCIA_SALIDA, d.HOJA_TOKENS_SALIDA, d.HOJA_CPU_MEMORIA_SALIDA):
            with self.subTest(hoja=nombre):
                self.assertEqual(UtilidadesExcel().buscar_filas_por_codigo(libro[nombre], proyecto.codigo), [])


if __name__ == '__main__':
    unittest.main()
