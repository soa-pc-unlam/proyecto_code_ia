from reportes.escritor_resultados import EscritorResultadosExcel
from reportes.gestor_libros import GestorLibrosExcel
"""Pruebas de metadatos opcionales y su escritura en perfiles de dispositivos."""

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from configuracion.lector_proyectos_excel import LectorProyectosExcel
from openpyxl import Workbook, load_workbook
from constantes import definiciones as d
from modelos.modelos import Proyecto


class MetadatosProyectosTest(unittest.TestCase):
    """Verifica que los metadatos lleguen del Excel de entrada al reporte."""

    @staticmethod
    def datos_base(codigo="L1-AND-M"):
        return {
            "codigo": codigo,
            "nombre_proyecto": "Smart Mirror",
            "ruta_codigo": "ruta",
            "herramienta_ia": "No aplica",
            "modelo_ia": "No aplica",
            "lenguaje": "Java",
        }

    @staticmethod
    def cargar_temporal(datos, directorio):
        libro = Workbook()
        hoja = libro.active
        hoja.title = "Datos_Proyectos"
        campos = ["codigo", "nombre_proyecto", "ruta_codigo", "herramienta_ia", "modelo_ia", "lenguaje", "id_proyecto", "dispositivo", "implementacion"]
        titulos = ["Código", "Nombre Proyecto", "Ruta Código", "Herramienta IA", "Modelo IA", "Lenguaje", "ID Proyecto", "Dispositivo", "Implementación"]
        hoja.append(titulos)
        for item in datos:
            hoja.append([item.get(campo) for campo in campos])
        try:
            return LectorProyectosExcel().cargar(libro)
        finally:
            libro.close()

    @staticmethod
    def datos_hoja(hoja, fila):
        return {
            celda.value: hoja.cell(fila, celda.column).value
            for celda in hoja[1]
        }

    def test_carga_tres_metadatos_desde_excel(self):
        datos = self.datos_base()
        datos.update(
            id_proyecto="L1", dispositivo="Android", implementacion="Manual"
        )
        with TemporaryDirectory() as directorio:
            proyecto = self.cargar_temporal([datos], directorio)[0]
        self.assertEqual(proyecto.id_proyecto, "L1")
        self.assertEqual(proyecto.dispositivo, "Android")
        self.assertEqual(proyecto.implementacion, "Manual")

    def test_excel_sin_metadatos_conserva_compatibilidad_pc(self):
        with TemporaryDirectory() as directorio:
            proyecto = self.cargar_temporal([self.datos_base("M1")], directorio)[0]
        self.assertEqual(proyecto.id_proyecto, "")
        self.assertEqual(proyecto.dispositivo, "")
        self.assertEqual(proyecto.implementacion, "")
        self.assertEqual(proyecto.codigo, "M1")

    def test_constructor_posicional_original_no_cambia(self):
        proyecto = Proyecto("M1", "Nombre", "ruta", "IA", "Modelo", "Python")
        self.assertEqual(proyecto.lenguaje, "Python")
        self.assertEqual(proyecto.dispositivo, "")

    def test_excel_real_dispositivos_conserva_metadatos(self):
        ruta = Path(__file__).resolve().parents[1] / "datos_entrada/datos_entrada_esp32_android.xlsx"
        libro = load_workbook(ruta, read_only=True, data_only=True)
        try:
            proyectos = LectorProyectosExcel().cargar(libro)
        finally:
            libro.close()
        self.assertTrue(proyectos)
        self.assertTrue(all(proyecto.id_proyecto for proyecto in proyectos))
        self.assertTrue(all(proyecto.dispositivo for proyecto in proyectos))
        self.assertTrue(all(proyecto.implementacion for proyecto in proyectos))

    def test_carga_y_escritura_dispositivos_distingue_implementaciones(self):
        manual = self.datos_base("L1-AND-M")
        manual.update(
            id_proyecto="L1", dispositivo="Android", implementacion="Manual"
        )
        vibecoding = self.datos_base("L1-AND-V")
        vibecoding.update(
            id_proyecto="L1", dispositivo="Android",
            implementacion="Vibecoding", herramienta_ia="Gemini",
        )
        with TemporaryDirectory() as directorio:
            proyectos = self.cargar_temporal([manual, vibecoding], directorio)
            libro = GestorLibrosExcel().crear_o_abrir_salida(
                Path(directorio) / "dispositivos.xlsx", d.HOJAS_REPORTE_DISPOSITIVOS
            )
            try:
                for proyecto in proyectos:
                    EscritorResultadosExcel().guardar_resultado(libro, proyecto, None, None)
                hoja = libro[d.HOJA_RESUMEN]
                self.assertEqual(hoja.max_row, 3)
                fila_manual = self.datos_hoja(hoja, 2)
                fila_ia = self.datos_hoja(hoja, 3)
                self.assertEqual(fila_manual[d.TEXTO_ENCABEZADO_ID_PROYECTO], "L1")
                self.assertEqual(fila_ia[d.TEXTO_ENCABEZADO_ID_PROYECTO], "L1")
                self.assertEqual(fila_manual[d.TEXTO_ENCABEZADO_IMPLEMENTACION], "Manual")
                self.assertEqual(fila_ia[d.TEXTO_ENCABEZADO_IMPLEMENTACION], "Vibecoding")
                self.assertEqual(fila_ia[d.TEXTO_ENCABEZADO_DISPOSITIVO], "Android")
                cpu = self.datos_hoja(libro[d.HOJA_CPU_MEMORIA_SALIDA], 2)
                self.assertNotIn(d.TEXTO_ENCABEZADO_DISPOSITIVO, cpu)
                self.assertEqual(list(cpu), d.ENCABEZADOS_CPU_MEMORIA_SALIDA)
                EscritorResultadosExcel().guardar_resultado(libro, proyectos[1], None, None)
                self.assertEqual(hoja.max_row, 3)
            finally:
                libro.close()

    def test_pc_no_agrega_columnas_de_dispositivos(self):
        with TemporaryDirectory() as directorio:
            proyecto = self.cargar_temporal([self.datos_base("M1")], directorio)[0]
            libro = GestorLibrosExcel().crear_o_abrir_salida(
                Path(directorio) / "pc.xlsx", d.HOJAS_REPORTE
            )
            try:
                EscritorResultadosExcel().guardar_resultado(libro, proyecto, None, None)
                hoja = libro[d.HOJA_RESUMEN]
                self.assertEqual(
                    [celda.value for celda in hoja[1]], d.ENCABEZADOS_RESUMEN
                )
                self.assertEqual(hoja.cell(2, 1).value, "M1")
            finally:
                libro.close()


if __name__ == "__main__":
    unittest.main()
