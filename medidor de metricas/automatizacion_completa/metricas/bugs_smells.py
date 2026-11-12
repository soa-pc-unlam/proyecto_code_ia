"""Detección y clasificación de bugs y code smells por lenguaje."""

import csv
import os
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path, PureWindowsPath

from util.archivos import crear_directorio, validar_ruta_proyecto
from util.fuentes_wiring import obtener_inclusiones_platformio, preparar_fuentes_wiring
from constantes.definiciones import (
    ISSUE_CATEGORIA, 
    ISSUE_REGLA, ISSUE_SEVERIDAD,
    ISSUE_SEVERIDAD_ALTA,
    ISSUE_SEVERIDAD_BAJA, 
    ISSUE_SEVERIDAD_MEDIA, 
    LENGUAJE_JAVA, 
    LENGUAJE_KOTLIN,
    LENGUAJE_PYTHON,
    LENGUAJE_WIRING,
    TIEMPO_MAXIMO_ANALIZADOR,
    LIMITACIONES_CPPCHECK, 
    FALLOS_CPPCHECK, 
    SEVERIDADES_CPPCHECK, 
    CATEGORIAS_CPPCHECK
)
from configuracion.configuracion import clasificar_isi
from modelos.modelos import MetricaBugsSmells
from metricas.herramientas import EjecutorHerramientas



@dataclass
class RegistroCoberturaCppcheck:
    """Conserva todos los avisos sin inundar el log de ejecución."""
    avisos: list[tuple[str, str]] = field(default_factory=list)

    def agregar(self, regla, mensaje):
        self.avisos.append((regla, mensaje))

    def registrar_resumen(self, logger, proyecto_codigo=None):
        if not self.avisos:
            return
        prefijo = f"[{proyecto_codigo}] " if proyecto_codigo else ""
        logger.debug(
            "%sCppcheck: cobertura limitada"
            "Consultar el diagnóstico de Cppcheck para ver cada caso.",
            prefijo
        )
        for regla, mensaje in self.avisos:
            logger.debug("Cppcheck: cobertura limitada: %s: %s", regla, mensaje)

    def guardar(self, ruta):
        """Escribe el detalle completo fuera del Excel, sin eliminar duplicados."""
        ruta = Path(ruta)
        ruta.parent.mkdir(parents=True, exist_ok=True)
        cantidades = Counter(regla for regla, _ in self.avisos)
        lineas = ["Diagnóstico de cobertura Cppcheck", f"Total de advertencias: {len(self.avisos)}", ""]
        lineas.extend(f"{regla}: {total}" for regla, total in sorted(cantidades.items()))
        lineas.extend(["", "Detalle completo:"])
        lineas.extend(f"{indice}. [{regla}] {mensaje}" for indice, (regla, mensaje) in enumerate(self.avisos, 1))
        ruta.write_text("\n".join(lineas) + "\n", encoding="utf-8")


class LimitacionesCppcheck(list):
    """Lista retrocompatible con las limitaciones únicas usadas por las métricas."""
    def __init__(self):
        super().__init__()
        self.diagnostico = RegistroCoberturaCppcheck()


class FabricaAnalizadoresIncidencias:
    """Factory: selecciona la herramienta de acuerdo con el lenguaje.

    Las funciones se resuelven al invocar la fábrica para conservar las
    interfaces públicas existentes y permitir sustituirlas en las pruebas.
    """

    @staticmethod
    def obtener(lenguaje):
        """Devuelve nombre de herramienta y función que ejecuta el análisis."""
        analizadores = {
            LENGUAJE_JAVA: ("PMD", AnalizadorPMD.ejecutar_pmd),
            LENGUAJE_PYTHON: ("Pylint", AnalizadorPylint.ejecutar_pylint),
            LENGUAJE_KOTLIN: ("Detekt", AnalizadorDetekt.ejecutar_detekt),
            LENGUAJE_WIRING: ("Cppcheck", AnalizadorCppcheck.ejecutar_cppcheck),
        }
        try:
            return analizadores[lenguaje]
        except KeyError:
            raise ValueError(
                f"No hay analizador de bugs/smells para el lenguaje: {lenguaje}"
            ) from None


