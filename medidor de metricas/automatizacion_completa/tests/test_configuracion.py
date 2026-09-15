"""Pruebas de carga, clasificación y validación de la configuración."""

import json
import tempfile
import unittest
from pathlib import Path

from configuracion.configuracion import (
    cargar_proyectos,
    clasificar_por_umbrales,
    validar_coeficiente_penalizacion,
    validar_configuracion_cpu_memoria,
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
        self.assertEqual(clasificar_por_umbrales(1, umbrales), ("Bajo", "A"))
        self.assertEqual(clasificar_por_umbrales(1.01, umbrales), ("Alto", "B"))

    def test_validar_umbrales_rechaza_rango_invertido(self):
        """Rechaza umbrales cuyo mínimo supera al máximo."""
        with self.assertRaisesRegex(ValueError, "Rango invertido"):
            validar_umbrales([{"min": 10, "max": 5}], "prueba")

    def test_cargar_proyectos_rechaza_codigos_duplicados(self):
        """Rechaza archivos con códigos de proyecto duplicados."""
        proyecto = {"codigo": "M1", "nombre_proyecto": "Prueba", "ruta_codigo": "codigo", "herramienta_ia": "IA", "modelo_ia": "Modelo", "lenguaje": "Python"}
        with tempfile.TemporaryDirectory() as directorio:
            ruta = Path(directorio) / "proyectos.json"
            ruta.write_text(json.dumps([proyecto, proyecto]), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicado"):
                cargar_proyectos(ruta)

    def test_validar_coeficiente_penalizacion(self):
        """Acepta cero y positivos, pero rechaza valores inválidos."""
        validar_coeficiente_penalizacion(0)
        validar_coeficiente_penalizacion(0.1)
        for valor in (-0.1, "0.1", True, float("inf")):
            with self.assertRaises(ValueError):
                validar_coeficiente_penalizacion(valor)


    def test_validar_configuracion_cpu_memoria(self):
        """Valida rango, tipos y orden de los umbrales de CPU/memoria."""
        validar_configuracion_cpu_memoria({"umbral_bajo": 25, "umbral_medio": 60})
        casos_invalidos = [
            {"umbral_bajo": 60, "umbral_medio": 25},
            {"umbral_bajo": -1, "umbral_medio": 60},
            {"umbral_bajo": 25, "umbral_medio": 101},
            {"umbral_bajo": "25", "umbral_medio": 60},
        ]
        for caso in casos_invalidos:
            with self.assertRaises(ValueError):
                validar_configuracion_cpu_memoria(caso)


if __name__ == "__main__":
    unittest.main()
