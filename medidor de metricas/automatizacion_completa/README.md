# Evaluador de métricas de calidad de código

Aplicación en Python que analiza proyectos con Lizard y, según el lenguaje,
PMD, Pylint o Detekt. También incorpora una rúbrica de concurrencia y genera
un libro Excel consolidado.

## Estructura

```text
automatizacion_metricas/
├── main.py
├── clean_file.py
├── requirements.txt
├── configuracion/
├── constantes/
├── datos_entrada/
├── metricas/
├── reportes/
├── util/
└── tests/
```

Los archivos generados se escriben en `resultados/`, `logs/` y en el Excel
indicado por `archivo_excel`. Estas salidas no forman parte del código fuente.

## Instalación

```bash
python -m pip install -r requirements.txt
```

Además deben estar disponibles en `PATH`: PMD para Java, Pylint para Python y
Detekt para Kotlin.

## Configuración

1. Definir rutas, ponderaciones y umbrales en `configuracion/configuracion.json`.
2. Definir los proyectos en `datos_entrada/proyectos_pc.json`.
3. Completar la hoja `Concurrencia` de `datos_entrada/datos_entrada_pc.xlsx`.

Los códigos de proyecto deben ser únicos. Las rutas pueden contener espacios.

## Ejecución

Ejecutar desde la raíz:

```bash
python main.py
```

El programa mantiene un único libro en memoria y lo guarda al finalizar. Si un
análisis opcional falla, registra el error y conserva los demás resultados. Si
no se pueden calcular complejidad o mantenibilidad, omite el reporte completo
para no reutilizar datos de ejecuciones anteriores.

Las herramientas externas tienen un tiempo máximo de cinco minutos.

## Pruebas

```bash
python -m unittest discover -s tests -v
```

Las pruebas cubren umbrales, proyectos duplicados, concurrencia y acumulación
de errores en Excel.

## Documentación del código

Los módulos, clases y funciones se documentan en español con docstrings de
Google. La primera línea resume su propósito. Las secciones se incluyen cuando
corresponden y conservan sus nombres en inglés:

- `Args:` describe cada parámetro con el nombre exacto de la firma, incluidos
  los opcionales y el significado de sus valores predeterminados. Se omite `self`.
- `Returns:` describe el valor devuelto, sus componentes y los casos sin datos.
  Se omite en funciones que solo devuelven `None`.
- `Raises:` identifica las excepciones que se propagan al llamador y sus causas.
- `Attributes:` describe los campos de las clases de datos.

Los tipos pueden expresarse en las anotaciones de la firma o entre paréntesis
junto al parámetro. Los efectos sobre archivos, libros y registros se explican
en la descripción. Una función sencilla sin parámetros ni retorno puede usar
un docstring de una sola línea.

```python
def calcular_promedio(puntajes):
    """Calcula el promedio de una colección de puntajes.

    Args:
        puntajes (list[float]): Puntajes que se desean promediar.

    Returns:
        float: Promedio redondeado a dos decimales, o cero sin datos.
    """
```

La documentación se puede consultar con `help()` desde Python; por ejemplo,
`help(metricas.concurrencia.calcular_promedio)` tras importar el módulo.

## Etapa 3: métricas estáticas de Android y ESP32

```bash
python main_pc.py
python main_esp32_android.py
```

`main_pc.py` conserva el flujo, las fórmulas y los reportes anteriores.
`main_esp32_android.py` usa `configuracion/configuracion_esp32_android.json`
y `datos_entrada/proyectos_esp32-android.json`. En esta etapa devuelve una lista
de `ResultadoProyecto` y escribe CSV y resúmenes TXT en
`resultados/esp32_android`, con registros en `logs/esp32_android`.
No abre el Excel de entrada ni genera Excel, gráficos o métricas dinámicas.
Tampoco ejecuta las rúbricas de concurrencia y tokens.

| Lenguaje | Fuentes | Complejidad y datos del MI | Incidencias |
| --- | --- | --- | --- |
| Android Java | `.java` | Lizard, lector Java | PMD, reglas Java quickstart existentes |
| Android Kotlin | `.kt` | Lizard, lector Kotlin | Detekt, configuración predeterminada existente |
| ESP32 Wiring/Arduino | `.ino`, `.cpp`, `.h` | Lizard, lector C++ | Cppcheck: error, warning, style, performance y portability |

