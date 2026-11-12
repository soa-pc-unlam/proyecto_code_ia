"""Comprueba la reutilización de fuentes Wiring entre analizadores."""

import tempfile
import unittest
from pathlib import Path
from threading import Semaphore
from unittest.mock import Mock, patch

from modelos.modelos import Proyecto
from procesamiento.procesamiento import AnalizadorProyecto
from util.fuentes_wiring import preparar_fuentes_wiring, seleccionar_fuentes_wiring


class TestFuentesUnaVez(unittest.TestCase):
    def test_validacion_y_ambos_analizadores_no_reseleccionan(self):
        with tempfile.TemporaryDirectory() as directorio:
            raiz = Path(directorio)
            (raiz / "programa.ino").write_text(
                "void setup() {}\nvoid loop() {}\n", encoding="utf-8"
            )
            proyecto = Proyecto("P1", "Ejemplo", str(raiz), "", "", "Wiring")
            logger = Mock()
            pipeline = Mock()

            def ejecutar(estado, semaforo):
                self.assertIsNotNone(estado.fuentes_wiring)
                self.assertEqual(len(estado.fuentes_wiring.archivos), 1)
                # Cada analizador prepara una copia privada con la selección inyectada.
                for _ in range(2):
                    with preparar_fuentes_wiring(
                        proyecto.ruta_codigo, estado.fuentes_wiring
                    ) as (temporal, originales):
                        self.assertEqual(len(originales), 1)
                        self.assertTrue(next(iter(originales)).is_file())

            pipeline.ejecutar.side_effect = ejecutar
            with patch(
                "util.fuentes_wiring.seleccionar_fuentes_wiring",
                wraps=seleccionar_fuentes_wiring,
            ) as seleccion:
                resultado = AnalizadorProyecto(
                    {}, logger, None, Semaphore(1), pipeline=pipeline
                ).analizar(proyecto)
            self.assertEqual(resultado.errores, [])
            self.assertEqual(seleccion.call_count, 1)
            pipeline.ejecutar.assert_called_once()

    def test_no_se_aceptan_fuentes_de_otro_proyecto(self):
        from util.fuentes_wiring import obtener_fuentes_proyecto
        with tempfile.TemporaryDirectory() as primero, tempfile.TemporaryDirectory() as segundo:
            Path(primero, "archivo.ino").write_text("void setup() {}")
            fuentes = obtener_fuentes_proyecto(primero)
            with self.assertRaisesRegex(ValueError, "no pertenecen"):
                with preparar_fuentes_wiring(segundo, fuentes):
                    pass


if __name__ == "__main__":
    unittest.main()
