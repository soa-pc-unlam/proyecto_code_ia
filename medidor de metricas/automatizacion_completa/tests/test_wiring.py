"""Soporte Wiring real, manejo de fallos y escritura en el Excel compartido."""

import csv
import importlib.util
import json
import logging
import shutil
import subprocess
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from threading import Semaphore
from unittest.mock import patch

from openpyxl import Workbook, load_workbook

from constantes import definiciones as d
from metricas.bugs_smells import AnalizadorCppcheck
from metricas.complejidad import EjecutorLizard, ProcesadorComplejidad
from metricas.mantenibilidad import LectorLizardMantenibilidad
from modelos.modelos import MetricaComplejidad, Proyecto
from procesamiento.procesamiento import procesar_proyecto, guardar_resultado_proyecto
from util.exception import LenguajeNoSoportadoError
from util.fuentes_wiring import (
    obtener_inclusiones_platformio, preparar_fuentes_wiring, seleccionar_fuentes_wiring,
)
from util.lenguajes import normalizar_lenguaje


HAY_LIZARD = bool(importlib.util.find_spec("lizard") and shutil.which("lizard"))
HAY_CPPCHECK = bool(shutil.which("cppcheck"))
FUENTE_WIRING = """#include <Arduino.h>

from reportes.escritor_resultados import EscritorResultadosExcel
from reportes.comparaciones import GeneradorComparaciones
from reportes.gestor_libros import GestorLibrosExcel
from reportes.excel_utilidades import UtilidadesExcel
#include "control.h"
void setup() {}
void loop() { if (digitalRead(2)) { digitalWrite(3, HIGH); } }
"""


