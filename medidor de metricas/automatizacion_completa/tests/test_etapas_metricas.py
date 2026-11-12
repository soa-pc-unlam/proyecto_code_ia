"""Pruebas independientes del pipeline y sus estrategias."""
import tempfile
import unittest
from threading import Semaphore
from unittest.mock import Mock

from procesamiento.etapas import (
    EstadoPipeline, EtapaMetrica, PipelineMetricas, ValidadorFuentesProyecto,
)
from procesamiento.procesamiento import FabricaEtapasMetricas, AnalizadorProyecto
from modelos.modelos import Proyecto


class PipelineMetricasTest(unittest.TestCase):
    def crear_estado(self):
        return EstadoPipeline(Mock(), "Java", {}, Mock(), Mock(), Mock())

    def test_orden_y_dependencia_de_resultados(self):
        llamadas = []

        def primera(e):
            llamadas.append("cc")
            return 10

        def segunda(e):
            llamadas.append("tokens")
            return e.valores["metricas_cc"] * 2

        pipeline = PipelineMetricas([
            EtapaMetrica("metricas_cc", primera, True),
            EtapaMetrica("metricas_tokens", segunda),
        ])
        estado = self.crear_estado()
        pipeline.ejecutar(estado, Semaphore(1))
        self.assertEqual(llamadas, ["cc", "tokens"])
        self.assertEqual(estado.valores["metricas_tokens"], 20)

    def test_estado_no_se_comparte(self):
        uno, dos = self.crear_estado(), self.crear_estado()
        uno.valores["metricas_cc"] = 5
        self.assertIsNone(dos.valores["metricas_cc"])

    def test_etapas_duplicadas_se_rechazan(self):
        etapa = EtapaMetrica("metricas_cc", lambda e: None)
        with self.assertRaises(ValueError):
            PipelineMetricas([etapa, etapa])

    def test_fabrica_conserva_seis_metricas_y_dos_pesadas(self):
        etapas = FabricaEtapasMetricas.crear().etapas
        self.assertEqual(len(etapas), 6)
        self.assertEqual(
            [e.nombre for e in etapas if e.requiere_semaforo],
            ["metricas_cc", "metricas_bugs_smells"],
        )

    def test_inyeccion_pipeline_y_validador(self):
        proyecto = Proyecto("P", "Prueba", "/tmp", "", "", "Java")
        pipeline, validador = Mock(), Mock()
        resultado = AnalizadorProyecto(
            {}, Mock(), Mock(), Semaphore(1), pipeline=pipeline, validador=validador
        ).analizar(proyecto)
        pipeline.ejecutar.assert_called_once()
        validador.validar.assert_called_once_with(proyecto, "java")
        self.assertIsNone(resultado.metricas_cc)

    def test_validador_no_toca_otros_lenguajes(self):
        ValidadorFuentesProyecto().validar(Mock(ruta_codigo="no-existe"), "Java")

    def test_wiring_ausente_falla_antes_del_pipeline(self):
        with tempfile.TemporaryDirectory() as ruta:
            proyecto = Proyecto("W", "Wiring", ruta, "", "", "Wiring")
            pipeline = Mock()
            resultado = AnalizadorProyecto(
                {}, Mock(), Mock(), Semaphore(1), pipeline=pipeline
            ).analizar(proyecto)
            pipeline.ejecutar.assert_not_called()
            self.assertTrue(resultado.errores)


if __name__ == '__main__':
    unittest.main()