class AnalizadorIncidencias:
    """Coordina selección, interpretación y reporte de bugs/code smells.

    La fábrica y el ejecutor se pueden sustituir sin modificar los cálculos.
    """

    def __init__(self, fabrica=None):
        self.fabrica = fabrica if fabrica is not None else FabricaAnalizadoresIncidencias()

    def analizar(self, proyecto, lenguaje, carpeta_resultados, umbrales_isi,
                logger, loc_codigo=None, fuentes_wiring=None):
        """Analiza un proyecto y devuelve las métricas agregadas de incidencias."""
        ruta_proyecto = validar_ruta_proyecto(proyecto.ruta_codigo)
        crear_directorio(carpeta_resultados)
        archivo_txt = Path(carpeta_resultados) / (
            f"{proyecto.codigo}_resumen_bugs_smells.txt"
        )
        limitaciones = LimitacionesCppcheck() if lenguaje == LENGUAJE_WIRING else []
        analizador, ejecutar = self.fabrica.obtener(lenguaje)
        if lenguaje == LENGUAJE_WIRING:
            if fuentes_wiring is None:
                issues = ejecutar(ruta_proyecto, proyecto.codigo, logger, limitaciones)
            else:
                issues = ejecutar(
                    ruta_proyecto, proyecto.codigo, logger, limitaciones,
                    fuentes_wiring=fuentes_wiring,
                )
        else:
            issues = ejecutar(ruta_proyecto, proyecto.codigo, logger)

        loc = loc_codigo if loc_codigo is not None else 0
        metricas = CalculadoraIncidencias.calcular_metricas_bugs_smells(
            analizador, issues, loc, umbrales_isi
        )
        if lenguaje == LENGUAJE_WIRING:
            ruta_diagnostico = Path(carpeta_resultados) / f"{proyecto.codigo}_diagnostico_cppcheck.txt"
            limitaciones.diagnostico.guardar(ruta_diagnostico)
        if limitaciones:
            metricas.observacion += " Cobertura limitada"
            metricas.top_reglas_violadas.append(
                ("Includes faltantes: " + "; ".join(limitaciones), None)
            )

        GeneradorResumenIncidencias.generar_resumen_bugs_smells_txt(
            proyecto, metricas, loc, issues, archivo_txt
        )
        return metricas




class LocalizadorHerramientas:
    """Responsabilidad específica del análisis; métodos invocables sin estado compartido."""
    @staticmethod
    def buscar_ejecutable(candidatos):
        """Busca el primer ejecutable disponible entre varios candidatos.

        Args:
            candidatos: Nombres de ejecutables aceptados.

        Returns:
            La ruta o el nombre del ejecutable encontrado.

        Raises:
            RuntimeError: Si ningún candidato está disponible.
        """
        for candidato in candidatos:
            ejecutable = shutil.which(candidato)
            if ejecutable:
                return ejecutable

        raise RuntimeError(f"No se encontro ninguno de estos ejecutables en PATH: {', '.join(candidatos)}")



