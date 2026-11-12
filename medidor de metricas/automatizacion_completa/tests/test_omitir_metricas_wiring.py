"""Verifica omisión de filas de código para ESP32 sin fuentes Wiring."""

from reportes.escritor_resultados import EscritorResultadosExcel
from reportes.gestor_libros import GestorLibrosExcel
from reportes.excel_utilidades import UtilidadesExcel
import logging
import tempfile
import unittest
from pathlib import Path
from threading import Semaphore
from unittest.mock import MagicMock

from openpyxl import Workbook

from constantes import definiciones as d
from modelos.modelos import Proyecto
from procesamiento.procesamiento import AnalizadorProyecto, guardar_resultado_proyecto
class TestOmitirMetricasWiring(unittest.TestCase):
    def _proyecto(self, ruta, lenguaje='Wiring'):
        return Proyecto('ESP-SIN-CODIGO', 'Sin fuente', str(ruta), '', '', lenguaje)

    def _libro(self):
        libro = Workbook()
        GestorLibrosExcel().crear_hojas_si_no_existen(libro)
        return libro

    def test_wiring_ausente_omite_las_tres_hojas_y_conserva_error(self):
        with tempfile.TemporaryDirectory() as ruta:
            pipeline = MagicMock()
            logger = logging.getLogger('test_sin_wiring')
            proyecto = self._proyecto(ruta)
            resultado = AnalizadorProyecto(None, logger, None, Semaphore(1), pipeline=pipeline).analizar(proyecto)
            self.assertTrue(resultado.omitir_metricas_codigo)
            self.assertTrue(any('No hay fuentes Wiring' in mensaje for mensaje in resultado.errores))
            pipeline.ejecutar.assert_not_called()
            libro = self._libro()
            guardar_resultado_proyecto(libro, resultado, logger)
            for hoja in (d.HOJA_COMPLEJIDAD, d.HOJA_MANTENIBILIDAD, d.HOJA_BUGS_SMELLS):
                with self.subTest(hoja=hoja):
                    self.assertEqual(UtilidadesExcel().buscar_filas_por_codigo(libro[hoja], proyecto.codigo), [])
            self.assertEqual(len(UtilidadesExcel().buscar_filas_por_codigo(libro[d.HOJA_RESUMEN], proyecto.codigo)), 1)
            self.assertEqual(len(UtilidadesExcel().buscar_filas_por_codigo(libro[d.HOJA_ERRORES], proyecto.codigo)), 1)

    def test_otros_proyectos_mantienen_comportamiento_anterior(self):
        libro = self._libro()
        proyecto = self._proyecto('/tmp/otra_fuente', 'Java')
        from reportes.escritor_resultados import EscritorResultadosExcel
        EscritorResultadosExcel().guardar_resultado(libro, proyecto, None, None, None)
        for hoja in (d.HOJA_COMPLEJIDAD, d.HOJA_MANTENIBILIDAD, d.HOJA_BUGS_SMELLS):
            with self.subTest(hoja=hoja):
                self.assertEqual(len(UtilidadesExcel().buscar_filas_por_codigo(libro[hoja], proyecto.codigo)), 1)

    def test_proyecto_wiring_valido_no_se_omite(self):
        with tempfile.TemporaryDirectory() as ruta:
            Path(ruta, 'programa.ino').write_text('void setup() {}\nvoid loop() {}\n', encoding='utf-8')
            pipeline = MagicMock()
            resultado = AnalizadorProyecto(None, logging.getLogger('test_wiring_valido'), None, Semaphore(1), pipeline=pipeline).analizar(self._proyecto(ruta))
            self.assertFalse(resultado.omitir_metricas_codigo)
            pipeline.ejecutar.assert_called_once()


if __name__ == '__main__':
    unittest.main()
