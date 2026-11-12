"""Preparación compartida de fuentes propias Wiring para los analizadores."""

import json
import logging
import os
import shutil
from configparser import ConfigParser
from contextlib import contextmanager
from pathlib import Path
from tempfile import TemporaryDirectory
from modelos.modelos import FuentesProyecto
from util.archivos import validar_ruta_proyecto
from constantes.definiciones import (
    EXTENSIONES_WIRING,
    DIRECTORIOS_EXCLUIDOS,
    LONGITUD_CABECERA_GENERADA,
    MARCAS_GENERACION, 
)


def obtener_inclusiones_platformio(ruta_codigo: str | Path, logger=None) -> list[Path]:
    """Obtiene rutas existentes del entorno sin instalar ni compilar paquetes.

    Args:
        ruta_codigo: Raíz que puede contener platformio.ini y metadatos de VS Code.
        logger: Registrador opcional de metadatos que no pueden aprovecharse.

    Returns:
        Directorios de inclusión existentes, en orden de prioridad y sin duplicados.
        Considera los entornos declarados y los almacenes estándar de PlatformIO.
    """

    candidatos = []
    raiz = Path(ruta_codigo).resolve()
    archivo_ini = raiz / "platformio.ini"

    if not archivo_ini.is_file():
        return []
    
    configuracion = ConfigParser(interpolation=None)
    configuracion.read(archivo_ini, encoding="utf-8-sig")
    core_configurado = configuracion.get("platformio", "core_dir", fallback=str(Path.home() / ".platformio"))
    core = Path(os.environ.get("PLATFORMIO_CORE_DIR", core_configurado))
    
    if not core.is_absolute():
        core = raiz / core
    
    libdeps = configuracion.get("platformio", "libdeps_dir", fallback=".pio/libdeps")
    almacen = Path(os.environ.get("PLATFORMIO_LIBDEPS_DIR", libdeps))
    
    if not almacen.is_absolute():
        almacen = raiz / almacen
    if logger is None:
        logger = logging.getLogger(__name__)
    try:
        candidatos = leer_inclusiones_vscode(raiz)
    except (OSError, json.JSONDecodeError) as error:
        logger.warning(f"Cppcheck: no se pudieron leer las inclusiones de VS Code: {error}")
    
    obtener_candidatos(configuracion, core, almacen, candidatos)
    return list(dict.fromkeys(ruta.resolve() for ruta in candidatos if ruta.is_dir()))

def obtener_candidatos(configuracion, core, almacen, candidatos):
    predeterminados = configuracion.get("platformio", "default_envs", fallback="")
    entornos = predeterminados.replace(",", " ").split()
    
    if not entornos:
        entornos = [nombre[4:] for nombre in configuracion.sections() if nombre.startswith("env:")]
    
    for entorno in entornos:
        candidatos.extend(obtener_directorios_bibliotecas(almacen / entorno))
        seccion = f"env:{entorno}"
        framework = configuracion.get(
            seccion, "framework", fallback=configuracion.get("env", "framework", fallback="")
        )
        plataforma = configuracion.get(
            seccion, "platform", fallback=configuracion.get("env", "platform", fallback="")
        )
    
        if "arduino" in framework and plataforma.startswith("espressif32"):
            paquete = core / "packages" / "framework-arduinoespressif32"
            candidatos.append(paquete / "cores" / "esp32")
            candidatos.extend(obtener_directorios_bibliotecas(paquete / "libraries"))
    
    candidatos.extend(obtener_directorios_bibliotecas(core / "lib"))


def obtener_directorios_bibliotecas(almacen: Path) -> list[Path]:
    """Lista raíces, src e include de bibliotecas instaladas en un almacén.

    Args:
        almacen: Directorio que contiene una carpeta por biblioteca.

    Returns:
        Candidatos de inclusión sin recorrer ni seleccionar fuentes para métricas.
    """
    if not almacen.is_dir():
        return []
    directorios = []
    
    for biblioteca in sorted(almacen.iterdir()):
        if biblioteca.is_dir():
            directorios.extend([biblioteca, biblioteca / "src", biblioteca / "include"])
    
    return directorios


