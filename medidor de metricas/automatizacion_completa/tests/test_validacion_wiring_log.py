"""La ausencia de fuentes debe registrarse sin traceback ni ejecución de métricas."""
import tempfile
import unittest
from threading import Semaphore
from unittest.mock import Mock
from modelos.modelos import Proyecto
from procesamiento.procesamiento import AnalizadorProyecto

class TestValidacionWiringLog(unittest.TestCase):
    def test_sin_wiring_es_advertencia_y_no_traceback(self):
        with tempfile.TemporaryDirectory() as ruta:
            proyecto = Proyecto("SIN-INO", "Proyecto sin fuente", ruta, "", "", "Wiring")
            logger = Mock()
            pipeline = Mock()
            resultado = AnalizadorProyecto({}, logger, Mock(), Semaphore(1), pipeline=pipeline).analizar(proyecto)
            pipeline.ejecutar.assert_not_called()
            logger.exception.assert_not_called()
            logger.error.assert_not_called()
            logger.warning.assert_any_call(unittest.mock.ANY)
            self.assertEqual(len(resultado.errores), 1)
            self.assertIn("No hay fuentes Wiring", resultado.errores[0])
            self.assertTrue(all(v is None for v in (
                resultado.metricas_cc, resultado.metricas_mi, resultado.metricas_bugs_smells,
                resultado.metricas_concurrencia, resultado.metricas_tokens, resultado.metricas_cpu_memoria)))