class AnalizadorCppcheck:
    """Responsabilidad específica del análisis; métodos invocables sin estado compartido."""
    @staticmethod
    def ejecutar_cppcheck(
        ruta_proyecto, proyecto_codigo, logger, limitaciones=None, fuentes_wiring=None
    ):
        """Ejecuta Cppcheck sobre copias Wiring y devuelve incidencias originales.

        Args:
            ruta_proyecto: Raíz del código Wiring propio.
            proyecto_codigo: Identificador utilizado en los registros.
            logger: Registrador de eventos y limitaciones de cobertura.
            limitaciones: Lista opcional de cabeceras y configuraciones no resueltas.

        Returns:
            Incidencias normalizadas con las rutas del proyecto original.

        Raises:
            RuntimeError: Si falta Cppcheck, falla el proceso o el XML es incompleto.
            subprocess.TimeoutExpired: Si se supera el tiempo máximo de análisis.
        """
        ejecutable = LocalizadorHerramientas.buscar_ejecutable(["cppcheck", "cppcheck.exe"])

        with preparar_fuentes_wiring(ruta_proyecto, fuentes_wiring) as (raiz, rutas_originales):
            comando = [ejecutable, "--language=c++", "--platform=unspecified",
                        "--enable=warning,style,performance,portability,information,missingInclude",
                        "--xml", "--xml-version=2", "--quiet", "--file-list=-", "-I", str(raiz),
                       ]
    
            AnalizadorCppcheck.obtener_bibliotecas_include(ruta_proyecto, logger, raiz, comando)
    
            logger.debug(f"[{proyecto_codigo}] Cppcheck: {' '.join(comando)}")
    
            resultado = EjecutorHerramientas().ejecutar(
                comando, entrada="\n".join(str(ruta) for ruta in rutas_originales),
                timeout=TIEMPO_MAXIMO_ANALIZADOR,
            )
            if resultado.returncode != 0:
                raise RuntimeError(f"Cppcheck no completó el análisis: {resultado.stderr}")
        
            return AnalizadorCppcheck.parsear_xml_cppcheck(resultado.stderr, logger, rutas_originales, limitaciones,
                                        proyecto_codigo=proyecto_codigo)

    @staticmethod
    def obtener_bibliotecas_include(ruta_proyecto, logger, raiz, comando):
        if (raiz / "include").is_dir():
            comando.extend(["-I", str(raiz / "include")])
    
        original = Path(ruta_proyecto).resolve()
    
        for directorio in obtener_inclusiones_platformio(original, logger):
            if directorio.is_relative_to(original):
                copia = raiz / directorio.relative_to(original)
                if copia.is_dir():
                    directorio = copia
            comando.extend(["-I", str(directorio)])

    @staticmethod
    def parsear_xml_cppcheck(salida, logger, rutas_originales=None, limitaciones=None,
                             proyecto_codigo=None):
        """Convierte cada diagnóstico XML válido en una única incidencia.

        Args:
            salida: XML de Cppcheck obtenido de stderr.
            logger: Registrador de diagnósticos informativos de cobertura.
            rutas_originales: Mapa opcional de rutas temporales a originales.
            limitaciones: Lista opcional de cabeceras y configuraciones no resueltas.

        Returns:
            Incidencias de fuentes propias, sin duplicados por inclusión de cabeceras.
            Las cabeceras no resueltas se registran sin invalidar el resultado.

        Raises:
            RuntimeError: Si el XML es inválido o el análisis quedó incompleto.
        """
        try:
            raiz = ET.fromstring(salida)
        except ET.ParseError as error:
            raise RuntimeError("Cppcheck no generó un reporte XML válido") from error
    
        errores = raiz.find("errors")
        if raiz.tag != "results" or raiz.get("version") != "2" or errores is None:
            raise RuntimeError("Cppcheck generó un reporte XML incompleto")
        issues = []
        registrados = set()
        diagnostico = (limitaciones.diagnostico
                       if isinstance(limitaciones, LimitacionesCppcheck)
                       else RegistroCoberturaCppcheck())
        AnalizadorCppcheck.obtener_issues_cppcheck(logger, rutas_originales, limitaciones, errores, issues, registrados,
                                diagnostico=diagnostico)
        diagnostico.registrar_resumen(logger, proyecto_codigo=proyecto_codigo)
        return issues

    @staticmethod
    def obtener_issues_cppcheck(logger, rutas_originales, limitaciones, errores, issues, registrados, diagnostico=None):
        for error in errores.findall("error"):
            regla = error.get("id", "")
            severidad = error.get("severity", "")
            mensaje = error.get("msg", "")
        
            if regla in FALLOS_CPPCHECK:
                raise RuntimeError(f"Cppcheck no completó el análisis: {regla}: {mensaje}")
        
            if regla in LIMITACIONES_CPPCHECK:
                if diagnostico is not None:
                    diagnostico.agregar(regla, mensaje)
                else:
                    logger.warning(f"Cppcheck: cobertura limitada: {regla}: {mensaje}")
                if limitaciones is not None and mensaje not in limitaciones:
                    limitaciones.append(mensaje)
                continue
        
            if severidad == "information":
                AnalizadorCppcheck.log_result_cppcheck(logger, regla, mensaje)
                continue
            
            if not regla or severidad not in SEVERIDADES_CPPCHECK:
                raise RuntimeError("Cppcheck devolvió un diagnóstico incompleto")
        
            ubicacion = error.find("location")
        
            if rutas_originales is not None and ubicacion is not None and ubicacion.get("file"):
                if Path(ubicacion.get("file", "")) not in rutas_originales:
                    logger.debug(f"Cppcheck: se omite diagnóstico externo: {regla}: {mensaje}")
                    continue
        
            issue = AnalizadorCppcheck.normalizar_incidencia_cppcheck(error, rutas_originales)
            clave = (issue["archivo"], issue["linea"], issue["columna"], regla, mensaje)
        
            if clave not in registrados:
                registrados.add(clave)
                issues.append(issue)

    @staticmethod
    def log_result_cppcheck(logger, regla, mensaje):
        if regla == "checkersReport":
            logger.debug(f"Cppcheck: {regla}: {mensaje}")
        elif regla == "normalCheckLevelMaxBranches":
            logger.debug(f"Cppcheck: {regla}: {mensaje}")
        else:
            logger.warning(f"Cppcheck: {regla}: {mensaje}")

    @staticmethod
    def normalizar_incidencia_cppcheck(error, rutas_originales=None):
        """Traduce la ubicación, categoría y severidad de un diagnóstico validado.

        Args:
            error: Elemento XML con regla y severidad ya comprobadas.
            rutas_originales: Mapa opcional de rutas temporales a originales.

        Returns:
            Diccionario compatible con los cálculos y reportes de incidencias.

        Raises:
            RuntimeError: Si falta la ubicación o no pertenece a las fuentes copiadas.
        """
        regla = error.get("id")
        severidad = error.get("severity")
        ubicacion = error.find("location")
    
        if ubicacion is None or not ubicacion.get("file"):
            raise RuntimeError(f"Cppcheck no indicó la ubicación de {regla}")
        ruta = Path(ubicacion.get("file"))
    
        if rutas_originales is not None:
            if ruta not in rutas_originales:
                raise RuntimeError(f"Cppcheck devolvió una ruta no analizada: {ruta}")
            ruta = rutas_originales[ruta]
    
        return {
            "archivo": str(ruta), "linea": ubicacion.get("line", ""),
            "columna": ubicacion.get("column", ""), "regla": regla,
            "mensaje": error.get("msg", ""), "severidad": SEVERIDADES_CPPCHECK[severidad],
            "categoria": CATEGORIAS_CPPCHECK[severidad],
        }



