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
