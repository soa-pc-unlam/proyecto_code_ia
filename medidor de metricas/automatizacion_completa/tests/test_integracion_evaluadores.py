"""Pruebas integrales del flujo compartido y de los dos perfiles Excel."""

import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

import main_pc
import main_esp32_android
from constantes import definiciones as d
from modelos.modelos import MetricaComplejidad
from procesamiento import procesamiento as proceso
from procesamiento import procesamiento_comun as comun
from reportes.excel import crear_o_abrir_excel_salida, obtener_mapa_encabezados
from util.logging_config import configurar_logger


RAIZ = Path(__file__).resolve().parents[1]


class IntegracionEvaluadoresTest(unittest.TestCase):
    """Recorre entradas JSON y Excel reales hasta generar el archivo de salida."""

    def setUp(self):
        """Prepara rutas temporales sin modificar los datos originales."""
        self.temporal = TemporaryDirectory()
        self.addCleanup(self.temporal.cleanup)
        self.carpeta = Path(self.temporal.name)

    def ejecutar(self, tipo, cantidad, proyectos_alternativos=None):
        """Ejecuta el main real con entradas reales y análisis estático aislado.

        La complejidad se sustituye porque depende de herramientas y código
        externos no incluidos en el proyecto; las entradas de rúbricas y
        los escritores/gráficos se ejecutan realmente.
        """
        es_pc = tipo == d.TIPO_PROYECTO_PC
        archivo_config = (
            d.CONFIGURACION_JSON_PC if es_pc
            else d.CONFIGURACION_JSON_ESP32_ANDROID
        )
        archivo_proyectos = (
            d.DATOS_PC_PROYECTOS_JSON if es_pc
            else d.DATOS_ESP32_ANDROID_PROYECTOS_JSON
        )
        configuracion = json.loads((RAIZ / archivo_config).read_text('utf-8'))
        proyectos = json.loads((RAIZ / archivo_proyectos).read_text('utf-8'))[:cantidad]
        if proyectos_alternativos is not None:
            proyectos = proyectos_alternativos
        etiqueta = 'pc' if es_pc else 'dispositivos'
        salida = self.carpeta / f'reporte_{etiqueta}.xlsx'
        configuracion['archivo_excel_salida'] = str(salida)
        configuracion['archivo_excel_entrada'] = str(
            RAIZ / configuracion['archivo_excel_entrada']
        )
        configuracion['carpeta_resultados'] = str(self.carpeta / f'resultados_{etiqueta}')
        configuracion['carpeta_logs'] = str(self.carpeta / f'logs_{etiqueta}')
        ruta_config = self.carpeta / f'config_{etiqueta}.json'
        ruta_proyectos = self.carpeta / f'proyectos_{etiqueta}.json'
        ruta_config.write_text(json.dumps(configuracion), encoding='utf-8')
        ruta_proyectos.write_text(json.dumps(proyectos), encoding='utf-8')

        atributo_config = (
            'CONFIGURACION_JSON_PC' if es_pc else 'CONFIGURACION_JSON_ESP32_ANDROID'
        )
        atributo_proyectos = (
            'DATOS_PC_PROYECTOS_JSON' if es_pc else 'DATOS_ESP32_ANDROID_PROYECTOS_JSON'
        )
        complejidad = MetricaComplejidad(
            cantidad_funciones=2, ccn_total=6, ccn_promedio=3,
            nloc_total=80, nloc_promedio=40, nivel_cc='Bajo',
            interpretacion_cc='Testeabilidad alta',
        )
        entrada = main_pc.main if es_pc else main_esp32_android.main
        with patch.object(d, atributo_config, str(ruta_config)), \
             patch.object(d, atributo_proyectos, str(ruta_proyectos)), \
             patch.object(comun, 'limitar_cpu'), \
             patch.object(proceso, 'analizar_complejidad', return_value=complejidad), \
             patch.object(proceso, 'analizar_mi', return_value=None):
            entrada()

        self.assertTrue(salida.exists(), f'No se generó el Excel: {salida}')
        perfil = d.HOJAS_REPORTE if es_pc else d.HOJAS_REPORTE_DISPOSITIVOS
        libro = crear_o_abrir_excel_salida(salida, perfil)
        try:
            for hoja, columnas in perfil.items():
                self.assertEqual([c.value for c in libro[hoja][1]], columnas)
                if hoja != d.HOJA_ERRORES:
                    self.assertEqual(libro[hoja].max_row, cantidad + 1, hoja)
            self.assertEqual(len(libro['Graficos']._charts), 8)
            resumen = libro[d.HOJA_RESUMEN]
            self.assertEqual(resumen.max_row, cantidad + 1)
            for indice, proyecto in enumerate(proyectos, 2):
                fila = {celda.value: resumen.cell(indice, celda.column).value
                        for celda in resumen[1]}
                self.assertEqual(fila[d.TEXTO_ENCABEZADO_CODIGO], proyecto['codigo'])
                self.assertEqual(fila[d.TEXTO_ENCABEZADO_CCN_PROMEDIO_RESUMEN], 3)
                if es_pc:
                    self.assertNotIn(d.TEXTO_ENCABEZADO_DISPOSITIVO, fila)
                else:
                    self.assertEqual(
                        fila[d.TEXTO_ENCABEZADO_DISPOSITIVO], proyecto['dispositivo']
                    )
                    self.assertEqual(
                        fila[d.TEXTO_ENCABEZADO_IMPLEMENTACION],
                        proyecto['implementacion'],
                    )
                    self.assertEqual(
                        fila[d.TEXTO_ENCABEZADO_ID_PROYECTO],
                        proyecto['id_proyecto'],
                    )
            hoja_cpu = libro[d.HOJA_CPU_MEMORIA_SALIDA]
            columnas_cpu = obtener_mapa_encabezados(hoja_cpu)
            if not es_pc:
                self.assertIn(d.HOJA_CPU_MEMORIA_SALIDA, libro.sheetnames)
                self.assertNotIn(d.TEXTO_ENCABEZADO_DISPOSITIVO, columnas_cpu)
                self.assertEqual(
                    [celda.value for celda in hoja_cpu[1]],
                    d.ENCABEZADOS_CPU_MEMORIA_SALIDA,
                )
                self.assertEqual(hoja_cpu.cell(2, 12).number_format, r"0.00\%")
            # Las filas de Errores son un historial: un proyecto puede tener
            # cero o varios errores y repetir la ejecución agrega eventos.
            errores = libro[d.HOJA_ERRORES]
            if es_pc:
                self.assertEqual(errores.max_row, 1)
                self.assertIsNotNone(
                    hoja_cpu.cell(2, columnas_cpu[d.TEXTO_ENCABEZADO_CPU_PROMEDIO_NORMALIZADA]).value
                )
            else:
                self.assertGreaterEqual(errores.max_row, 2)
                primera_fila = {
                    celda.value: resumen.cell(2, celda.column).value
                    for celda in resumen[1]
                }
                if proyectos_alternativos is None:
                    # El Excel Android de entrada contiene mediciones reales.
                    self.assertIsNotNone(primera_fila[d.TEXTO_ENCABEZADO_CPU_PROMEDIO])
                    self.assertIsNotNone(primera_fila[d.TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN])
                    self.assertTrue(primera_fila[d.TEXTO_ENCABEZADO_INTERPRETACION_CPU_RESUMEN])
                    self.assertTrue(primera_fila[d.TEXTO_ENCABEZADO_INTERPRETACION_MEMORIA])
                    if proyectos[0]['codigo'] == 'L1-1C2026-AND-M':
                        self.assertAlmostEqual(
                            primera_fila[d.TEXTO_ENCABEZADO_CPU_PROMEDIO], 0.125
                        )
                        self.assertAlmostEqual(
                            primera_fila[d.TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN],
                            107 / 7792 * 100,
                        )
                    for encabezado in (
                        d.TEXTO_ENCABEZADO_CPUS_LOGICAS,
                        d.TEXTO_ENCABEZADO_CPU_NORMALIZADA_1,
                        d.TEXTO_ENCABEZADO_CPU_NORMALIZADA_2,
                        d.TEXTO_ENCABEZADO_MEMORIA_TOTAL_SALIDA_MB,
                        d.TEXTO_ENCABEZADO_MEMORIA_NORMALIZADA_PROMEDIO,
                    ):
                        self.assertIsNotNone(
                            hoja_cpu.cell(2, columnas_cpu[encabezado]).value,
                            encabezado,
                        )
                else:
                    # Para ESP32 sin filas de entrada no se inventan mediciones.
                    self.assertIsNone(primera_fila[d.TEXTO_ENCABEZADO_CPU_PROMEDIO])
                    self.assertIsNone(primera_fila[d.TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN])
        finally:
            libro.close()
        return salida, proyectos, perfil

    def test_reconfigurar_logger_cierra_archivo_anterior(self):
        """Reejecutar el sistema libera el archivo del logger anterior."""
        primero = configurar_logger(self.carpeta / 'logs_primero')
        manejador = next(h for h in primero.handlers if hasattr(h, 'baseFilename'))
        flujo_anterior = manejador.stream
        self.assertFalse(flujo_anterior.closed)
        segundo = configurar_logger(self.carpeta / 'logs_segundo')
        self.assertTrue(flujo_anterior.closed)
        for actual in segundo.handlers[:]:
            segundo.removeHandler(actual)
            actual.close()

    def test_main_pc_con_entrada_real_y_salida_excel(self):
        """Ejecuta main_pc con JSON/Excel auténticos y verifica las hojas."""
        self.ejecutar(d.TIPO_PROYECTO_PC, 1)

    def test_main_dispositivos_con_manual_y_vibecoding(self):
        """Ejecuta main de dispositivos con las dos implementaciones Android."""
        self.ejecutar(d.TIPO_PROYECTO_ESP32_ANDROID, 2)

    def test_dispositivos_android_java_y_kotlin(self):
        """Verifica los cuatro proyectos reales Android, en Java y Kotlin."""
        self.ejecutar(d.TIPO_PROYECTO_ESP32_ANDROID, 4)

    def test_esp32_respeta_perfil_y_mediciones_ausentes(self):
        """Comprueba ESP32 con un proyecto de prueba sin datos de medición."""
        ejemplo = {
            'codigo': 'E1-ESP-M', 'id_proyecto': 'E1',
            'nombre_proyecto': 'Sensor de prueba',
            'ruta_codigo': str(self.carpeta / 'no_disponible'),
            'herramienta_ia': 'No aplica', 'modelo_ia': 'No aplica',
            'lenguaje': 'Wiring', 'dispositivo': 'ESP32',
            'implementacion': 'Manual',
        }
        self.ejecutar(d.TIPO_PROYECTO_ESP32_ANDROID, 1, [ejemplo])

    def test_dos_ejecuciones_actualizan_sin_duplicar(self):
        """Repetir el evaluador no crea proyectos ni gráficos duplicados."""
        self.ejecutar(d.TIPO_PROYECTO_ESP32_ANDROID, 2)
        self.ejecutar(d.TIPO_PROYECTO_ESP32_ANDROID, 2)


if __name__ == '__main__':
    unittest.main()