class AnalizadorPMD:
    """Responsabilidad específica del análisis; métodos invocables sin estado compartido."""
    @staticmethod
    def ejecutar_pmd(ruta_proyecto, proyecto_codigo, logger):
        """Ejecuta PMD sobre un proyecto y devuelve sus incidencias.

        Args:
            ruta_proyecto: Ruta del código fuente.
            proyecto_codigo: Código del proyecto usado en los registros.
            logger: Logger utilizado para registrar la ejecución.

        Returns:
            Lista de incidencias normalizadas.

        Raises:
            RuntimeError: Si falta el ejecutable o el analizador no puede generar el reporte.
            subprocess.TimeoutExpired: Si el análisis supera los 300 segundos.
        """
        ejecutable = LocalizadorHerramientas.buscar_ejecutable(["pmd", "pmd.bat", "pmd.cmd"])

        with tempfile.NamedTemporaryFile(delete=False, suffix=".csv") as temporal:
            archivo_csv = Path(temporal.name)

        comando = [
            ejecutable,
            "check",
            "-d",
            str(ruta_proyecto),
            "-R",
            "rulesets/java/quickstart.xml",
            "-f",
            "csv",
            "-r",
            str(archivo_csv),
        ]

        logger.debug(f"[{proyecto_codigo}] PMD: {' '.join(comando)}")
        try:
            resultado = EjecutorHerramientas().ejecutar(comando, timeout=300)
            if not archivo_csv.exists() or (
                resultado.returncode != 0 and archivo_csv.stat().st_size == 0
            ):
                raise RuntimeError(f"PMD no generó el archivo CSV. {resultado.stderr}")
            return AnalizadorPMD.parsear_csv_pmd(archivo_csv)
        finally:
            archivo_csv.unlink(missing_ok=True)

    @staticmethod
    def parsear_csv_pmd(archivo_csv):
        """Convierte un reporte CSV de PMD en incidencias normalizadas.

        Args:
            archivo_csv: Ruta del reporte de PMD.

        Returns:
            Lista de incidencias detectadas.
        """
        issues = []

        with open(archivo_csv, "r", encoding="utf-8", errors="ignore", newline="") as archivo:
            reader = csv.DictReader(archivo)

            for row in reader:
                prioridad = AnalizadorPMD.obtener_campo(row, ["Priority", "priority"])
                regla = AnalizadorPMD.obtener_campo(row, ["Rule", "rule"]) or "Regla desconocida"
                categoria = AnalizadorPMD.obtener_campo(row, ["Rule set", "RuleSet", "ruleset", "Category"]) or "Sin categoria"

                issues.append(
                    {
                        "archivo": AnalizadorPMD.obtener_campo(row, ["File", "file"]),
                        "linea": AnalizadorPMD.obtener_campo(row, ["Line", "line", "Begin Line"]),
                        "columna": AnalizadorPMD.obtener_campo(row, ["Column", "column", "Begin Column"]),
                        "regla": regla,
                        "mensaje": AnalizadorPMD.obtener_campo(row, ["Description", "description", "Problem"]),
                        "severidad": AnalizadorPMD.clasificar_severidad_pmd(prioridad),
                        "categoria": categoria,
                    }
                )

        return issues

    @staticmethod
    def obtener_campo(row, posibles_nombres):
        """Obtiene el primer campo disponible de una fila.

        Args:
            row: Fila representada como diccionario.
            posibles_nombres: Nombres alternativos del campo.

        Returns:
            El valor encontrado o una cadena vacía.
        """
        for nombre in posibles_nombres:
            if nombre in row:
                return row[nombre]
        return ""

    @staticmethod
    def clasificar_severidad_pmd(prioridad):
        """Traduce una prioridad de PMD a una severidad normalizada.

        Args:
            prioridad: Prioridad informada por PMD.

        Returns:
            Severidad alta, media o baja.
        """
        try:
            prioridad = int(prioridad)
        except (TypeError, ValueError):
            return "Baja"

        if prioridad in {1, 2}:
            return "Alta"
        if prioridad == 3:
            return "Media"
        return "Baja"



