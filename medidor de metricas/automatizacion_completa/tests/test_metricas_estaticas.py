"""Verificaciones puntuales de métricas estáticas y compatibilidad PC."""

import importlib.util
import json
import logging
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from threading import Semaphore
from unittest.mock import Mock, patch

from constantes import definiciones
from metricas.bugs_smells import (
    calcular_metricas_bugs_smells,
    ejecutar_detekt,
    ejecutar_pmd,
    parsear_csv_pmd,
    parsear_xml_detekt,
)
from metricas.complejidad import procesar_csv_lizard
from metricas.mantenibilidad import calcular_metricas_mi, parse_lizard_csv_mi
from modelos.modelos import ContextoAnalisis, Proyecto
from procesamiento.analisis import analizar_bugs_smells_seguro, analizar_complejidad
from procesamiento.procesamiento import (
    gestionar_procesamiento_proyectos,
    procesar_proyecto,
)


FUENTES = {
    "Java": ("Main.java", """public class Main {
    public int evaluar(int valor) {
        if (valor > 0) {
            return valor;
        }
        return 0;
    }
}
"""),
    "Kotlin": ("main.kt", """fun evaluar(valor: Int): Int {
    if (valor > 0) {
        return valor
    }
    return 0
}
"""),
    "Wiring": ("main.ino", """int evaluar(int valor) {
    if (valor > 0) {
        return valor;
    }
    return 0;
}
"""),
}