class WiringTest(unittest.TestCase):
    """Comprueba el lenguaje, los adaptadores y el recorrido hasta el reporte."""

    def setUp(self):
        """Prepara fuentes y datos de prueba fuera de las entradas reales."""
        temporal = TemporaryDirectory()
        self.addCleanup(temporal.cleanup)
        self.raiz = Path(temporal.name)
        self.codigo = self.raiz / "codigo propio"
        self.codigo.mkdir()
        self.logger = logging.getLogger("pruebas.wiring")
        self.configuracion = json.loads(Path(
            d.CONFIGURACION_JSON_ESP32_ANDROID
        ).read_text(encoding="utf-8"))
        self.configuracion["carpeta_resultados"] = str(self.raiz / "resultados")
        self.proyecto = Proyecto(
            "E1-ESP-M", "Sensor", str(self.codigo), "No aplica", "No aplica",
            "Wiring", "E1", "ESP32", "Manual",
        )
        (self.codigo / "main.ino").write_text(FUENTE_WIRING, encoding="utf-8")
        (self.codigo / "control.h").write_text(
            "#pragma once\ninline int error_control() { return 1 / 0; }\n",
            encoding="utf-8",
        )
        (self.codigo / "control.cpp").write_text(
            '#include "control.h"\n'
            'int calcular(int v) { if (v > 0) return v; return 0; }\n',
            encoding="utf-8",
        )

    def crear_entrada(self, proyectos):
        """Construye un Excel con mediciones ficticias explícitas para las pruebas."""
        libro = Workbook()
        self.addCleanup(libro.close)
        hojas = (
            (d.HOJA_CPU_MEMORIA_ENTRADA, d.ENCABEZADOS_CPU_MEMORIA_ENTRADA),
            (d.HOJA_CONCURRENCIA_ENTRADA, d.ENCABEZADOS_CONCURRENCIA_ENTRADA),
            (d.HOJA_TOKENS_ENTRADA, d.ENCABEZADOS_TOKENS_ENTRADA),
        )
        for nombre, encabezados in hojas:
            libro.create_sheet(nombre).append([d.TEXTO_ENCABEZADO_CODIGO, *encabezados])
        for proyecto in proyectos:
            libro[d.HOJA_CPU_MEMORIA_ENTRADA].append([
                proyecto.codigo, "Prueba", d.MODO_CPU_TOTAL, 2,
                "Acción A", 20, "Acción B", 40, 1, 3, 10,
            ])
            libro[d.HOJA_CONCURRENCIA_ENTRADA].append([
                proyecto.codigo, "Alta", "Alta", "Alta", "Alta",
            ])
            libro[d.HOJA_TOKENS_ENTRADA].append([
                proyecto.codigo, "Registro de prueba", 1, 100,
            ])
        return libro

    def test_normalizacion_conserva_lenguajes_anteriores(self):
        """Wiring se admite sin alterar identificadores anteriores ni aceptar otros."""
        for entrada, esperado in (
            (" Wiring ", "wiring"), ("WIRING", "wiring"), ("py", "python"),
            ("Java", "java"), ("kt", "kotlin"),
        ):
            with self.subTest(entrada=entrada):
                self.assertEqual(normalizar_lenguaje(entrada), esperado)
        with self.assertRaises(LenguajeNoSoportadoError):
            normalizar_lenguaje("desconocido")

    def test_seleccion_excluye_dependencias_y_generados(self):
        """Ambos analizadores reciben solo las fuentes propias seleccionadas."""
        for nombre in ("build", ".pio", "libraries", "vendor", "generated"):
            carpeta = self.codigo / nombre
            carpeta.mkdir()
            (carpeta / "ajena.cpp").write_text("int ajena() {}", encoding="utf-8")
        (self.codigo / "auto.h").write_text("// @generated\nint f();", encoding="utf-8")
        (self.codigo / "ajena.java").write_text("class Ajena {}", encoding="utf-8")
        self.assertEqual(
            {ruta.name for ruta in seleccionar_fuentes_wiring(self.codigo)},
            {"main.ino", "control.cpp", "control.h"},
        )

    def test_temporales_conservan_originales_y_no_colisionan(self):
        """Los sketches no pisan archivos existentes y el temporal se limpia al fallar."""
        (self.codigo / "main.ino.cpp").write_text("int otra() {}", encoding="utf-8")
        originales = {ruta: ruta.read_bytes() for ruta in self.codigo.iterdir()}
        with self.assertRaisesRegex(RuntimeError, "fallo de prueba"):
            with preparar_fuentes_wiring(self.codigo) as (raiz, rutas):
                self.assertEqual(len(rutas), len(originales))
                self.assertTrue(all(ruta.exists() for ruta in rutas))
                self.assertEqual(set(rutas.values()), set(originales))
                raise RuntimeError("fallo de prueba")
        self.assertFalse(raiz.exists())
        self.assertEqual(originales, {ruta: ruta.read_bytes() for ruta in self.codigo.iterdir()})

    def test_sin_fuentes_no_genera_mediciones_en_cero(self):
        """Una carpeta sin código produce un error explícito."""
        vacia = self.raiz / "vacia"
        vacia.mkdir()
        with self.assertRaisesRegex(ValueError, "No hay fuentes Wiring"):
            with preparar_fuentes_wiring(vacia):
                self.fail("No debe prepararse un análisis sin fuentes")

    def test_cppcheck_severidades_ubicaciones_y_duplicados(self):
        """Cuenta un diagnóstico con varias ubicaciones y traduce las severidades."""
        diagnosticos = []
        for severidad in ("error", "warning", "performance", "style", "portability"):
            diagnosticos.append(
                f'<error id="{severidad}" severity="{severidad}" msg="Mensaje">'
                '<location file="main.cpp" line="3" column="2"/>'
                '<location file="main.cpp" line="1"/></error>'
            )
        xml = '<results version="2"><errors>' + ''.join(diagnosticos) * 2
        xml += '</errors></results>'
        issues = AnalizadorCppcheck.parsear_xml_cppcheck(xml, self.logger)
        self.assertEqual(len(issues), 5)
        self.assertEqual([issue["severidad"] for issue in issues], [
            d.ISSUE_SEVERIDAD_ALTA, d.ISSUE_SEVERIDAD_MEDIA, d.ISSUE_SEVERIDAD_MEDIA,
            d.ISSUE_SEVERIDAD_BAJA, d.ISSUE_SEVERIDAD_BAJA,
        ])

    def test_cppcheck_fallos_y_reportes_incompletos(self):
        """No acepta XML incompleto ni errores de configuración como mediciones."""
        salidas = ["", "<invalido/>", '<results version="2"/>']
        for regla in ("syntaxError", "unknownMacro", "internalError"):
            salidas.append(
                f'<results version="2"><errors><error id="{regla}" '
                'severity="error" msg="Fallo"/></errors></results>'
            )
        for salida in salidas:
            with self.subTest(salida=salida), self.assertRaises(RuntimeError):
                AnalizadorCppcheck.parsear_xml_cppcheck(salida, self.logger)

    def test_cppcheck_sin_incidencias_e_informacion(self):
        """Una medición válida vacía se distingue de una limitación de cobertura."""
        self.assertEqual(AnalizadorCppcheck.parsear_xml_cppcheck(
            '<results version="2"><errors/></results>', self.logger,
        ), [])
        xml = ('<results version="2"><errors><error id="missingIncludeSystem" '
               'severity="information" msg="Arduino.h ausente"/></errors></results>')
        with self.assertLogs(self.logger, level="WARNING") as mensajes:
            self.assertEqual(AnalizadorCppcheck.parsear_xml_cppcheck(xml, self.logger), [])
        self.assertIn("missingIncludeSystem: 1", mensajes.output[0])

    def test_inclusiones_platformio_usadas_sin_seleccionar_dependencias(self):
        """Resuelve rutas instaladas y metadatos del entorno predeterminado."""
        core = self.raiz / "platformio"
        paquete = core / "packages" / "framework-arduinoespressif32"
        rutas = [
            paquete / "cores" / "esp32",
            paquete / "libraries" / "WiFi" / "src",
            self.codigo / ".pio" / "libdeps" / "esp32" / "PubSubClient" / "src",
            self.codigo / "include",
        ]
        for ruta in rutas:
            ruta.mkdir(parents=True)
        ajena = self.codigo / ".pio" / "libdeps" / "otro" / "Ajena" / "src"
        ajena.mkdir(parents=True)
        (self.codigo / "platformio.ini").write_text(
            f"[platformio]\ncore_dir = {core}\ndefault_envs = esp32\n"
            "[env:esp32]\nplatform = espressif32\nframework = arduino\n"
            "[env:otro]\nplatform = espressif32\nframework = arduino\n",
            encoding="utf-8",
        )
        metadatos = self.codigo / ".vscode"
        metadatos.mkdir()
        (metadatos / "c_cpp_properties.json").write_text(json.dumps({
            "configurations": [{"includePath": [
                "${workspaceFolder}/include", str(rutas[0]), "${desconocida}/src",
            ]}],
        }), encoding="utf-8")
        inclusiones = obtener_inclusiones_platformio(self.codigo)
        self.assertTrue(set(rutas).issubset(inclusiones))
        self.assertNotIn(ajena, inclusiones)
        self.assertEqual(inclusiones[0], self.codigo / "include")
        self.assertEqual(len(inclusiones), len(set(inclusiones)))
        self.assertEqual(len(seleccionar_fuentes_wiring(self.codigo)), 3)

    def test_cppcheck_conserva_incidencias_con_cabeceras_ausentes(self):
        """Las inclusiones ausentes limitan cobertura sin descartar incidencias propias."""
        xml = ('<results version="2"><errors>'
               '<error id="missingInclude" severity="information" '
               'msg="Include file: &quot;PubSubClient.h&quot; not found."/>'
               '<error id="nullPointer" severity="error" msg="Null">'
               '<location file="main.cpp" line="3"/></error>'
               '<error id="toomanyconfigs" severity="information" msg="12 de 40"/>'
               '</errors></results>')
        limitaciones = []
        with self.assertLogs(self.logger, level="WARNING"):
            issues = AnalizadorCppcheck.parsear_xml_cppcheck(xml, self.logger, limitaciones=limitaciones)
        self.assertEqual(len(issues), 1)
        self.assertEqual(issues[0]["severidad"], d.ISSUE_SEVERIDAD_ALTA)
        self.assertIn("PubSubClient.h", limitaciones[0])
        self.assertIn("12 de 40", limitaciones[1])

    def test_metadatos_vscode_invalidos_conservan_rutas_instaladas(self):
        """Un archivo auxiliar inválido no impide analizar con las rutas disponibles."""
        biblioteca = self.codigo / ".pio" / "libdeps" / "esp32" / "Biblioteca" / "src"
        biblioteca.mkdir(parents=True)
        (self.codigo / "platformio.ini").write_text(
            "[env:esp32]\nplatform = otra\n", encoding="utf-8",
        )
        metadatos = self.codigo / ".vscode"
        metadatos.mkdir()
        (metadatos / "c_cpp_properties.json").write_text("// archivo incompleto", encoding="utf-8")
        with self.assertLogs(self.logger, level="WARNING"):
            inclusiones = obtener_inclusiones_platformio(self.codigo, self.logger)
        self.assertIn(biblioteca, inclusiones)

    def test_cppcheck_no_cuenta_diagnosticos_de_bibliotecas(self):
        """Un diagnóstico cuya ubicación principal es externa no aumenta el ISI propio."""
        propia = self.codigo / "control.cpp"
        biblioteca = self.raiz / "biblioteca.h"
        diagnosticos = []
        for ruta in (propia, biblioteca):
            diagnosticos.append(
                '<error id="zerodiv" severity="error" msg="Division by zero">'
                f'<location file="{ruta}" line="3"/></error>'
            )
        xml = '<results version="2"><errors>' + ''.join(diagnosticos)
        xml += '</errors></results>'
        issues = AnalizadorCppcheck.parsear_xml_cppcheck(xml, self.logger, {propia: propia})
        self.assertEqual(len(issues), 1)
        self.assertEqual(issues[0]["archivo"], str(propia))
        with self.assertRaisesRegex(RuntimeError, "no indicó la ubicación"):
            AnalizadorCppcheck.parsear_xml_cppcheck(
                xml.replace(f'file="{propia}"', ''), self.logger, {propia: propia},
            )

    @unittest.skipUnless(HAY_CPPCHECK, "Cppcheck no instalado")
    def test_cppcheck_real_resuelve_dependencia_y_omite_sus_incidencias(self):
        """Cppcheck lee una biblioteca instalada sin incorporarla a las métricas."""
        biblioteca = self.codigo / ".pio" / "libdeps" / "esp32" / "Externa" / "src"
        biblioteca.mkdir(parents=True)
        (biblioteca / "Externa.h").write_text(
            "inline int defecto_externo() { return 1 / 0; }", encoding="utf-8",
        )
        (self.codigo / "platformio.ini").write_text(
            "[env:esp32]\nplatform = otra\n", encoding="utf-8",
        )
        (self.codigo / "main.ino").write_text(
            '#include "Externa.h"\n' + FUENTE_WIRING, encoding="utf-8",
        )
        limitaciones = []
        issues = AnalizadorCppcheck.ejecutar_cppcheck(
            self.codigo, self.proyecto.codigo, self.logger, limitaciones,
        )
        self.assertTrue(any(issue["regla"] == "zerodiv" for issue in issues))
        self.assertTrue(all(Path(issue["archivo"]).parent == self.codigo for issue in issues))
        self.assertFalse(any("Externa.h" in mensaje for mensaje in limitaciones))
        self.assertTrue(any("Arduino.h" in mensaje for mensaje in limitaciones))

    def test_cppcheck_proceso_fallido_no_acepta_xml_vacio_valido(self):
        """Un proceso fallido se informa aunque haya emitido un XML sin incidencias."""
        resultado = subprocess.CompletedProcess(
            [], 1, "", '<results version="2"><errors/></results>',
        )
        with patch("metricas.bugs_smells.LocalizadorHerramientas.buscar_ejecutable", return_value="cppcheck"), \
             patch("metricas.bugs_smells.subprocess.run", return_value=resultado):
            with self.assertRaisesRegex(RuntimeError, "no completó"):
                AnalizadorCppcheck.ejecutar_cppcheck(self.codigo, self.proyecto.codigo, self.logger)

    @unittest.skipUnless(HAY_LIZARD, "Lizard no instalado")
    def test_lizard_real_incluye_ino_cpp_h_y_conserva_rutas(self):
        """La complejidad incluye sketches, fuentes y funciones de cabeceras."""
        archivo = EjecutorLizard.ejecutar_lizard_csv(
            self.proyecto, "wiring", self.configuracion["carpeta_resultados"], self.logger,
        )
        complejidad = ProcesadorComplejidad.procesar_csv_lizard(archivo)
        self.assertEqual(complejidad.cantidad_funciones, 4)
        self.assertEqual(complejidad.ccn_promedio, 1.5)
        with archivo.open(encoding="utf-8", newline="") as entrada:
            filas = list(csv.reader(entrada))
        self.assertEqual({fila[6] for fila in filas}, {
            str(self.codigo / "main.ino"), str(self.codigo / "control.cpp"),
            str(self.codigo / "control.h"),
        })
        self.assertNotIn("metricas_wiring_", archivo.read_text(encoding="utf-8"))

    @unittest.skipUnless(HAY_LIZARD, "Lizard no instalado")
    def test_mi_wiring_no_mezcla_archivos_homonimos(self):
        """El MI de Wiring distingue las rutas y mantiene el contrato histórico."""
        otra = self.codigo / "otra"
        otra.mkdir()
        (otra / "control.cpp").write_text("int otro() { return 1; }", encoding="utf-8")
        entrada = self.crear_entrada([self.proyecto])
        with patch("procesamiento.procesamiento.analizar_bugs_smells_seguro"):
            resultado = procesar_proyecto(
                self.proyecto, self.configuracion, self.logger, entrada, Semaphore(1),
            )
        self.assertEqual(len(resultado.metricas_mi.archivos), 4)
        archivo = Path(self.configuracion["carpeta_resultados"]) / "E1-ESP-M_lizard.csv"
        self.assertEqual(len(LectorLizardMantenibilidad.parse_lizard_csv_mi(archivo)), 3)

    @unittest.skipUnless(HAY_LIZARD, "Lizard no instalado")
    def test_cppcheck_ausente_conserva_las_demas_metricas(self):
        """La falta de Cppcheck no elimina los resultados de Lizard ni CPU/memoria."""
        entrada = self.crear_entrada([self.proyecto])
        with patch("metricas.bugs_smells.LocalizadorHerramientas.buscar_ejecutable", side_effect=RuntimeError(
            "Cppcheck ausente en PATH"
        )):
            resultado = procesar_proyecto(
                self.proyecto, self.configuracion, self.logger, entrada, Semaphore(1),
            )
        self.assertIsNotNone(resultado.metricas_cc)
        self.assertIsNotNone(resultado.metricas_mi)
        self.assertIsNotNone(resultado.metricas_cpu_memoria)
        self.assertIsNone(resultado.metricas_bugs_smells)
        self.assertTrue(any("Cppcheck ausente" in error for error in resultado.errores))

    @unittest.skipUnless(HAY_LIZARD and HAY_CPPCHECK, "Lizard o Cppcheck no instalado")
    def test_integracion_real_excel_compartido_y_actualizacion(self):
        """Wiring manual y Vibecoding llegan al Excel junto a una fila Android."""
        vibecoding = Proyecto(
            "E1-ESP-V", "Sensor", str(self.codigo), "IA", "Modelo", "Wiring",
            "E1", "ESP32", "Vibecoding",
        )
        android = Proyecto(
            "E1-AND-M", "Android", "ruta", "No aplica", "No aplica", "Java",
            "E1", "Android", "Manual",
        )
        entrada = self.crear_entrada([self.proyecto, vibecoding])
        archivo = self.raiz / "metricas_resultados_esp32_android.xlsx"
        libro = GestorLibrosExcel().crear_o_abrir_salida(archivo, d.HOJAS_REPORTE_DISPOSITIVOS)
        self.addCleanup(libro.close)
        EscritorResultadosExcel().guardar_resultado(
            libro, android, MetricaComplejidad(1, 1, 1, 1, 1), None,
        )
        originales = {ruta: ruta.read_bytes() for ruta in self.codigo.iterdir()}
        for proyecto in (self.proyecto, vibecoding):
            resultado = procesar_proyecto(
                proyecto, self.configuracion, self.logger, entrada, Semaphore(1),
            )
            self.assertEqual(resultado.errores, [])
            self.assertEqual(resultado.metricas_cc.cantidad_funciones, 4)
            self.assertEqual(resultado.metricas_bugs_smells.analizador, "Cppcheck")
            self.assertIn("Cobertura limitada", resultado.metricas_bugs_smells.observacion)
            self.assertIn("zerodiv", dict(resultado.metricas_bugs_smells.top_reglas_violadas))
            if proyecto.implementacion == "Manual":
                self.assertIsNone(resultado.metricas_concurrencia)
                self.assertIsNone(resultado.metricas_tokens)
            else:
                self.assertEqual(resultado.metricas_concurrencia.promedio, 3)
                self.assertGreater(resultado.metricas_tokens.eficiencia_ponderada, 0)
            guardar_resultado_proyecto(libro, resultado, self.logger)
            guardar_resultado_proyecto(libro, resultado, self.logger)
        GestorLibrosExcel().finalizar(libro, archivo, tipo_proyecto=d.TIPO_PROYECTO_ESP32_ANDROID)
        verificacion = load_workbook(archivo)
        self.addCleanup(verificacion.close)
        resumen = verificacion[d.HOJA_RESUMEN]
        self.assertEqual(resumen.max_row, 4)
        columnas = UtilidadesExcel().obtener_mapa_encabezados(resumen)
        self.assertEqual(resumen.cell(2, columnas[d.TEXTO_ENCABEZADO_LENGUAJE]).value, "Java")
        self.assertEqual(resumen.cell(3, columnas[d.TEXTO_ENCABEZADO_LENGUAJE]).value, "Wiring")
        self.assertEqual(GeneradorComparaciones().obtener_parejas_completas(resumen, columnas), [
            ("E1", "ESP32", "E1-ESP-M", "E1-ESP-V"),
        ])
        self.assertEqual(verificacion[d.HOJA_BUGS_SMELLS].max_row, 3)
        self.assertIn("Arduino.h", verificacion[d.HOJA_BUGS_SMELLS].cell(2, 8).value)
        resumen_txt = Path(self.configuracion["carpeta_resultados"]) / (
            "E1-ESP-M_resumen_bugs_smells.txt"
        )
        self.assertIn("Cobertura limitada", resumen_txt.read_text(encoding="utf-8"))
        self.assertEqual(verificacion[d.HOJA_ERRORES].max_row, 1)
        self.assertEqual(len(verificacion[d.HOJA_GRAFICOS]._charts), 4)
        self.assertEqual(originales, {ruta: ruta.read_bytes() for ruta in self.codigo.iterdir()})


if __name__ == "__main__":
    unittest.main()
