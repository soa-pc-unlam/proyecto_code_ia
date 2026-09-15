"""Pruebas de escritura de resultados en reportes Excel."""

import unittest

from openpyxl import Workbook

from reportes.excel import (
    crear_hojas_si_no_existen,
    escribir_hoja_tokens,
    guardar_error_excel,
)
from modelos.modelos import MetricaTokens, Proyecto


class ReportesTest(unittest.TestCase):
    """Verifica la creación y actualización de los reportes."""

    def test_los_errores_del_mismo_proyecto_no_se_sobrescriben(self):
        """Conserva todos los errores registrados para un mismo proyecto."""
        libro = Workbook()
        libro.remove(libro.active)
        crear_hojas_si_no_existen(libro)
        proyecto = Proyecto("M1", "Prueba", "ruta", "IA", "Modelo", "Python")
        guardar_error_excel(libro, proyecto, "Primer error")
        guardar_error_excel(libro, proyecto, "Segundo error")
        hoja = libro["Errores"]
        self.assertEqual(hoja.max_row, 3)
        self.assertEqual(hoja.cell(2, 3).value, "Primer error")
        self.assertEqual(hoja.cell(3, 3).value, "Segundo error")

    def test_tokens_actualiza_sin_duplicar(self):
        """Actualiza la fila de tokens correspondiente al mismo código."""
        libro = Workbook()
        libro.remove(libro.active)
        crear_hojas_si_no_existen(libro)
        metrica = MetricaTokens("M1", "Chat", 100, 1, 2000, 50, 45.45,
                                "Alto", "Interpretación")
        escribir_hoja_tokens(libro["Tokens"], "M1", metrica)
        metrica.refinamientos = 2
        escribir_hoja_tokens(libro["Tokens"], "M1", metrica)
        hoja = libro["Tokens"]
        self.assertEqual(hoja.max_row, 2)
        self.assertEqual(hoja.cell(2, 4).value, 2)

    def test_tokens_no_escribe_resultado_parcial(self):
        """No crea filas de datos cuando la métrica no está disponible."""
        libro = Workbook()
        libro.remove(libro.active)
        crear_hojas_si_no_existen(libro)
        escribir_hoja_tokens(libro["Tokens"], "M1", None)
        self.assertEqual(libro["Tokens"].max_row, 2)


if __name__ == "__main__":
    unittest.main()
