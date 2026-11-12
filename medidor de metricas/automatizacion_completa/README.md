# Evaluador de métricas de calidad de código
 
Aplicación en Python que analiza proyectos con Lizard y, según el lenguaje,
PMD, Pylint, Detekt o Cppcheck. También incorpora una rúbrica de concurrencia y genera
un libro Excel consolidado.

## Estructura

```text
automatizacion_metricas/
├── main_pc.py
├── main_esp32_android.py
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
indicado por `archivo_excel_salida` en la configuración del perfil.

## Instalación

```bash
python -m pip install -r requirements.txt
```

Además deben estar disponibles en `PATH`: PMD para Java, Pylint para Python y
Detekt para Kotlin y Cppcheck para Wiring/Arduino. Lizard y Cppcheck ya están
disponibles en el entorno utilizado para verificar el soporte Wiring.

## Configuración

1. Definir rutas, ponderaciones y umbrales en `configuracion/configuracion_pc.json`
   o `configuracion/configuracion_esp32_android.json`.
2. Definir los proyectos en la hoja `Datos_Proyectos` de
   `datos_entrada/datos_entrada_pc.xlsx` o
   `datos_entrada/datos_entrada_esp32_android.xlsx`, según el perfil.
3. Completar CPU/memoria, Concurrencia y Datos Tokens en el mismo Excel de
   entrada. En dispositivos Manual se omiten concurrencia y tokens.

La carga identifica las columnas de `Datos_Proyectos` por su encabezado (sin
importar el orden ni las tildes). Los campos `ID Proyecto`, `Dispositivo` e
`Implementación` son opcionales para PC. Las filas vacías se ignoran; las
filas incompletas y los códigos repetidos producen un error de validación.
El libro de entrada sólo se abre en modo lectura y no se modifica.

Los códigos de proyecto deben ser únicos. Las rutas pueden contener espacios.

## Ejecución

Ejecutar desde la raíz:

```bash
python main_pc.py
python main_esp32_android.py
```

El programa mantiene un único libro en memoria y lo guarda al finalizar. Si un
análisis opcional falla, registra el error y conserva los demás resultados. Si
no se pueden calcular complejidad o mantenibilidad, conserva las demás métricas
disponibles y registra los errores. Los detalles con métricas ausentes no se
reescriben: un libro existente puede conservar resultados de ejecuciones anteriores.

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

## Procesamiento de Android y ESP32 Wiring

```bash
python main_esp32_android.py
```

El flujo usa `configuracion/configuracion_esp32_android.json` y lee los
proyectos de `Datos_Proyectos` y las mediciones de
`datos_entrada/datos_entrada_esp32_android.xlsx` y agrega o actualiza los
resultados por c?digo en `metricas_resultados_esp32_android.xlsx`, junto a
Android. Reutiliza Resumen, las hojas de m?tricas, Errores, Comparacion y los
gr?ficos del perfil de dispositivos. Los CSV y res?menes TXT se guardan en
la carpeta de resultados configurada. `main_pc.py` conserva su flujo.

| Lenguaje | Fuentes | Complejidad y MI | Incidencias |
| --- | --- | --- | --- |
| Android Java | `.java` | Lizard, lector Java | PMD |
| Android Kotlin | `.kt` | Lizard, lector Kotlin | Detekt |
| ESP32 Wiring | `.ino`, `.cpp`, `.cc`, `.cxx`, `.c`, `.h`, `.hpp` | Lizard, lector C++ | Cppcheck |

Wiring se normaliza como `wiring` y conserva su nombre original en el Excel.
`util/fuentes_wiring.py` selecciona las fuentes propias y prepara copias
temporales compartiendo el mismo criterio para Lizard y Cppcheck. Conserva
la estructura relativa y adapta los `.ino` a nombres terminados en `.cpp`
sin colisiones. Los originales no se modifican y las rutas originales se
restauran en los CSV y diagn?sticos antes de eliminar las copias temporales.

La selecci?n excluye dependencias y carpetas de compilaci?n como `.pio`,
`build`, `libraries`, `vendor` y `generated`; la lista completa est? en
`util/fuentes_wiring.py`. Tambi?n excluye enlaces y archivos marcados con
`@generated` o `automatically generated` en comentarios de los primeros
2048 caracteres. Las dependencias guardadas bajo otros nombres no se pueden
identificar autom?ticamente: la ra?z configurada debe contener c?digo propio.

Se reutilizan los c?lculos existentes de CCN, MI, Issues/KLOC e ISI. En Wiring,
el MI distingue archivos hom?nimos por su ruta completa; los dem?s lenguajes
conservan su agrupaci?n anterior. NLOC y tokens del MI provienen de las
funciones de Lizard. No se calcula volumen Halstead ni se cambia la f?rmula
hist?rica del MI. La densidad usa `metricas_mi.nloc_mi`, por lo que el an?lisis
de incidencias conserva su dependencia del MI.

Cppcheck se ejecuta en C++ con plataforma `unspecified`, sin asumir tamaños
del equipo anfitrión ni inventar macros o tipos Arduino. Además de la raíz y
`include`, aprovecha las rutas concretas de `.vscode/c_cpp_properties.json`,
las dependencias instaladas de los entornos de `platformio.ini`, el framework
Arduino de ESP32 y las bibliotecas del almacén de PlatformIO. Respeta
`default_envs`, `core_dir`, `libdeps_dir` y las variables de entorno
`PLATFORMIO_CORE_DIR` y `PLATFORMIO_LIBDEPS_DIR`. Los metadatos de VS Code
inválidos se advierten y se conservan las rutas instaladas disponibles.
No instala paquetes, compila proyectos ni modifica dependencias.

Las bibliotecas se consultan como cabeceras, pero sus diagnósticos se excluyen
de las métricas propias. Se cuenta una incidencia por diagnóstico y se eliminan
duplicados de cabeceras. El mapeo es: `error` → Alta;
`warning`/`performance` → Media; `style`/`portability` → Baja.
Las reglas y coberturas difieren entre analizadores.

`missingInclude` y `missingIncludeSystem` se registran como advertencias de
cobertura y permiten conservar los resultados. Si Cppcheck informa que no
exploró todas las configuraciones del preprocesador, también se registra esa
limitación. La columna `Observacion` de Bugs_Smells y el resumen TXT indican
`Cobertura limitada` junto a las causas, incluso cuando hay cero incidencias.
Los errores de sintaxis, macros desconocidas, fallos internos, configuraciones
inválidas o fallos del proceso siguen invalidando la medición. Las métricas
fallidas quedan en `None` y sus errores se escriben en Errores. Una carpeta sin
fuentes o sin funciones Wiring no produce CCN o MI ficticios.

Las entradas ESP32 del JSON apuntan a `Embebido/Proyecto_sin_Vibecoding` y
`Embebido/Proyecto_con_Vibecoding`. Ambos c?digos deben tener mediciones de
CPU/memoria en el Excel de entrada si se desea calcular esos indicadores.
La implementaci?n Vibecoding necesita adem?s su r?brica de Concurrencia y
sus Datos Tokens. En Manual se omiten concurrencia y tokens, igual que Android.
La comparaci?n usa `(ID proyecto, dispositivo)` y las dos implementaciones.

Al verificar las entradas actuales, la carpeta ESP32 Vibecoding solo contiene
un `LEEME.txt`; el Excel de entrada tampoco contiene mediciones ESP32.
El proyecto manual permite calcular CCN, MI e incidencias con cobertura limitada
cuando siguen faltando cabeceras como `PubSubClient.h`. Las fuentes de Vibecoding
y las mediciones de entrada deben completarse para obtener todos los resultados.
No se agregan mediciones inventadas.

Verificaci?n espec?fica:

```bash
python -m unittest discover -s tests -p test_wiring.py -v
python -m unittest discover -s tests -p test_metricas_estaticas.py -v
```

Las pruebas Wiring ejecutan Lizard y Cppcheck reales cuando est?n disponibles,
verifican los fallos de herramientas y la escritura del Excel junto a Android,
la actualizaci?n sin duplicados y la comparaci?n Manual/Vibecoding.