Lizard se usa mediante su paquete Python instalado. Para `.ino` recibe un
nombre virtual `.cpp`; los analizadores de incidencias reciben copias
temporales filtradas, manteniendo la estructura relativa. Cppcheck recibe
explícitamente también las cabeceras, con lenguaje C++ y plataforma
`unspecified`, para evitar asumir los tamaños del equipo que ejecuta Python.
Las incidencias se devuelven con sus rutas originales y sin duplicados.
Los originales no se modifican. No se instala ninguna herramienta automáticamente.

La selección común excluye directorios de compilación, generados y dependencias
(`build`, `bin`, `obj`, `out`, `dist`, `target`, `generated`, `gen`, `.pio`,
`.gradle`, `vendor`, `external`, `third_party`, `third-party`, `libraries`,
`libs`, `dependencies`, `deps`, `node_modules`, entre otros de
`util/archivos.py`). También excluye `R.java`, `BuildConfig.java` y archivos con
marcas de generación en comentarios de sus primeros 2048 caracteres. No sigue
directorios simbólicos ni fuentes que resuelvan fuera del proyecto. Las
bibliotecas copiadas a carpetas con otros nombres no pueden identificarse
automáticamente: la raíz configurada debe contener código propio.

El CCN promedio conserva el promedio por función y los clasificadores existentes.
El MI conserva exactamente la variante histórica:
`(171 - 5.2*ln(tokens) - 0.23*avgCCN - 16.2*ln(nloc)) * 100 / 171`,
limitada a `[0, 100]`, con promedio simple por archivo. Los tokens, NLOC y CCN
son mediciones de funciones de Lizard; los archivos homónimos se distinguen
por su ruta completa en este flujo. Los archivos sin funciones no participan
del MI. Esta variante **no calcula volumen Halstead ni equivale al MI clásico
que lo requiere**. El resumen TXT de MI incluye esta aclaración.

Para Issues/KLOC se usa NLOC medido por Lizard en los archivos completos,
incluidas declaraciones fuera de funciones, comentarios y blancos excluidos.
Este denominador es independiente de la disponibilidad del MI y utiliza la
misma selección de fuentes en los tres lenguajes. Se reutilizan el cálculo
de densidad, las ponderaciones ISI `5/2/1` y los umbrales configurados.
Las reglas y el mapeo de severidades PMD y Detekt se conservan. En Cppcheck:
`error` → alta; `warning`/`performance` → media; `style`/`portability` → baja.
La severidad `information` describe cobertura y se registra, sin contabilizarla
como incidencia. Las reglas, severidades y coberturas de estas herramientas
son distintas: sus densidades e índices no son equivalentes entre analizadores.

Cppcheck se ejecuta sin compilación ni SDK de ESP32. No se agregan prototipos,
macros, tipos o bibliotecas Arduino inventados. Las inclusiones de sistema
ausentes se informan como limitación; errores de sintaxis, macros desconocidas,
inclusiones locales faltantes o fallos internos invalidan la medición.
No se ejecutan reglas de Detekt que necesiten resolución de tipos mediante
classpath del proyecto. PMD con errores recuperables también se rechaza en
el flujo nuevo, aunque haya emitido resultados parciales. Ver contratos de
[PMD](https://pmd.github.io/pmd/pmd_userdocs_cli_reference.html),
[Detekt 1.23.8](https://detekt.dev/docs/1.23.8/gettingstarted/cli/) y
[Cppcheck](https://cppcheck.sourceforge.io/manual.html).

Una herramienta ausente, un reporte incompleto o datos insuficientes dejan
la métrica en `None`, con la causa en `ResultadoProyecto.errores` y el registro.
Un análisis válido sin incidencias sí devuelve Issues/KLOC e ISI iguales a cero.
Sin funciones, CCN y MI quedan no disponibles; sin NLOC positivo, la densidad
tampoco se calcula. Los umbrales se respetan: si un promedio cae en un hueco
de la configuración existente, la clasificación no está disponible y se informa
el error; no se inventa un nivel.

Verificaciones puntuales de esta etapa, sin ejecutar toda la batería:

```bash
python -m unittest discover -s tests -p test_metricas_estaticas.py -v
python -m unittest discover -s tests -p test_procesamiento_comun.py -v
python -m unittest discover -s tests -p test_main.py -v
```

Las pruebas de integración usan analizadores reales cuando están instalados;
las pruebas de herramientas ausentes y fallos emplean dobles explícitos.
Se comprueban también resultados históricos PC de CCN, MI, Issues/KLOC e ISI.