class AnalizadorPylint:
    """Responsabilidad específica del análisis; métodos invocables sin estado compartido."""
    @staticmethod
    def ejecutar_pylint(ruta_proyecto, proyecto_codigo, logger):
        """Ejecuta Pylint y devuelve sus mensajes normalizados.

        Args:
            ruta_proyecto: Ruta del código fuente.
            proyecto_codigo: Código del proyecto usado en los registros.
            logger: Logger utilizado para registrar la ejecución.

        Returns:
            Lista de incidencias detectadas.

        Raises:
            RuntimeError: Si falta el ejecutable o el analizador no puede generar el reporte.
            subprocess.TimeoutExpired: Si el análisis supera los 300 segundos.
        """
        ejecutable = LocalizadorHerramientas.buscar_ejecutable(["pylint"])
        comando = [ejecutable, str(ruta_proyecto), "--output-format=text"]

        logger.debug(f"[{proyecto_codigo}] Pylint: {' '.join(comando)}")
        resultado = EjecutorHerramientas().ejecutar(comando, timeout=300)
        if resultado.returncode >= 32:
            raise RuntimeError(f"Pylint no pudo ejecutar el análisis: {resultado.stderr}")
        return AnalizadorPylint.parsear_salida_pylint(resultado.stdout + "\n" + resultado.stderr)

    @staticmethod
    def parsear_salida_pylint(salida):
        """Convierte la salida textual de Pylint en incidencias.

        Args:
            salida: Texto producido por Pylint.

        Returns:
            Lista de incidencias normalizadas.
        """
        patron = re.compile(r"^(.*?):(\d+):(\d+): ([CRWEF])(\d+): (.*?) \((.*?)\)")
        tipos = {
            "C": "convention",
            "R": "refactor",
            "W": "warning",
            "E": "error",
            "F": "fatal",
        }

        issues = []
        for linea in salida.splitlines():
            match = patron.match(linea)
            if not match:
                continue

            archivo, linea_num, columna, codigo_tipo, codigo, mensaje, regla = match.groups()
            tipo = tipos.get(codigo_tipo, "unknown")
            issues.append(
                {
                    "archivo": archivo,
                    "linea": linea_num,
                    "columna": columna,
                    "tipo": tipo,
                    "codigo": codigo_tipo + codigo,
                    "mensaje": mensaje,
                    "regla": regla,
                    "severidad": AnalizadorPylint.clasificar_severidad_pylint(tipo),
                    "categoria": AnalizadorPylint.clasificar_categoria_pylint(tipo),
                }
            )

        return issues

    @staticmethod
    def clasificar_severidad_pylint(tipo):
        """Asigna una severidad a una categoría de Pylint.

        Args:
            tipo: Categoría textual de Pylint.

        Returns:
            Severidad alta, media o baja.
        """
        if tipo in {"fatal", "error"}:
            return "Alta"
        if tipo in {"warning", "refactor"}:
            return "Media"
        return "Baja"

    @staticmethod
    def clasificar_categoria_pylint(tipo):
        """Asigna una categoría funcional a un tipo de Pylint.

        Args:
            tipo: Categoría textual de Pylint.

        Returns:
            Categoría normalizada de la incidencia.
        """
        if tipo == "convention":
            return "Estilo"
        if tipo == "refactor":
            return "Diseno"
        if tipo in {"warning", "error", "fatal"}:
            return "Bug potencial"
        return "Documentacion"



