"""Pruebas de la orquestacion concurrente."""

import threading
import time
import unittest
from unittest.mock import patch

from main import procesar_todos_proyectos


class ProcesamientoConcurrenteTest(unittest.TestCase):
    """Verifica el paralelismo y la finalizacion segura del reporte."""

    def test_proyectos_se_procesan_concurrentemente_antes_de_guardar(self):
        barrera = threading.Barrier(2)
        terminados = []

        def procesar(proyecto, configuracion, logger, libro, bloqueo_libro):
            barrera.wait(timeout=1)
            time.sleep(0.01)
            terminados.append(proyecto)

        with patch("main.procesar_proyecto", side_effect=procesar), patch(
            "main.finalizar_libro"
        ) as finalizar:
            procesar_todos_proyectos(
                ["M1", "M2"],
                {"max_workers": 2, "archivo_excel": "reporte.xlsx"},
                logger=None,
                libro=object(),
            )

        self.assertCountEqual(terminados, ["M1", "M2"])
        finalizar.assert_called_once()


if __name__ == "__main__":
    unittest.main()
