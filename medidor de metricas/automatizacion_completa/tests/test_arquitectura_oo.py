"""Pruebas para la nueva coordinación orientada a objetos."""
import tempfile
import unittest
from pathlib import Path
from threading import Semaphore
from unittest.mock import Mock, patch

from reportes.escritor_resultados import EscritorResultadosExcel
from modelos.modelos import Proyecto
from procesamiento.procesamiento import AnalizadorProyecto, GestorProcesamiento


class RefactorizacionTest(unittest.TestCase):
    def test_analizador_conserva_seis_etapas(self):
        proyecto = Proyecto('P1', 'Ejemplo', '/tmp', '', '', 'Java')
        pasos = (
            'analizar_complejidad', 'analizar_mi', 'analizar_bugs_smells_seguro',
            'analizar_concurrencia_seguro', 'analizar_tokens_seguro',
            'analizar_cpu_memoria_seguro',
        )
        m = {x: Mock() for x in pasos}
        with patch.multiple('procesamiento.procesamiento', **m):
            resultado = AnalizadorProyecto({}, Mock(), Mock(), Semaphore(1)).analizar(proyecto)
        self.assertEqual(resultado.errores, [])
        for nombre in pasos:
            m[nombre].assert_called_once()

    def test_wiring_sin_fuentes_omite_todas_las_metricas(self):
        with tempfile.TemporaryDirectory() as temporal:
            proyecto = Proyecto('P2', 'Sin Wiring', temporal, '', '', 'Wiring')
            with patch('procesamiento.procesamiento.analizar_complejidad') as analizar:
                resultado = AnalizadorProyecto({}, Mock(), Mock(), Semaphore(1)).analizar(proyecto)
            analizar.assert_not_called()
            self.assertEqual(len(resultado.errores), 1)
            self.assertIsNone(resultado.metricas_cpu_memoria)
            self.assertIsNone(resultado.metricas_tokens)

    def test_fallo_de_un_proyecto_no_detiene_los_otros(self):
        proyectos = [Mock(codigo='P1'), Mock(codigo='P2')]
        configuracion, salida, entrada, logger = {}, Mock(), Mock(), Mock()
        with patch('procesamiento.procesamiento.procesar_proyecto', side_effect=[
            RuntimeError('proyecto inválido'), Mock(proyecto=proyectos[1])
        ]), patch('procesamiento.procesamiento.guardar_resultado_proyecto') as guardar, \
             patch.object(EscritorResultadosExcel, 'guardar_error') as error:
            # Los Future se esperan en el orden de entrega, conservado por la implementación.
            GestorProcesamiento(configuracion, salida, entrada, logger).ejecutar(proyectos)
        guardar.assert_called_once()
        error.assert_called_once()


if __name__ == '__main__':
    unittest.main()