class AnalizadorDetekt:
    """Responsabilidad específica del análisis; métodos invocables sin estado compartido."""
    @staticmethod
    def ejecutar_detekt(ruta_proyecto, proyecto_codigo, logger):
        """Ejecuta Detekt y devuelve sus incidencias normalizadas.

        Args:
            ruta_proyecto: Ruta del código fuente.
            proyecto_codigo: Código del proyecto usado en los registros.
            logger: Logger utilizado para registrar la ejecución.

        Returns:
            Lista de incidencias detectadas.

        Raises:
            RuntimeError: Si falta el ejecutable o Detekt no genera un XML no vacío.
            ET.ParseError: Si el reporte generado no es un XML válido.
            subprocess.TimeoutExpired: Si el análisis supera los 300 segundos.
        """
        ejecutable = LocalizadorHerramientas.buscar_ejecutable(["detekt-cli", "detekt-cli.bat", "detekt"])

        with tempfile.NamedTemporaryFile(delete=False, suffix=".detekt.xml") as temporal:
            archivo_xml = Path(temporal.name)

        comando = [
            ejecutable,
            "--input",
            str(ruta_proyecto),
            "--report",
            f"xml:{archivo_xml}",
        ]

        logger.debug(f"[{proyecto_codigo}] Detekt: {' '.join(comando)}")
        try:
            resultado = EjecutorHerramientas().ejecutar(comando, timeout=300)
            if not archivo_xml.exists() or archivo_xml.stat().st_size == 0:
                raise RuntimeError(f"Detekt no generó el archivo XML. {resultado.stderr}")
            return AnalizadorDetekt.parsear_xml_detekt(archivo_xml)
        finally:
            archivo_xml.unlink(missing_ok=True)

    @staticmethod
    def parsear_xml_detekt(archivo_xml):
        """Convierte un reporte XML de Detekt en incidencias.

        Args:
            archivo_xml: Ruta del reporte XML.

        Returns:
            Lista de incidencias normalizadas.
        """
        tree = ET.parse(archivo_xml)
        root = tree.getroot()
        issues = []

        for file_node in root.findall("file"):
            nombre_archivo = file_node.attrib.get("name", "")

            for error in file_node.findall("error"):
                regla = error.attrib.get("source", "Regla desconocida")
                issues.append(
                    {
                        "archivo": nombre_archivo,
                        "linea": error.attrib.get("line", ""),
                        "columna": error.attrib.get("column", ""),
                        "regla": regla,
                        "mensaje": error.attrib.get("message", ""),
                        "severidad": AnalizadorDetekt.clasificar_severidad_detekt(regla),
                        "categoria": AnalizadorDetekt.clasificar_categoria_detekt(regla),
                    }
                )

        return issues

    @staticmethod
    def clasificar_severidad_detekt(regla):
        """Asigna una severidad a una regla de Detekt.

        Args:
            regla: Identificador de la regla.

        Returns:
            Severidad alta, media o baja.
        """
        regla = regla.lower()
        alta = [
            "complexmethod",
            "largeclass",
            "longmethod",
            "toomanyfunctions",
            "nestedblockdepth",
            "cognitivecomplexmethod",
            "throwscount",
            "swallowedexception",
            "toogenericexceptioncaught",
            "unsafe",
        ]
        media = [
            "magicnumber",
            "unused",
            "empty",
            "returncount",
            "cyclomaticcomplexmethod",
            "longparameterlist",
        ]

        if any(valor in regla for valor in alta):
            return ISSUE_SEVERIDAD_ALTA
        if any(valor in regla for valor in media):
            return ISSUE_SEVERIDAD_MEDIA
        return ISSUE_SEVERIDAD_BAJA

    @staticmethod
    def clasificar_categoria_detekt(regla):
        """Asigna una categoría funcional a una regla de Detekt.

        Args:
            regla: Identificador de la regla.

        Returns:
            Categoría normalizada de la incidencia.
        """
        regla = regla.lower()

        if any(valor in regla for valor in ["style", "naming", "spacing", "format"]):
            return "Estilo"
        if any(valor in regla for valor in ["complex", "long", "large", "nested", "many", "coupling"]):
            return "Diseno / mantenibilidad"
        if any(valor in regla for valor in ["empty", "unused", "null", "exception", "unsafe", "unreachable"]):
            return "Bug potencial"
        if any(valor in regla for valor in ["comment", "documentation", "kdoc"]):
            return "Documentacion"
        return "Otros"



