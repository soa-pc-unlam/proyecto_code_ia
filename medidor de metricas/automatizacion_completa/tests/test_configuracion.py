"""Pruebas de carga, clasificación y validación de la configuración."""

import tempfile
import unittest
from openpyxl import Workbook
from configuracion.lector_proyectos_excel import LectorProyectosExcel

from configuracion.configuracion import (
    clasificar_por_umbrales,
    validar_coeficiente_penalizacion,
    validar_umbrales,
)


class ConfiguracionTest(unittest.TestCase):
    """Verifica las reglas aplicadas a configuración y proyectos."""

    def test_clasificar_por_umbrales_respeta_limites(self):
        """Asigna cada valor al intervalo que incluye sus límites."""
        umbrales = [
            {"min": 0, "max": 1, "nivel": "Bajo", "interpretacion": "A"},
            {"min": 1.01, "max": None, "nivel": "Alto", "interpretacion": "B"},
        ]
        self.assertEqual(clasificar_por_umbrales(1, umbrales), umbrales[0])
        self.assertEqual(clasificar_por_umbrales(1.01, umbrales), umbrales[1])

    def test_validar_umbrales_rechaza_rango_invertido(self):
        """Rechaza umbrales cuyo mínimo supera al máximo."""
        with self.assertRaisesRegex(ValueError, "Rango invertido"):
            validar_umbrales([{"min": 10, "max": 5}], "prueba")

    def test_cargar_proyectos_rechaza_codigos_duplicados(self):
        libro = Workbook()
        hoja = libro.active
        hoja.title = "Datos_Proyectos"
        hoja.append(["Código", "Nombre Proyecto", "Ruta Código", "Herramienta IA", "Modelo IA", "Lenguaje"])
        hoja.append(["M1", "Prueba", "codigo", "IA", "Modelo", "Python"])
        hoja.append(["M1", "Prueba", "codigo", "IA", "Modelo", "Python"])
        with self.assertRaisesRegex(ValueError, "duplicado"):
            LectorProyectosExcel().cargar(libro)
        libro.close()

    def test_validar_coeficiente_penalizacion(self):
        """Acepta cero y positivos, pero rechaza valores inválidos."""
        validar_coeficiente_penalizacion(0)
        validar_coeficiente_penalizacion(0.1)
        for valor in (-0.1, "0.1", True, float("inf")):
            with self.assertRaises(ValueError):
                validar_coeficiente_penalizacion(valor)




if __name__ == "__main__":
    unittest.main()