def leer_inclusiones_vscode(raiz: Path) -> list[Path]:
    """Lee las rutas de inclusión generadas por PlatformIO para VS Code.

    Args:
        raiz: Directorio del proyecto.

    Returns:
        Rutas concretas de includePath; omite variables que no puede resolver.
    """
    archivo = raiz / ".vscode" / "c_cpp_properties.json"
    if not archivo.is_file():
        return []
    datos = json.loads(archivo.read_text(encoding="utf-8-sig"))
    directorios = []
    for configuracion in datos.get("configurations", []):
        for valor in configuracion.get("includePath", []):
            valor = valor.replace("${workspaceFolder}", str(raiz))
            valor = valor.replace("${workspaceRoot}", str(raiz))
            if "${" in valor or "*" in valor:
                continue
            ruta = Path(valor)
            if not ruta.is_absolute():
                ruta = raiz / ruta
            directorios.append(ruta)
    return directorios



def obtener_fuentes_proyecto(ruta_codigo: str | Path) -> FuentesProyecto:
    """Selecciona las fuentes una vez y devuelve su descripción inmutable."""
    raiz = validar_ruta_proyecto(ruta_codigo).resolve()
    return FuentesProyecto(raiz, tuple(seleccionar_fuentes_wiring(raiz)))


def seleccionar_fuentes_wiring(ruta_codigo: str | Path) -> list[Path]:
    """Selecciona C/C++ y sketches propios sin dependencias ni generados.

    Args:
        ruta_codigo: Directorio raíz del código Wiring.

    Returns:
        Rutas absolutas ordenadas de las fuentes seleccionadas.

    Raises:
        ValueError: Si no hay fuentes propias compatibles.
        OSError: Si no se puede leer el código fuente.
    """
    raiz = validar_ruta_proyecto(ruta_codigo).resolve()
    fuentes = []
    for directorio, carpetas, nombres in os.walk(raiz):
        carpetas[:] = [
            nombre for nombre in carpetas
            if nombre.lower() not in DIRECTORIOS_EXCLUIDOS
            and not Path(directorio, nombre).is_symlink()
            and not Path(directorio, nombre).is_junction()
        ]
        for nombre in nombres:
            ruta = Path(directorio, nombre)
            if ruta.suffix.lower() not in EXTENSIONES_WIRING or ruta.is_symlink():
                continue
            if not ruta.resolve().is_relative_to(raiz):
                continue
            with ruta.open(encoding="utf-8", errors="replace") as archivo:
                cabecera = archivo.read(LONGITUD_CABECERA_GENERADA)
            comentarios = "\n".join(
                linea.strip().lower() for linea in cabecera.splitlines()
                if linea.strip().startswith(("//", "/*", "*"))
            )
            if any(marca in comentarios for marca in MARCAS_GENERACION):
                continue
            fuentes.append(ruta)
    if not fuentes:
        raise ValueError(f"No hay archivos Wiring en el directorio indicado")
    return sorted(fuentes)


@contextmanager
def preparar_fuentes_wiring(ruta_codigo: str | Path, fuentes: FuentesProyecto | None = None):
    """Copia fuentes a un temporal y adapta la extensión de los sketches.

    Args:
        ruta_codigo: Raíz del código original, que se conserva intacto.
        fuentes: Selección ya validada; si se omite, se selecciona localmente.

    Yields:
        Tupla con la raíz temporal y un mapa de rutas temporales a originales.
        El directorio temporal se elimina incluso si falla un analizador.
    """
    raiz = validar_ruta_proyecto(ruta_codigo).resolve()
    if fuentes is None:
        archivos = seleccionar_fuentes_wiring(raiz)
    else:
        if fuentes.raiz != raiz:
            raise ValueError("Las fuentes no pertenecen al proyecto indicado")
        archivos = list(fuentes.archivos)
    with TemporaryDirectory(prefix="metricas_wiring_") as temporal:
        destino = Path(temporal)
        rutas_originales = {}
        # Primero se conservan los nombres que pueden aparecer en los includes.
        archivos.sort(key=lambda ruta: ruta.suffix.lower() == ".ino")
        for original in archivos:
            copia = destino / original.relative_to(raiz)
            if original.suffix.lower() == ".ino":
                copia = copia.with_name(copia.name + ".cpp")
                while copia.exists():
                    copia = copia.with_name(copia.name + ".cpp")
            copia.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(original, copia)
            rutas_originales[copia] = original
        yield destino, rutas_originales
