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
2. Definir los proyectos en `datos_entrada/proyectos.json`.
3. Completar la hoja `Concurrencia` de `datos_entrada/datos_entrada.xlsx`.

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
