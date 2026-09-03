"""Pruebas de la evaluación de métricas de concurrencia."""

import unittest

from metricas.concurrencia import calcular_promedio, obtener_puntaje


class ConcurrenciaTest(unittest.TestCase):
    """Verifica los cálculos y validaciones de concurrencia."""

    def test_calcular_promedio(self):
        """Calcula el promedio y contempla una colección vacía."""
        self.assertEqual(calcular_promedio([3, 2, 3, 1]), 2.25)
        self.assertEqual(calcular_promedio([]), 0)

    def test_obtener_puntaje_normaliza_texto(self):
        """Normaliza niveles válidos y rechaza valores desconocidos."""
        self.assertEqual(obtener_puntaje(" alta ", {"Alta": 3}), 3)
        with self.assertRaises(ValueError):
            obtener_puntaje("desconocido", {"Alta": 3})


if __name__ == "__main__":
    unittest.main()
