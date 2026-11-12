"""Validación de la carga desde Datos_Proyectos en Excel."""

import unittest
from pathlib import Path

from openpyxl import Workbook, load_workbook

from configuracion.lector_proyectos_excel import LectorProyectosExcel


RAIZ = Path(__file__).resolve().parents[1]


class LectorProyectosExcelTest(unittest.TestCase):
    def setUp(self):
        self.libro = Workbook()
        self.hoja = self.libro.active
        self.hoja.title = "Datos_Proyectos"
        self.encabezados = [
            "Código", "Nombre Proyecto", "Ruta Código", "Herramienta IA",
            "Modelo IA", "Lenguaje",
        ]
        self.hoja.append(self.encabezados)
        self.fila = ["X1", "Proyecto", "C:/codigo", "IA", "Modelo", "Python"]
        self.addCleanup(self.libro.close)

    def test_pc_reconoce_columnas_reordenadas_y_tildes(self):
        self.hoja.delete_rows(1)
        self.hoja.append(list(reversed(self.encabezados)))
        self.hoja.append(list(reversed(self.fila)))
        proyecto = LectorProyectosExcel().cargar(self.libro)[0]
        self.assertEqual((proyecto.codigo, proyecto.ruta_codigo), ("X1", "C:/codigo"))

    def test_filas_vacias_se_omiten(self):
        self.hoja.append([None] * 6)
        self.hoja.append(self.fila)
        self.assertEqual(len(LectorProyectosExcel().cargar(self.libro)), 1)

    def test_fila_incompleta_indica_numero(self):
        self.hoja.append(["X1", "Proyecto", None, "IA", "Modelo", "Python"])
        with self.assertRaisesRegex(ValueError, "Fila 2.*Ruta Código"):
            LectorProyectosExcel().cargar(self.libro)

    def test_columna_obligatoria_faltante(self):
        self.hoja.delete_cols(3)
        with self.assertRaisesRegex(ValueError, "Falta la columna 'Ruta Código'"):
            LectorProyectosExcel().cargar(self.libro)

    def test_hoja_faltante(self):
        self.hoja.title = "Otra"
        with self.assertRaisesRegex(ValueError, "No existe la hoja"):
            LectorProyectosExcel().cargar(self.libro)

    def test_carga_real_ambos_perfiles_en_modo_lectura(self):
        for perfil in ("pc", "esp32_android"):
            with self.subTest(perfil=perfil):
                archivo = RAIZ / "datos_entrada" / f"datos_entrada_{perfil}.xlsx"
                libro = load_workbook(archivo, data_only=True, read_only=True)
                try:
                    proyectos = LectorProyectosExcel().cargar(libro)
                    self.assertTrue(proyectos)
                    self.assertTrue(all(proyecto.codigo for proyecto in proyectos))
                    if perfil == "esp32_android":
                        self.assertTrue(all(proyecto.dispositivo for proyecto in proyectos))
                    else:
                        self.assertTrue(all(proyecto.dispositivo == "" for proyecto in proyectos))
                finally:
                    libro.close()


if __name__ == "__main__":
    unittest.main()
