"""Pruebas de la configuración de los perfiles de reporte."""

import unittest

from constantes import definiciones as d


class TestPerfilesReporte(unittest.TestCase):
    """Comprueba las hojas de PC y Android/ESP32 sin generar Excel."""

    def test_perfil_pc_conserva_las_hojas_originales(self):
        self.assertIs(d.PERFILES_REPORTE[d.TIPO_PROYECTO_PC], d.HOJAS_REPORTE)
        self.assertEqual(len(d.HOJAS_REPORTE), 8)
        self.assertIn(d.HOJA_CPU_MEMORIA_SALIDA, d.HOJAS_REPORTE)
        self.assertEqual(d.HOJA_CPU_MEMORIA_SALIDA, "CPU-Memoria")
        self.assertEqual(len(d.ENCABEZADOS_RESUMEN), 20)

    def test_perfil_dispositivos_comparte_hoja_cpu_memoria(self):
        self.assertIs(
            d.PERFILES_REPORTE[d.TIPO_PROYECTO_ESP32_ANDROID],
            d.HOJAS_REPORTE_DISPOSITIVOS,
        )
        self.assertIn(d.HOJA_CPU_MEMORIA_SALIDA, d.HOJAS_REPORTE_DISPOSITIVOS)
        self.assertIs(
            d.HOJAS_REPORTE_DISPOSITIVOS[d.HOJA_CPU_MEMORIA_SALIDA],
            d.HOJAS_REPORTE[d.HOJA_CPU_MEMORIA_SALIDA],
        )
        self.assertEqual(
            d.ENCABEZADOS_RESUMEN_DISPOSITIVOS[2:5],
            [d.TEXTO_ENCABEZADO_ID_PROYECTO,
             d.TEXTO_ENCABEZADO_DISPOSITIVO,
             d.TEXTO_ENCABEZADO_IMPLEMENTACION],
        )

    def test_encabezados_comunes_se_reutilizan(self):
        for hoja in (
            d.HOJA_COMPLEJIDAD,
            d.HOJA_MANTENIBILIDAD,
            d.HOJA_BUGS_SMELLS,
            d.HOJA_ERRORES,
            d.HOJA_CONCURRENCIA_SALIDA,
            d.HOJA_TOKENS_SALIDA,
        ):
            with self.subTest(hoja=hoja):
                self.assertIs(d.HOJAS_REPORTE[hoja], d.HOJAS_REPORTE_DISPOSITIVOS[hoja])

    def test_cpu_memoria_usa_exactamente_encabezados_de_pc(self):
        self.assertIs(
            d.HOJAS_REPORTE_DISPOSITIVOS[d.HOJA_CPU_MEMORIA_SALIDA],
            d.HOJAS_REPORTE[d.HOJA_CPU_MEMORIA_SALIDA],
        )
        self.assertEqual(len(d.ENCABEZADOS_CPU_MEMORIA_SALIDA), 23)

    def test_encabezados_especificos_no_se_comparten(self):
        self.assertIsNot(d.ENCABEZADOS_RESUMEN, d.ENCABEZADOS_RESUMEN_DISPOSITIVOS)
        self.assertEqual(len(d.ENCABEZADOS_RESUMEN_DISPOSITIVOS), 23)
        self.assertEqual(
            len(d.ENCABEZADOS_CPU_MEMORIA_SALIDA),
            len(set(d.ENCABEZADOS_CPU_MEMORIA_SALIDA)),
        )


if __name__ == "__main__":
    unittest.main()
