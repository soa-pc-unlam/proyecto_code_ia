"""Regresiones de la escritura común para PC y Android/ESP32."""

import unittest
from dataclasses import replace

from constantes import definiciones as d
from modelos.modelos import MetricaCpuMemoria, MetricaComplejidad, Proyecto
from reportes.excel import crear_o_abrir_excel_salida, guardar_resultado_excel


class EscrituraPerfilesTest(unittest.TestCase):
    """Verifica la ubicación de los valores y la actualización por código."""

    def setUp(self):
        self.proyecto = Proyecto(
            "L1-M", "Smart Mirror", "ruta", "No aplica", "No aplica", "Java"
        )
        self.complejidad = MetricaComplejidad(
            cantidad_funciones=4, ccn_total=12, ccn_promedio=3,
            nloc_total=100, nloc_promedio=25, nivel_cc="Bajo",
            interpretacion_cc="Correcta",
        )

    def guardar(self, libro, proyecto, cpu=None):
        guardar_resultado_excel(
            libro, proyecto, self.complejidad, None, metricas_cpu_memoria=cpu
        )

    def valores(self, hoja, fila=2):
        return {
            celda.value: hoja.cell(row=fila, column=celda.column).value
            for celda in hoja[1]
        }

    def test_pc_conserva_la_escritura_posicional_y_cpu_original(self):
        libro = crear_o_abrir_excel_salida("/ruta/inexistente/reporte_pc.xlsx")
        cpu = MetricaCpuMemoria(
            codigo="L1-M", lenguaje="Java", metodo_medicion="Medición PC",
            modo_cpu="100%=capacidad total", cpus_logicas=8,
            accion_1="abrir", cpu_medida_1=12, accion_2="cerrar",
            cpu_medida_2=25, cpu_normalizada_1=12, cpu_normalizada_2=25,
            cpu_promedio_normalizada=18.5, cpu_maxima_normalizada=25,
            nivel_cpu="Bajo", interpretacion_cpu="Bajo consumo",
            memoria_acc1=100, memoria_acc2=200, memoria_total=1000,
            memoria_promedio=150, memoria_promedio_normalizada=15,
            memoria_maxima=200, nivel_memoria="Bajo",
            interpretacion_memoria="Bajo consumo de memoria",
        )
        try:
            self.guardar(libro, self.proyecto, cpu)
            self.assertEqual(libro.sheetnames, list(d.HOJAS_REPORTE))
            resumen = libro[d.HOJA_RESUMEN]
            self.assertEqual([c.value for c in resumen[1]], d.ENCABEZADOS_RESUMEN)
            self.assertEqual(resumen.cell(2, 6).value, 3)
            self.assertEqual(resumen.cell(2, 17).value, 18.5)
            self.assertEqual(resumen.cell(2, 19).value, 15)
            hoja_cpu = libro[d.HOJA_CPU_MEMORIA_SALIDA]
            self.assertEqual(hoja_cpu.cell(2, 12).value, 18.5)
            self.assertEqual(hoja_cpu.cell(2, 20).value, 15)
            self.assertEqual(hoja_cpu.cell(2, 12).number_format, r"0.00\%")
            self.assertEqual(hoja_cpu.cell(2, 20).number_format, r"0.00\%")
        finally:
            libro.close()

    def test_dispositivos_escriben_en_sus_encabezados_y_actualizan(self):
        libro = crear_o_abrir_excel_salida(
            "/ruta/inexistente/reporte_dispositivos.xlsx",
            hojas_reporte=d.HOJAS_REPORTE_DISPOSITIVOS,
        )
        proyecto = replace(
            self.proyecto, id_proyecto="L1", dispositivo="Android",
            implementacion="Manual"
        )
        cpu = MetricaCpuMemoria(
            codigo="L1-M", lenguaje="Java", metodo_medicion="Medición real",
            modo_cpu="100%=1 núcleo", cpus_logicas=8,
            accion_1="Inicio", cpu_medida_1=200, accion_2="Navegar",
            cpu_medida_2=280, cpu_normalizada_1=25, cpu_normalizada_2=35,
            cpu_promedio_normalizada=30, cpu_maxima_normalizada=35,
            nivel_cpu="Bajo", interpretacion_cpu="Consumo bajo",
            memoria_acc1=120, memoria_acc2=140, memoria_total=1000,
            memoria_promedio=130, memoria_promedio_normalizada=13,
            memoria_maxima=140, nivel_memoria="Bajo",
            interpretacion_memoria="Consumo bajo",
        )
        try:
            self.guardar(libro, proyecto, cpu)
            resumen = self.valores(libro[d.HOJA_RESUMEN])
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_CCN_PROMEDIO_RESUMEN], 3)
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_ID_PROYECTO], "L1")
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_DISPOSITIVO], "Android")
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_IMPLEMENTACION], "Manual")
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_CPU_PROMEDIO], 30)
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN], 13)
            hoja = libro[d.HOJA_CPU_MEMORIA_SALIDA]
            valores = self.valores(hoja)
            self.assertEqual(valores[d.TEXTO_ENCABEZADO_CPUS_LOGICAS], 8)
            self.assertEqual(valores[d.TEXTO_ENCABEZADO_CPU_NORMALIZADA_1], 25)
            self.assertEqual(valores[d.TEXTO_ENCABEZADO_CPU_NORMALIZADA_2], 35)
            self.assertEqual(valores[d.TEXTO_ENCABEZADO_CPU_PROMEDIO_NORMALIZADA], 30)
            self.assertEqual(valores[d.TEXTO_ENCABEZADO_MEMORIA_TOTAL_SALIDA_MB], 1000)
            self.assertEqual(valores[d.TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_MB], 130)
            self.assertEqual(valores[d.TEXTO_ENCABEZADO_MEMORIA_NORMALIZADA_PROMEDIO], 13)
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_INTERPRETACION_CPU_RESUMEN], "Consumo bajo")
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_INTERPRETACION_MEMORIA], "Consumo bajo")
            self.assertNotIn(d.TEXTO_ENCABEZADO_DISPOSITIVO, valores)
            self.assertEqual(valores[d.TEXTO_ENCABEZADO_LENGUAJE], "Java")
            self.assertEqual(valores[d.TEXTO_ENCABEZADO_MODO_CPU], "100%=1 núcleo")
            self.assertEqual(list(valores), d.ENCABEZADOS_CPU_MEMORIA_SALIDA)
            self.assertEqual(hoja.cell(2, 12).number_format, r"0.00\%")
            self.assertEqual(hoja.cell(2, 20).number_format, r"0.00\%")
            columna_memoria = d.ENCABEZADOS_CPU_MEMORIA_SALIDA.index(
                d.TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_MB
            ) + 1
            self.assertEqual(hoja.cell(2, columna_memoria).number_format, "0.00")
            cpu.cpu_promedio_normalizada = 42
            self.guardar(libro, proyecto, cpu)
            self.assertEqual(libro[d.HOJA_RESUMEN].max_row, 2)
            self.assertEqual(hoja.max_row, 2)
            self.assertEqual(
                self.valores(hoja)[d.TEXTO_ENCABEZADO_CPU_PROMEDIO_NORMALIZADA], 42
            )
        finally:
            libro.close()

    def test_dispositivos_reutilizan_normalizacion_y_limpian_datos_ausentes(self):
        libro = crear_o_abrir_excel_salida(
            "/ruta/inexistente/reporte_dispositivos.xlsx",
            hojas_reporte=d.HOJAS_REPORTE_DISPOSITIVOS,
        )
        metricas_pc = MetricaCpuMemoria(
            codigo="L1-M", lenguaje="Java", metodo_medicion="Medición",
            modo_cpu="100%=capacidad total", cpus_logicas=8,
            accion_1="Inicio", cpu_medida_1=15, accion_2="Navegar",
            cpu_medida_2=17, cpu_normalizada_1=15, cpu_normalizada_2=17,
            cpu_promedio_normalizada=85, cpu_maxima_normalizada=95,
            nivel_cpu="Alto", interpretacion_cpu="Consumo alto",
            memoria_acc1=600, memoria_acc2=700, memoria_total=1000,
            memoria_promedio=650, memoria_promedio_normalizada=65,
            memoria_maxima=700, nivel_memoria="Alto",
            interpretacion_memoria="Consumo alto",
        )
        try:
            self.guardar(libro, self.proyecto, metricas_pc)
            resumen = self.valores(libro[d.HOJA_RESUMEN])
            hoja_cpu = self.valores(libro[d.HOJA_CPU_MEMORIA_SALIDA])
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_CPU_PROMEDIO], 85)
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN], 65)
            self.assertEqual(hoja_cpu[d.TEXTO_ENCABEZADO_CPU_PROMEDIO_NORMALIZADA], 85)
            self.assertEqual(hoja_cpu[d.TEXTO_ENCABEZADO_MEMORIA_NORMALIZADA_PROMEDIO], 65)
            self.assertEqual(hoja_cpu[d.TEXTO_ENCABEZADO_CPU_MEDIDA_1], 15)
            self.guardar(libro, self.proyecto, None)
            self.assertEqual(
                self.valores(libro[d.HOJA_CPU_MEMORIA_SALIDA])[
                    d.TEXTO_ENCABEZADO_CPU_MEDIDA_1
                ], ""
            )
        finally:
            libro.close()

    def test_migracion_hoja_dispositivos_anterior_conserva_mediciones(self):
        """Reordena una hoja antigua conservando datos normalizados existentes."""
        from reportes.excel import crear_hojas_si_no_existen

        libro = crear_o_abrir_excel_salida(
            "/ruta/inexistente/reporte_dispositivos.xlsx",
            hojas_reporte=d.HOJAS_REPORTE_DISPOSITIVOS,
        )
        try:
            hoja = libro[d.HOJA_CPU_MEMORIA_SALIDA]
            hoja.delete_cols(1, hoja.max_column)
            antiguos = [
                d.TEXTO_ENCABEZADO_CODIGO,
                d.TEXTO_ENCABEZADO_DISPOSITIVO,
                "CPU promedio (%)",
                "CPU máxima (%)",
                d.TEXTO_ENCABEZADO_MEMORIA_NORMALIZADA_PROMEDIO,
            ]
            hoja.append(antiguos)
            # Eliminar la fila vacía creada por el libro en memoria.
            if hoja.cell(1, 1).value is None:
                hoja.delete_rows(1)
            hoja.append(["L1-M", "Android", 23, 30, 18])

            crear_hojas_si_no_existen(libro, d.HOJAS_REPORTE_DISPOSITIVOS)
            self.assertEqual([celda.value for celda in hoja[1]],
                             d.ENCABEZADOS_CPU_MEMORIA_SALIDA)
            datos = self.valores(hoja)
            self.assertEqual(datos[d.TEXTO_ENCABEZADO_CPU_PROMEDIO_NORMALIZADA], 23)
            self.assertEqual(datos[d.TEXTO_ENCABEZADO_CPU_MAXIMA_NORMALIZADA], 30)
            self.assertEqual(datos[d.TEXTO_ENCABEZADO_MEMORIA_NORMALIZADA_PROMEDIO], 18)
            self.assertEqual(hoja.cell(2, 12).number_format, r"0.00\%")
            self.assertNotIn(d.TEXTO_ENCABEZADO_DISPOSITIVO, datos)
        finally:
            libro.close()

    def test_libro_existente_con_columnas_agregadas_se_actualiza_bien(self):
        libro = crear_o_abrir_excel_salida(
            "/ruta/inexistente/reporte_dispositivos.xlsx",
            hojas_reporte=d.HOJAS_REPORTE,
        )
        # Simula que el libro previo tenía los encabezados de PC.
        from reportes.excel import crear_hojas_si_no_existen
        crear_hojas_si_no_existen(libro, d.HOJAS_REPORTE_DISPOSITIVOS)
        try:
            self.guardar(libro, self.proyecto)
            resumen = self.valores(libro[d.HOJA_RESUMEN])
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_CCN_PROMEDIO_RESUMEN], 3)
            self.assertEqual(resumen[d.TEXTO_ENCABEZADO_CODIGO], "L1-M")
            self.assertNotEqual(
                resumen[d.TEXTO_ENCABEZADO_ID_PROYECTO], "Bajo"
            )
        finally:
            libro.close()


if __name__ == "__main__":
    unittest.main()
