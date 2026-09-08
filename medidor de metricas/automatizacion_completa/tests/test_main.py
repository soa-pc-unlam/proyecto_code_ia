"""Pruebas de la orquestación concurrente."""

import threading
import time
import unittest
from unittest.mock import Mock, patch

from procesamiento.procesamiento import (
    gestionar_procesamiento_proyectos,
)


class ProcesamientoConcurrenteTest(unittest.TestCase):
    """Verifica el procesamiento concurrente de los proyectos."""

    @patch(
        "procesamiento.procesamiento.guardar_resultado_proyecto"
    )
    @patch(
        "procesamiento.procesamiento.procesar_proyecto"
    )
    def test_proyectos_se_procesan_concurrentemente(
        self,
        procesar_proyecto,
        guardar_resultado,
    ):
        """Verifica que dos proyectos se procesan simultáneamente y se guardan.

        Args:
            procesar_proyecto: Mock del analizador inyectado por patch.
            guardar_resultado: Mock de la escritura de resultados inyectado por patch.
        """
        barrera = threading.Barrier(2)

        proyecto_1 = Mock(codigo="M1")
        proyecto_2 = Mock(codigo="M2")

        def procesar(*args):
            """Simula un análisis que espera a que otro proyecto alcance la barrera.

            Args:
                *args: Argumentos del analizador; el primero es el proyecto.

            Returns:
                Mock: Resultado asociado con el proyecto recibido.

            Raises:
                threading.BrokenBarrierError: Si el segundo proyecto no llega a tiempo.
            """
            proyecto = args[0]
            barrera.wait(timeout=2)
            time.sleep(0.01)
            resultado = Mock()
            resultado.proyecto = proyecto
            return resultado

        procesar_proyecto.side_effect = procesar

        gestionar_procesamiento_proyectos(
            proyectos=[proyecto_1, proyecto_2],
            configuracion={},
            libro_salida=Mock(),
            libro_entrada=Mock(),
            logger=Mock(),
        )

        self.assertEqual(procesar_proyecto.call_count, 2)
        self.assertEqual(guardar_resultado.call_count, 2)


if __name__ == "__main__":
    unittest.main()