class CalculadoraIncidencias:
    """Responsabilidad específica del análisis; métodos invocables sin estado compartido."""
    @staticmethod
    def calcular_metricas_bugs_smells(analizador, issues, loc, umbrales_isi):
        """Agrega incidencias en métricas comparables de calidad.

        Args:
            analizador: Nombre del analizador utilizado.
            issues: Incidencias detectadas.
            loc: Cantidad de líneas de código analizadas.
            umbrales_isi: Umbrales del índice de severidad.

        Returns:
            Las métricas agregadas de bugs y code smells.
        """
        total_issues = len(issues)
        issues_kloc = round((total_issues / loc) * 1000, 2) if loc > 0 else 0.0
    
        severidades = Counter(issue[ISSUE_SEVERIDAD] for issue in issues)
        reglas = Counter(issue[ISSUE_REGLA] for issue in issues)

        cantidad_alta = severidades[ISSUE_SEVERIDAD_ALTA]
        cantidad_media = severidades[ISSUE_SEVERIDAD_MEDIA]
        cantidad_baja = severidades[ISSUE_SEVERIDAD_BAJA]
    
        isi = CalculadoraIncidencias.calcular_isi(cantidad_alta, cantidad_media, cantidad_baja, total_issues)
        nivel_isi, interpretacion_isi,observacion = clasificar_isi(isi, umbrales_isi)

        """if cantidad_alta > 0:
            observacion = INTERPRETACION_ISSUE_ALTOS
        elif cantidad_media > 0:
            observacion= INTERPRETACION_ISSUE_MEDIOS
        else:
            observacion = INTERPRETACION_ISSUE_BAJOS
        """
        return MetricaBugsSmells(
            analizador=analizador,
            total_issues=total_issues,
            issues_kloc =issues_kloc,
            isi=isi,
            nivel_isi=nivel_isi,
            interpretacion_isi=interpretacion_isi,
            observacion=observacion,
            cantidad_baja=cantidad_baja,
            cantidad_media=cantidad_media,
            cantidad_alta=cantidad_alta,
            top_reglas_violadas=reglas.most_common(10),
        )

    @staticmethod
    def calcular_isi(cantidad_alta, cantidad_media, cantidad_baja, total_issues):
        """Calcula el índice de severidad de incidencias.

        Args:
            cantidad_alta: Cantidad de incidencias de severidad alta.
            cantidad_media: Cantidad de incidencias de severidad media.
            cantidad_baja: Cantidad de incidencias de severidad baja.
            total_issues: Cantidad total de incidencias.

        Returns:
            El índice ponderado, o cero cuando no hay incidencias.
        """
        if total_issues <= 0:
            return 0.0

        puntaje = (cantidad_alta * 5) + (cantidad_media * 2) + (cantidad_baja * 1)
        return round(puntaje / total_issues, 2)