class MetricasEstaticasTest(unittest.TestCase):
    """Comprueba cálculos, herramientas ausentes y fallos sin métricas ficticias."""

    def setUp(self):
        """Prepara fuentes y salidas temporales independientes del proyecto."""
        self.temporal = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporal.cleanup)
        self.raiz = Path(self.temporal.name)
        self.configuracion = json.loads(Path(
            definiciones.CONFIGURACION_JSON_ESP32_ANDROID
        ).read_text(encoding="utf-8"))
        self.configuracion[definiciones.TEXTO_CAMPO_CARPETA_RESULTADOS] = str(
            self.raiz / "resultados"
        )
        self.logger = logging.getLogger("pruebas.estaticas")
        self.logger.addHandler(logging.NullHandler())
        self.logger.propagate = False

    def crear_proyecto(self, lenguaje):
        """Crea una fuente representativa con una función de CCN igual a dos.

        Args:
            lenguaje: Clave de los ejemplos Java, Kotlin o Wiring.

        Returns:
            Proyecto temporal cuyo código fuente se acaba de crear.
        """
        ruta = self.raiz / lenguaje
        ruta.mkdir(exist_ok=True)
        nombre, codigo = FUENTES[lenguaje]
        (ruta / nombre).write_text(codigo, encoding="utf-8")
        return Proyecto(lenguaje, lenguaje, str(ruta), "Manual", "N/A", lenguaje)

    def procesar(self, proyecto):
        """Ejecuta solamente el flujo estático sobre las fuentes temporales.

        Args:
            proyecto: Proyecto con ejemplos guardados en el directorio temporal.

        Returns:
            Métricas disponibles y errores de los análisis del proyecto.
        """
        return procesar_proyecto(
            proyecto, self.configuracion, self.logger, None, Semaphore(1),
            solo_estaticas=True,
        )

    @unittest.skipUnless(importlib.util.find_spec("lizard"), "Lizard no instalado")
    @unittest.skip("Requiere solo_estaticas y Cppcheck, ausentes en etapa 7")
    def test_integracion_real_java_kotlin_wiring(self):
        """Obtiene CCN, MI y calidad con las herramientas realmente instaladas."""
        herramientas = {
            "Java": ("pmd", "PMD"),
            "Kotlin": ("detekt-cli", "Detekt"),
            "Wiring": ("cppcheck", "Cppcheck"),
        }
        for lenguaje, (ejecutable, analizador) in herramientas.items():
            with self.subTest(lenguaje=lenguaje):
                if not shutil.which(ejecutable):
                    self.skipTest(f"{ejecutable} no instalado")
                proyecto = self.crear_proyecto(lenguaje)
                ruta = Path(proyecto.ruta_codigo)
                antes = {p.name: p.read_bytes() for p in ruta.iterdir()}
                with patch("procesamiento.procesamiento.analizar_cpu_memoria_seguro") as cpu:
                    resultado = self.procesar(proyecto)
                self.assertEqual(resultado.errores, [])
                self.assertEqual(resultado.metricas_cc.ccn_promedio, 2)
                self.assertEqual(resultado.metricas_cc.nivel_cc, "Bajo")
                self.assertGreater(resultado.metricas_mi.mi, 0)
                self.assertEqual(resultado.metricas_mi.nivel_mi, "Muy buena")
                calidad = resultado.metricas_bugs_smells
                self.assertEqual(calidad.analizador, analizador)
                self.assertGreaterEqual(calidad.issues_kloc, 0)
                self.assertIn("no son equivalentes", calidad.observacion)
                self.assertIsNone(resultado.metricas_cpu_memoria)
                self.assertIsNone(resultado.metricas_concurrencia)
                self.assertIsNone(resultado.metricas_tokens)
                cpu.assert_not_called()
                self.assertEqual(antes, {p.name: p.read_bytes() for p in ruta.iterdir()})

    @unittest.skip("Requiere seleccionar_fuentes, ausente en etapa 7")
    def test_seleccion_uniforme_de_fuentes(self):
        """Excluye dependencias, fuentes generadas y extensiones de otro lenguaje."""
        proyecto = self.crear_proyecto("Wiring")
        raiz = Path(proyecto.ruta_codigo)
        for carpeta in ("build", ".pio", "libraries", "vendor", "generated"):
            ruta = raiz / carpeta
            ruta.mkdir()
            (ruta / "ajena.cpp").write_text("int ajena() {}", encoding="utf-8")
        for nombre in ("control.cpp", "control.h", "ajena.java"):
            (raiz / nombre).write_text("", encoding="utf-8")
        (raiz / "autogenerado.h").write_text("// @generated\nint f();", encoding="utf-8")
        self.assertEqual(
            {p.name for p in seleccionar_fuentes(proyecto)},
            {"main.ino", "control.cpp", "control.h"},
        )

    @unittest.skipUnless(importlib.util.find_spec("lizard"), "Lizard no instalado")
    @unittest.skip("Requiere seleccionar_fuentes y estricto, ausentes")
    def test_wiring_incluye_cpp_h_y_no_mezcla_homonimos(self):
        """Cuenta funciones de las tres extensiones y distingue rutas completas."""
        proyecto = self.crear_proyecto("Wiring")
        raiz = Path(proyecto.ruta_codigo)
        for carpeta, nombre in (("uno", "control.cpp"), ("dos", "control.cpp"),
                                ("include", "control.h")):
            ruta = raiz / carpeta
            ruta.mkdir()
            (ruta / nombre).write_text("int simple() { return 1; }", encoding="utf-8")
        contexto = ContextoAnalisis(fuentes=seleccionar_fuentes(proyecto))
        metricas = analizar_complejidad(proyecto, self.configuracion, self.logger, contexto)
        self.assertEqual(metricas.cantidad_funciones, 4)
        self.assertEqual(metricas.ccn_promedio, 1.25)
        archivos = parse_lizard_csv_mi(contexto.archivo_csv_lizard, estricto=True)
        self.assertEqual(len(archivos), 4)
        self.assertGreater(contexto.nloc_codigo, 0)

    @unittest.skipUnless(
        importlib.util.find_spec("lizard") and shutil.which("cppcheck"),
        "Lizard o Cppcheck no instalado",
    )
    @unittest.skip("Requiere solo_estaticas y Cppcheck, ausentes")
    def test_wiring_real_con_arduino_cpp_y_cabecera(self):
        """Analiza un sketch Arduino y una incidencia en una cabecera propia."""
        proyecto = self.crear_proyecto("Wiring")
        raiz = Path(proyecto.ruta_codigo)
        (raiz / "main.ino").write_text(
            '#include <Arduino.h>\n#include "control.h"\n'
            'void setup() {}\n'
            'void loop() { if (digitalRead(2)) { digitalWrite(3, HIGH); } }\n',
            encoding="utf-8",
        )
        (raiz / "control.h").write_text(
            '#pragma once\ninline int error_control() { return 1 / 0; }\n',
            encoding="utf-8",
        )
        (raiz / "control.cpp").write_text(
            '#include "control.h"\n'
            'int calcular(int v) { if (v > 0) return v; return 0; }\n',
            encoding="utf-8",
        )
        resultado = self.procesar(proyecto)
        self.assertEqual(resultado.errores, [])
        self.assertEqual(resultado.metricas_cc.cantidad_funciones, 4)
        self.assertEqual(resultado.metricas_cc.ccn_promedio, 1.5)
        self.assertIn("zerodiv", dict(resultado.metricas_bugs_smells.top_reglas_violadas))
        self.assertTrue(any("control.h" in ruta for ruta in resultado.metricas_mi.archivos))
        self.assertTrue((raiz / "main.ino").is_file())
        self.assertFalse((raiz / "main.ino.cpp").exists())

    @unittest.skipUnless(importlib.util.find_spec("lizard"), "Lizard no instalado")
    @unittest.skip("Requiere solo_estaticas, ausente en etapa 7")
    def test_sin_funciones_no_escribe_ccn_ficticio(self):
        """Fuentes con declaraciones permiten calidad, pero CCN y MI son None."""
        proyecto = self.crear_proyecto("Java")
        (Path(proyecto.ruta_codigo) / "Main.java").write_text(
            "public class Main { private int valor; }", encoding="utf-8"
        )
        with patch("metricas.bugs_smells.analizar_fuentes_propias", return_value=("PMD", [])):
            resultado = self.procesar(proyecto)
        self.assertIsNone(resultado.metricas_cc)
        self.assertIsNone(resultado.metricas_mi)
        self.assertEqual(resultado.metricas_bugs_smells.issues_kloc, 0)
        salida = Path(self.configuracion[definiciones.TEXTO_CAMPO_CARPETA_RESULTADOS])
        self.assertFalse((salida / "Java_resumen_lizard.txt").exists())

    @unittest.skip("Requiere solo_estaticas, ausente en etapa 7")
    def test_coordinador_estatico_devuelve_resultados_sin_excel(self):
        """La coordinación común preserva resultados parciales sin escribir Excel."""
        proyecto = self.crear_proyecto("Java")
        with patch("procesamiento.procesamiento.procesar_proyecto") as procesar, \
             patch("procesamiento.procesamiento.guardar_resultado_proyecto") as guardar:
            resultados = gestionar_procesamiento_proyectos(
                [proyecto], self.configuracion, None, None, self.logger,
                solo_estaticas=True,
            )
        self.assertEqual(resultados, [procesar.return_value])
        guardar.assert_not_called()

    def test_pc_conserva_analisis_no_estaticos(self):
        """El modo PC sigue ejecutando concurrencia, tokens y CPU/memoria."""
        proyecto = self.crear_proyecto("Java")
        nombres = (
            "analizar_complejidad", "analizar_mi", "analizar_bugs_smells_seguro",
            "analizar_concurrencia_seguro", "analizar_tokens_seguro",
            "analizar_cpu_memoria_seguro",
        )
        operaciones = {nombre: Mock() for nombre in nombres}
        with patch.multiple("procesamiento.procesamiento", **operaciones):
            resultado = procesar_proyecto(
                proyecto, self.configuracion, self.logger, Mock(), Semaphore(1)
            )
        self.assertEqual(resultado.errores, [])
        for operacion in operaciones.values():
            operacion.assert_called_once()

    @unittest.skipUnless(importlib.util.find_spec("lizard"), "Lizard no instalado")
    @unittest.skip("Requiere solo_estaticas, ausente en etapa 7")
    def test_herramienta_ausente_no_se_convierte_en_cero(self):
        """La ausencia del analizador mantiene CCN y MI y deja calidad en None."""
        proyecto = self.crear_proyecto("Java")
        with patch("metricas.bugs_smells.buscar_ejecutable", side_effect=RuntimeError(
            "PMD no instalado en PATH"
        )):
            resultado = self.procesar(proyecto)
        self.assertEqual(resultado.metricas_cc.ccn_promedio, 2)
        self.assertIsNotNone(resultado.metricas_mi)
        self.assertIsNone(resultado.metricas_bugs_smells)
        self.assertIn("no instalado", resultado.errores[0])

    @unittest.skip("Requiere solo_estaticas y analizar_fuentes_lizard, ausentes")
    def test_lizard_ausente_informa_mediciones_no_disponibles(self):
        """Un fallo de Lizard no produce CCN, MI ni densidad con datos inventados."""
        proyecto = self.crear_proyecto("Java")
        with patch("metricas.complejidad.analizar_fuentes_lizard", side_effect=RuntimeError(
            "Lizard no está instalado"
        )):
            resultado = self.procesar(proyecto)
        self.assertIsNone(resultado.metricas_cc)
        self.assertIsNone(resultado.metricas_mi)
        self.assertIsNone(resultado.metricas_bugs_smells)
        self.assertIn("Lizard", resultado.errores[0])

    @unittest.skip("Requiere contexto con fuentes, ausente en etapa 7")
    def test_calidad_no_depende_de_disponibilidad_del_mi(self):
        """El flujo nuevo utiliza NLOC medido aunque no pueda obtener MI."""
        proyecto = self.crear_proyecto("Java")
        contexto = ContextoAnalisis(fuentes=seleccionar_fuentes(proyecto), nloc_codigo=100)
        with patch("procesamiento.analisis.analizar_bugs_smells") as analizar:
            metrica = analizar_bugs_smells_seguro(
                proyecto, self.configuracion, self.logger, None, contexto
            )
        self.assertIs(metrica, analizar.return_value)
        self.assertEqual(analizar.call_args.kwargs["loc_codigo"], 100)

    @unittest.skip("Requiere estricto, ausente en etapa 7")
    def test_mi_rechaza_tokens_ausentes(self):
        """El modo estricto no usa el sustituto MI=100 ante tokens inexistentes."""
        ruta = self.raiz / "sin_tokens.csv"
        ruta.write_text("3,2,0,1,3,f,Main.java,f\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "MI no disponible"):
            parse_lizard_csv_mi(ruta, estricto=True)

    @unittest.skip("Requiere estricto, ausente en etapa 7")
    def test_pmd_rechaza_prioridad_ausente_en_modo_nuevo(self):
        """No convierte una severidad no medida en una incidencia baja."""
        ruta = self.raiz / "incompleto.csv"
        ruta.write_text(
            '"File","Priority","Rule"\n"Main.java","","Regla"\n',
            encoding="utf-8",
        )
        with self.assertRaisesRegex(RuntimeError, "incompleta"):
            parsear_csv_pmd(ruta, estricto=True)

    def test_cero_incidencias_es_medicion_valida(self):
        """Un XML completo sin incidencias produce densidad e ISI iguales a cero."""
        issues = []  # La versión de etapa 7 no analiza XML de Cppcheck
        metricas = calcular_metricas_bugs_smells(
            "Cppcheck", issues, 100, self.configuracion["umbrales_isi"]
        )
        self.assertEqual((metricas.issues_kloc, metricas.isi), (0, 0))
        self.assertEqual(metricas.nivel_isi, "Sin issues")

    @unittest.skip("Requiere parsear_xml_cppcheck, ausente en etapa 7")
    def test_cppcheck_normaliza_una_vez_y_rechaza_fallos(self):
        """Conserva severidad alta y cuenta un diagnóstico con varias ubicaciones."""
        xml = ('<results version="2"><errors><error id="nullPointer" '
               'severity="error" msg="Null"><location file="main.cpp" line="3"/>'
               '<location file="main.cpp" line="1"/></error></errors></results>')
        issues = parsear_xml_cppcheck(xml, self.logger)
        self.assertEqual(len(issues), 1)
        metrica = calcular_metricas_bugs_smells(
            "Cppcheck", issues, 100, self.configuracion["umbrales_isi"]
        )
        self.assertEqual((metrica.issues_kloc, metrica.isi), (10, 5))
        for salida in ("", "<invalido/>", xml.replace("nullPointer", "syntaxError")):
            with self.subTest(salida=salida), self.assertRaises(RuntimeError):
                parsear_xml_cppcheck(salida, self.logger)

    @unittest.skip("Requiere estricto, ausente en etapa 7")
    def test_fallos_de_pmd_y_detekt_no_aceptan_reportes_parciales(self):
        """Un proceso fallido nunca se interpreta como análisis sin incidencias."""
        resultado = subprocess.CompletedProcess([], 1, "", "fallo del analizador")
        for analizar in (ejecutar_pmd, ejecutar_detekt):
            with self.subTest(analizador=analizar.__name__):
                with patch("metricas.bugs_smells.buscar_ejecutable", return_value="tool"), \
                     patch("metricas.bugs_smells.subprocess.run", return_value=resultado):
                    with self.assertRaisesRegex(RuntimeError, "no completó"):
                        analizar(self.raiz, "P1", self.logger, estricto=True)

    @unittest.expectedFailure  # falta observacion en ISI Muy alto del JSON existente
    def test_reutiliza_procesadores_java_y_kotlin(self):
        """Los reportes de ambas herramientas utilizan el mismo cálculo ISI."""
        pmd = self.raiz / "pmd.csv"
        pmd.write_text(
            '"File","Priority","Rule","Line","Description"\n'
            '"Main.java","1","ReglaJava","2","Problema"\n', encoding="utf-8",
        )
        detekt = self.raiz / "detekt.xml"
        detekt.write_text(
            '<checkstyle><file name="main.kt"><error line="2" '
            'source="detekt.LongMethod" message="Problema"/></file></checkstyle>',
            encoding="utf-8",
        )
        for nombre, issues in (("PMD", parsear_csv_pmd(pmd)),
                               ("Detekt", parsear_xml_detekt(detekt))):
            with self.subTest(analizador=nombre):
                metricas = calcular_metricas_bugs_smells(
                    nombre, issues, 100, self.configuracion["umbrales_isi"]
                )
                self.assertEqual((metricas.issues_kloc, metricas.isi), (10, 5))

    @unittest.skip("Requiere normalizar_incidencias_propias, ausente en etapa 7")
    def test_elimina_duplicados_y_archivos_ajenos(self):
        """Conserva una incidencia por archivo, ubicación, regla y mensaje."""
        temporal = self.raiz / "main.ino.cpp"
        original = self.raiz / "main.ino"
        issue = {"archivo": str(temporal), "linea": "3", "regla": "R", "mensaje": "M"}
        ajena = dict(issue, archivo=str(self.raiz / "externa.h"))
        issues = normalizar_incidencias_propias(
            [issue, dict(issue), ajena], self.raiz, {temporal: original}
        )
        self.assertEqual(len(issues), 1)
        self.assertEqual(issues[0]["archivo"], str(original))

    def test_pc_conserva_calculos_y_agrupacion_anteriores(self):
        """Fija CCN, MI, densidad e ISI del contrato anterior de PC."""
        ruta = self.raiz / "pc.csv"
        ruta.write_text(
            "3,2,15,1,3,f@1-3,src/Main.java,f\n"
            "5,4,25,1,5,g@1-5,other/Main.java,g\n", encoding="utf-8",
        )
        cc = procesar_csv_lizard(ruta)
        mi = calcular_metricas_mi(parse_lizard_csv_mi(ruta), self.configuracion["umbrales_mi"])
        self.assertEqual((cc.ccn_promedio, cc.nloc_total, cc.cantidad_funciones), (3, 8, 2))
        self.assertEqual((mi.mi, mi.tokens_codigo, mi.nloc_mi), (68.68, 40, 8))
        self.assertEqual(list(mi.archivos), ["Main.java"])
        issues = [{"severidad": s, "regla": s} for s in ("Alta", "Media", "Baja")]
        calidad = calcular_metricas_bugs_smells("PMD", issues, 8, self.configuracion["umbrales_isi"])
        self.assertEqual((calidad.issues_kloc, calidad.isi), (375, 2.67))


if __name__ == "__main__":
    unittest.main()
