"""Pruebas de escritura de resultados en reportes Excel."""

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from openpyxl import Workbook

from reportes.excel import (
    crear_hojas_si_no_existen,
    crear_o_abrir_excel_salida,
    escribir_hoja_tokens,
    guardar_error_excel,
)
from modelos.modelos import MetricaTokens, Proyecto
from constantes import definiciones as d


class ReportesTest(unittest.TestCase):
    """Verifica la creación y actualización de los reportes."""

    def test_nuevo_excel_pc_usa_hojas_originales(self):
        """El perfil de PC sigue siendo el predeterminado del generador."""
        with TemporaryDirectory() as temporal:
            ruta = Path(temporal) / "pc.xlsx"
            libro = crear_o_abrir_excel_salida(ruta)
            try:
                self.assertEqual(libro.sheetnames, list(d.HOJAS_REPORTE))
                for nombre, encabezados in d.HOJAS_REPORTE.items():
                    self.assertEqual(
                        [celda.value for celda in libro[nombre][1]], encabezados
                    )
            finally:
                libro.close()
            self.assertFalse(ruta.exists())  # No se guarda antes de finalizar.

    def test_nuevo_excel_dispositivos_usa_sus_hojas(self):
        """Las hojas nuevas respetan exactamente el perfil Android/ESP32."""
        with TemporaryDirectory() as temporal:
            ruta = Path(temporal) / "dispositivos.xlsx"
            libro = crear_o_abrir_excel_salida(
                ruta, hojas_reporte=d.HOJAS_REPORTE_DISPOSITIVOS
            )
            try:
                self.assertEqual(libro.sheetnames, list(d.HOJAS_REPORTE_DISPOSITIVOS))
                self.assertIn(d.HOJA_CPU_MEMORIA_SALIDA, libro.sheetnames)
                for nombre, encabezados in d.HOJAS_REPORTE_DISPOSITIVOS.items():
                    self.assertEqual(
                        [celda.value for celda in libro[nombre][1]], encabezados
                    )
            finally:
                libro.close()

    def test_reabrir_excel_dispositivos_preserva_datos(self):
        """Reutiliza el archivo existente sin duplicar hojas ni filas."""
        with TemporaryDirectory() as temporal:
            ruta = Path(temporal) / "dispositivos.xlsx"
            libro = crear_o_abrir_excel_salida(
                ruta, hojas_reporte=d.HOJAS_REPORTE_DISPOSITIVOS
            )
            libro[d.HOJA_COMPLEJIDAD].append(["AND-M", 2])
            libro.save(ruta)
            libro.close()
            libro = crear_o_abrir_excel_salida(
                ruta, hojas_reporte=d.HOJAS_REPORTE_DISPOSITIVOS
            )
            try:
                self.assertEqual(libro.sheetnames, list(d.HOJAS_REPORTE_DISPOSITIVOS))
                self.assertEqual(libro[d.HOJA_COMPLEJIDAD].cell(2, 1).value, "AND-M")
                self.assertEqual(libro[d.HOJA_COMPLEJIDAD].max_row, 2)
            finally:
                libro.close()

    def test_migracion_nombre_hoja_cpu_memoria_de_ambos_perfiles(self):
        """Conserva filas y encabezados al abrir un Excel generado anteriormente."""
        from openpyxl import Workbook
        for antiguo in ("CPU-Memoria PC", "CPU-Memoria Dispositivos"):
            with self.subTest(nombre_anterior=antiguo):
                libro = Workbook()
                hoja = libro.active
                hoja.title = antiguo
                hoja.append(d.ENCABEZADOS_CPU_MEMORIA_SALIDA)
                hoja.append(["M1", "Java", "Manual"])
                crear_hojas_si_no_existen(libro, d.HOJAS_REPORTE_DISPOSITIVOS)
                self.assertNotIn(antiguo, libro.sheetnames)
                self.assertIn(d.HOJA_CPU_MEMORIA_SALIDA, libro.sheetnames)
                cpu = libro[d.HOJA_CPU_MEMORIA_SALIDA]
                self.assertEqual(cpu.cell(2, 1).value, "M1")
                self.assertEqual(
                    [celda.value for celda in cpu[1]],
                    d.ENCABEZADOS_CPU_MEMORIA_SALIDA,
                )
                libro.close()

    def test_no_elimina_hojas_cpu_memoria_si_hay_datos_en_dos_hojas(self):
        """Una colisión de nombres antiguos no debe borrar mediciones."""
        libro = Workbook()
        libro.active.title = "CPU-Memoria PC"
        libro.create_sheet("CPU-Memoria Dispositivos")
        with self.assertRaises(ValueError):
            crear_hojas_si_no_existen(libro, d.HOJAS_REPORTE_DISPOSITIVOS)
        self.assertIn("CPU-Memoria PC", libro.sheetnames)
        self.assertIn("CPU-Memoria Dispositivos", libro.sheetnames)
        libro.close()

    def test_los_errores_del_mismo_proyecto_no_se_sobrescriben(self):
        """Conserva todos los errores registrados para un mismo proyecto."""
        libro = Workbook()
        libro.remove(libro.active)
        crear_hojas_si_no_existen(libro)
        proyecto = Proyecto("M1", "Prueba", "ruta", "IA", "Modelo", "Python")
        guardar_error_excel(libro, proyecto, "Primer error")
        guardar_error_excel(libro, proyecto, "Segundo error")
        hoja = libro["Errores"]
        self.assertEqual(hoja.max_row, 3)
        self.assertEqual(hoja.cell(2, 3).value, "Primer error")
        self.assertEqual(hoja.cell(3, 3).value, "Segundo error")

    def test_tokens_actualiza_sin_duplicar(self):
        """Actualiza la fila de tokens correspondiente al mismo código."""
        libro = Workbook()
        libro.remove(libro.active)
        crear_hojas_si_no_existen(libro)
        metrica = MetricaTokens("M1", "Chat", 100, 1, 2000, 50, 45.45,
                                "Alto", "Interpretación")
        escribir_hoja_tokens(libro["Tokens"], "M1", metrica)
        metrica.refinamientos = 2
        escribir_hoja_tokens(libro["Tokens"], "M1", metrica)
        hoja = libro["Tokens"]
        self.assertEqual(hoja.max_row, 2)
        self.assertEqual(hoja.cell(2, 4).value, 2)

    def test_tokens_no_escribe_resultado_parcial(self):
        """No crea filas de datos cuando la métrica no está disponible."""
        libro = Workbook()
        libro.remove(libro.active)
        crear_hojas_si_no_existen(libro)
        escribir_hoja_tokens(libro["Tokens"], "M1", None)
        self.assertEqual(libro["Tokens"].max_row, 2)


if __name__ == "__main__":
    unittest.main()
