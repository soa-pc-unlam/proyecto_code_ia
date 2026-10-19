"""Pruebas de formato de columnas y gráficos por perfil de reporte."""

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from constantes import definiciones as d
from reportes.excel import (
    crear_hojas_si_no_existen,
    crear_o_abrir_excel_salida,
    finalizar_libro,
    formatear_celdas_hoja_resumen,
    obtener_mapa_encabezados,
    resolver_columnas_graficos,
)


class FormatosGraficosPerfilesTest(unittest.TestCase):
    """Comprueba el funcionamiento conjunto del formateador y el generador."""

    @staticmethod
    def crear_libro(ruta, perfil):
        libro = crear_o_abrir_excel_salida(ruta, hojas_reporte=perfil)
        libro[d.HOJA_RESUMEN].append(["P1"])
        libro[d.HOJA_COMPLEJIDAD].append(["P1"])
        return libro

    def test_pc_conserva_formato_original_por_columna(self):
        """Los encabezados adicionales no alteran los colores de PC."""
        with TemporaryDirectory() as temporal:
            libro = self.crear_libro(Path(temporal) / "pc.xlsx", d.HOJAS_REPORTE)
            try:
                hoja = libro[d.HOJA_RESUMEN]
                formatear_celdas_hoja_resumen(hoja, "P1")
                colores = [hoja.cell(2, i).fill.fgColor.rgb[-6:] for i in range(1, 21)]
                self.assertEqual(colores[0], d.COLOR_CODIGO_RESUMEN)
                self.assertEqual(colores[1:5], [d.COLOR_DATOS_PROYECTO_RESUMEN] * 4)
                for columna in (7, 9, 12, 14, 16, 18, 20):
                    self.assertEqual(colores[columna - 1], d.COLOR_INTERPRETACION_RESUMEN)
                self.assertEqual(hoja.cell(2, 17).number_format, r'0.00\%')
                self.assertEqual(hoja.cell(2, 19).number_format, r'0.00\%')
            finally:
                libro.close()

    def test_dispositivos_formatean_identificacion_y_metricas_por_encabezado(self):
        """Los campos insertados en Resumen tienen formato correcto."""
        with TemporaryDirectory() as temporal:
            libro = self.crear_libro(
                Path(temporal) / "dispositivos.xlsx", d.HOJAS_REPORTE_DISPOSITIVOS
            )
            try:
                hoja = libro[d.HOJA_RESUMEN]
                formatear_celdas_hoja_resumen(hoja, "P1")
                columnas = obtener_mapa_encabezados(hoja)
                for encabezado in d.ENCABEZADOS_DATOS_PROYECTO_RESUMEN:
                    self.assertEqual(
                        hoja.cell(2, columnas[encabezado]).fill.fgColor.rgb[-6:],
                        d.COLOR_DATOS_PROYECTO_RESUMEN,
                    )
                for encabezado in d.ENCABEZADOS_INTERPRETACION_RESUMEN:
                    self.assertEqual(
                        hoja.cell(2, columnas[encabezado]).fill.fgColor.rgb[-6:],
                        d.COLOR_INTERPRETACION_RESUMEN,
                    )
                for encabezado in (
                    d.TEXTO_ENCABEZADO_CPU_PROMEDIO,
                    d.TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN,
                ):
                    self.assertEqual(
                        hoja.cell(2, columnas[encabezado]).number_format, r'0.00\%'
                    )
            finally:
                libro.close()

    def test_graficos_pc_mantienen_columnas_y_ubicaciones_originales(self):
        """Los índices históricos de PC siguen generando las ocho series."""
        with TemporaryDirectory() as temporal:
            ruta = Path(temporal) / "pc.xlsx"
            libro = self.crear_libro(ruta, d.HOJAS_REPORTE)
            try:
                finalizar_libro(libro, ruta, incluir_graficos=True)
                graficos = libro["Graficos"]._charts
                self.assertEqual(len(graficos), len(d.CONFIGURACION_GRAFICOS))
                self.assertEqual(graficos[0].series[0].val.numRef.f, "'Resumen'!$F$2")
                self.assertEqual(graficos[1].series[0].val.numRef.f, "'Complejidad'!$E$2")
            finally:
                libro.close()

    def test_graficos_dispositivos_referencian_metricas_no_metadatos(self):
        """Los gráficos usan la posición real desplazada por columnas extras."""
        with TemporaryDirectory() as temporal:
            ruta = Path(temporal) / "dispositivos.xlsx"
            libro = self.crear_libro(ruta, d.HOJAS_REPORTE_DISPOSITIVOS)
            try:
                finalizar_libro(
                    libro, ruta, incluir_graficos=True,
                    configuracion_graficos=d.CONFIGURACION_GRAFICOS_DISPOSITIVOS,
                )
                graficos = libro["Graficos"]._charts
                self.assertEqual(len(graficos), len(d.CONFIGURACION_GRAFICOS))
                self.assertEqual(graficos[0].series[0].val.numRef.f, "'Resumen'!$I$2")
                self.assertEqual(graficos[1].series[0].val.numRef.f, "'Complejidad'!$E$2")
                self.assertEqual(graficos[6].series[0].val.numRef.f, "'Resumen'!$T$2")
                self.assertEqual(graficos[7].series[0].val.numRef.f, "'Resumen'!$V$2")
            finally:
                libro.close()

    def test_graficos_en_archivos_previos_resuelven_columnas_reales(self):
        """Los libros antiguos pueden tener nuevos encabezados al final."""
        with TemporaryDirectory() as temporal:
            libro = self.crear_libro(Path(temporal) / "antiguo.xlsx", d.HOJAS_REPORTE)
            try:
                crear_hojas_si_no_existen(libro, d.HOJAS_REPORTE_DISPOSITIVOS)
                configuracion = resolver_columnas_graficos(
                    libro, d.CONFIGURACION_GRAFICOS_DISPOSITIVOS
                )
                self.assertEqual(configuracion[0][1], 6)
                self.assertEqual(configuracion[6][1], 17)
            finally:
                libro.close()

    def test_encabezado_de_grafico_ausente_se_informa(self):
        """No crea silenciosamente un gráfico basado en otra métrica."""
        with TemporaryDirectory() as temporal:
            libro = self.crear_libro(
                Path(temporal) / "dispositivos.xlsx", d.HOJAS_REPORTE_DISPOSITIVOS
            )
            try:
                with self.assertRaisesRegex(ValueError, "Falta la columna 'Inexistente'"):
                    resolver_columnas_graficos(
                        libro, [(d.HOJA_RESUMEN, "Inexistente", "", "", "A1")]
                    )
            finally:
                libro.close()

    def test_graficos_se_guardan_y_regeneran_sin_duplicarse(self):
        """Guardar, reabrir y regenerar conserva una sola hoja de gráficos."""
        with TemporaryDirectory() as temporal:
            ruta = Path(temporal) / "reporte.xlsx"
            libro = self.crear_libro(ruta, d.HOJAS_REPORTE_DISPOSITIVOS)
            finalizar_libro(
                libro, ruta, incluir_graficos=True,
                configuracion_graficos=d.CONFIGURACION_GRAFICOS_DISPOSITIVOS,
            )
            libro.close()

            libro = crear_o_abrir_excel_salida(ruta, d.HOJAS_REPORTE_DISPOSITIVOS)
            try:
                self.assertEqual(len(libro["Graficos"]._charts), 8)
                finalizar_libro(
                    libro, ruta, incluir_graficos=True,
                    configuracion_graficos=d.CONFIGURACION_GRAFICOS_DISPOSITIVOS,
                )
                self.assertEqual(libro.sheetnames.count("Graficos"), 1)
                self.assertEqual(len(libro["Graficos"]._charts), 8)
            finally:
                libro.close()


if __name__ == "__main__":
    unittest.main()
