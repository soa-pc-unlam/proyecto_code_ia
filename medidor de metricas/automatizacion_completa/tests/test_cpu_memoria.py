"""Pruebas de cálculo y validación de métricas de CPU y memoria."""

import unittest

from openpyxl import Workbook

from constantes.definiciones import ENCABEZADOS_CPU_MEMORIA_ENTRADA, HOJA_CPU_MEMORIA_ENTRADA
from modelos.modelos import Proyecto
from metricas.cpu_memoria import analizar_cpu_memoria, crear_metrica_cpu_memoria


class _LoggerNulo:
    def debug(self, _mensaje):
        pass


class CpuMemoriaTest(unittest.TestCase):
    """Verifica normalización, promedios, máximos y clasificaciones."""

    def setUp(self):
        self.proyecto = Proyecto("M1", "Prueba", "ruta", "IA", "Modelo", "Python")
        self.configuracion = {"umbral_bajo": 25, "umbral_medio": 60}
        self.valores = {
            "Método medición": "psutil", "Modo CPU": "100%=1 núcleo",
            "CPUs lógicas": 4, "Acción 1": "Reposo", "CPU Acc1": 0.40,
            "Acción 2": "Carga", "CPU Acc2": 2.00, "Memoria Acc1 (MB)": 100,
            "Memoria Acc2 (MB)": 300, "MemoriaTotal (MB)": 1000,
        }
        self.formatos = {campo: "General" for campo in self.valores}
        self.formatos["CPU Acc1"] = "0.00%"
        self.formatos["CPU Acc2"] = "0.00%"

    def crear(self):
        return crear_metrica_cpu_memoria(
            self.proyecto, self.valores, self.formatos, self.configuracion
        )

    def test_normaliza_cpu_por_nucleos_y_conserva_porcentajes_excel(self):
        metrica = self.crear()
        self.assertAlmostEqual(metrica.cpu_medida_1, 40)
        self.assertAlmostEqual(metrica.cpu_medida_2, 200)
        self.assertAlmostEqual(metrica.cpu_normalizada_1, 10)
        self.assertAlmostEqual(metrica.cpu_normalizada_2, 50)
        self.assertAlmostEqual(metrica.cpu_promedio_normalizada, 30)
        self.assertAlmostEqual(metrica.cpu_maxima_normalizada, 50)
        self.assertEqual(metrica.nivel_cpu, "Medio")

    def test_modo_capacidad_total_no_divide_por_cpus(self):
        self.valores["Modo CPU"] = "100%=capacidad total"
        metrica = self.crear()
        self.assertAlmostEqual(metrica.cpu_normalizada_1, 40)
        self.assertAlmostEqual(metrica.cpu_normalizada_2, 200)

    def test_una_sola_medicion_se_usa_para_promedio_y_maximo(self):
        self.valores["CPU Acc2"] = None
        self.valores["Memoria Acc2 (MB)"] = None
        metrica = self.crear()
        self.assertAlmostEqual(metrica.cpu_promedio_normalizada, 10)
        self.assertAlmostEqual(metrica.cpu_maxima_normalizada, 10)
        self.assertAlmostEqual(metrica.memoria_promedio, 100)
        self.assertAlmostEqual(metrica.memoria_maxima, 100)

    def test_sin_mediciones_queda_no_evaluable(self):
        self.valores["CPU Acc1"] = None
        self.valores["CPU Acc2"] = None
        self.valores["Modo CPU"] = None
        self.valores["CPUs lógicas"] = None
        self.valores["Memoria Acc1 (MB)"] = None
        self.valores["Memoria Acc2 (MB)"] = None
        metrica = self.crear()
        self.assertIsNone(metrica.cpu_promedio_normalizada)
        self.assertEqual(metrica.nivel_cpu, "No evaluable")
        self.assertEqual(metrica.nivel_memoria, "No evaluable")

    def test_memoria_normalizada_se_almacena_como_proporcion(self):
        metrica = self.crear()
        self.assertAlmostEqual(metrica.memoria_promedio, 200)
        self.assertAlmostEqual(metrica.memoria_promedio_normalizada, 0.20)
        self.assertAlmostEqual(metrica.memoria_maxima, 300)
        self.assertEqual(metrica.nivel_memoria, "Bajo")

    def test_memoria_total_cero_no_calcula_normalizacion(self):
        self.valores["MemoriaTotal (MB)"] = 0
        metrica = self.crear()
        self.assertIsNone(metrica.memoria_promedio_normalizada)
        self.assertEqual(metrica.nivel_memoria, "No evaluable")

    def test_clasificacion_respeta_limites_sin_redondear(self):
        self.valores["Modo CPU"] = "100%=capacidad total"
        self.valores["CPU Acc1"] = 0.249999
        self.valores["CPU Acc2"] = None
        metrica = self.crear()
        self.assertEqual(metrica.nivel_cpu, "Bajo")
        self.valores["CPU Acc1"] = 0.25
        self.assertEqual(self.crear().nivel_cpu, "Medio")
        self.valores["CPU Acc1"] = 0.60
        self.assertEqual(self.crear().nivel_cpu, "Medio")
        self.valores["CPU Acc1"] = 0.600001
        self.assertEqual(self.crear().nivel_cpu, "Alto")

    def test_rechaza_cpus_logicas_invalidas_en_modo_por_nucleo(self):
        self.valores["CPUs lógicas"] = 0
        with self.assertRaisesRegex(ValueError, "CPUs lógicas"):
            self.crear()

    def test_rechaza_mediciones_negativas(self):
        self.valores["Memoria Acc1 (MB)"] = -1
        with self.assertRaisesRegex(ValueError, "Memoria Acc1"):
            self.crear()


    def crear_libro_entrada(self, codigos):
        """Crea un libro mínimo para probar búsqueda por código."""
        libro = Workbook()
        hoja = libro.active
        hoja.title = HOJA_CPU_MEMORIA_ENTRADA
        hoja.append(["Código"] + ENCABEZADOS_CPU_MEMORIA_ENTRADA)
        for codigo in codigos:
            hoja.append([codigo, "ps", "100%=capacidad total", 4, None, 10, None, None, 20, None, 1000])
        return libro

    def test_rechaza_codigo_duplicado(self):
        libro = self.crear_libro_entrada(["M1", "M1"])
        with self.assertRaisesRegex(ValueError, "duplicado"):
            analizar_cpu_memoria(self.proyecto, libro, self.configuracion, _LoggerNulo())

    def test_rechaza_codigo_inexistente(self):
        libro = self.crear_libro_entrada(["OTRO"])
        with self.assertRaisesRegex(ValueError, "No se encontró"):
            analizar_cpu_memoria(self.proyecto, libro, self.configuracion, _LoggerNulo())


if __name__ == "__main__":
    unittest.main()