class GeneradorResumenIncidencias:
    """Responsabilidad específica del análisis; métodos invocables sin estado compartido."""
    @staticmethod
    def generar_resumen_bugs_smells_txt(proyecto, metricas, loc, issues, archivo_txt):
        """Escribe el reporte detallado de bugs y code smells.

        Args:
            proyecto: Proyecto al que corresponde el reporte.
            metricas: Métricas agregadas de incidencias.
            loc: Cantidad de líneas analizadas.
            issues: Detalle de incidencias detectadas.
            archivo_txt: Ruta del archivo de salida.
        """
        severidades = Counter(issue[ISSUE_SEVERIDAD] for issue in issues)
        categorias = Counter(issue[ISSUE_CATEGORIA] for issue in issues)

        with open(archivo_txt, "w", encoding="utf-8") as archivo:
            archivo.write(f"REPORTE DE BUGS Y SMELLS - {metricas.analizador}\n")
            archivo.write("=" * 60 + "\n\n")

            archivo.write(f"Codigo: {proyecto.codigo}\n")
            archivo.write(f"Proyecto: {proyecto.nombre_proyecto}\n")
            archivo.write(f"Herramienta IA: {proyecto.herramienta_ia}\n")
            archivo.write(f"Modelo IA: {proyecto.modelo_ia}\n")
            archivo.write(f"Lenguaje: {proyecto.lenguaje}\n")
            archivo.write(f"LOC analizadas: {loc}\n\n")

            archivo.write("METRICAS COMPARABLES\n")
            archivo.write("-" * 60 + "\n")
            archivo.write(f"Analizador: {metricas.analizador}\n")
            archivo.write(f"Total de issues: {metricas.total_issues}\n")
            archivo.write(f"Issues/KLOC: {metricas.issues_kloc}\n")
            archivo.write(f"ISI: {metricas.isi}\n")
            archivo.write(f"Nivel de ISI: {metricas.nivel_isi}\n")
            archivo.write(f"Interpretacion de ISI: {metricas.interpretacion_isi}\n")
            archivo.write(f"Observacion: {metricas.observacion}\n\n")

            archivo.write("SEVERIDAD\n")
            archivo.write("-" * 60 + "\n")
            archivo.write(f"Alta: {severidades[ISSUE_SEVERIDAD_ALTA]}\n")
            archivo.write(f"Media: {severidades[ISSUE_SEVERIDAD_MEDIA]}\n")
            archivo.write(f"Baja: {severidades[ISSUE_SEVERIDAD_BAJA]}\n\n")

            archivo.write("CATEGORIAS\n")
            archivo.write("-" * 60 + "\n")
            for categoria, cantidad in categorias.most_common():
                archivo.write(f"{categoria}: {cantidad}\n")

            archivo.write("\nTOP REGLAS VIOLADAS\n")
            archivo.write("-" * 60 + "\n")
            for regla, cantidad in metricas.top_reglas_violadas:
                archivo.write(f"{regla}: {cantidad}\n")

            archivo.write("\nDETALLE DE ISSUES\n")
            archivo.write("-" * 60 + "\n")
            if not issues:
                archivo.write("No se detectaron issues.\n")
            else:
                for issue in issues:
                    archivo.write(
                        f"[{issue[ISSUE_SEVERIDAD]}] "
                        f"[{issue[ISSUE_CATEGORIA]}] "
                        f"{GeneradorResumenIncidencias.normalizar_ruta_salida(issue.get('archivo', ''))}:"
                        f"{issue.get('linea', '')}:{issue.get('columna', '')} "
                        f"{issue.get('regla', '')} - {issue.get('mensaje', '')}\n"
                    )

    @staticmethod
    def normalizar_ruta_salida(ruta):
        """Reduce una ruta a un nombre de archivo portable.

        Args:
            ruta: Ruta producida por una herramienta de análisis.

        Returns:
            El nombre del archivo o la ruta original si no puede reducirse.
        """
        nombre = os.path.basename(ruta)
        if nombre == ruta:
            nombre = PureWindowsPath(ruta).name
        return nombre or ruta
