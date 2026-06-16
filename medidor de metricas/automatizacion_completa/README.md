# Evaluador de Métricas de Calidad de Código IA

Programa en Python para evaluar métricas de calidad sobre proyectos generados por IA.

## Funcionalidades

- Lee uno o más proyectos desde `proyectos.json`.
- Lee los criterios de clasificación desde `configuracion.json`.
- Ejecuta Lizard para calcular complejidad ciclomática.
- Calcula el índice de mantenibilidad usando la fórmula definida en `calculate_mi.py`.
- Ejecuta análisis de bugs y smells según el lenguaje:
  - PMD para Java.
  - Pylint para Python.
  - Detekt para Kotlin.
- Genera archivos `.csv` y `.txt` por cada proyecto analizado.
- Genera o actualiza un archivo Excel con las hojas `Resumen`, `Complejidad`, `Mantenibilidad`, `Bugs_Smells` y `Errores`.
- Actualiza filas existentes si el código del proyecto ya existe.
- Genera gráficos comparativos en Excel.
- Guarda logs de ejecución.

## Estructura del proyecto

```text
automatizacion_completa_modular/
|
|-- main.py
|-- calculate_mi.py
|-- configuracion.json
|-- proyectos.json
|-- requirements.txt
|
|-- metricas/
|   |-- __init__.py
|   |-- bugs_smells.py
|   |-- complejidad.py
|   `-- mantenibilidad.py
|
|-- reportes/
|   |-- __init__.py
|   |-- excel.py
|   `-- txt.py
|
|-- util/
|   |-- __init__.py
|   |-- archivos.py
|   |-- configuracion.py
|   |-- lenguajes.py
|   |-- logging_config.py
|   `-- modelos.py
|
|-- resultados/
`-- logs/
```

## Instalación

```bash
pip install -r requirements.txt
```

`requirements.txt` incluye `lizard` y `openpyxl`.

Para el análisis de bugs y smells también deben estar disponibles en `PATH` las herramientas externas que correspondan:

- `pmd`, `pmd.bat` o `pmd.cmd` para Java.
- `pylint` para Python.
- `detekt-cli`, `detekt-cli.bat` o `detekt` para Kotlin.

## Ejecución

Desde la carpeta raíz del proyecto:

```bash
python main.py
```

## Archivos principales

- `main.py`: punto de entrada del programa.
- `proyectos.json`: proyectos a evaluar.
- `configuracion.json`: umbrales de clasificación y rutas de salida.
- `metricas/complejidad.py`: ejecución y procesamiento de Lizard.
- `metricas/mantenibilidad.py`: cálculo de MI, resumen TXT y objeto reutilizable.
- `metricas/bugs_smells.py`: ejecución de PMD, Pylint o Detekt, resumen TXT y objeto reutilizable.
- `reportes/excel.py`: generación y actualización del Excel.
- `util/configuracion.py`: lectura, validación de JSON y clasificación por umbrales.
- `util/modelos.py`: estructuras de datos del programa.

## Mantenibilidad

El cálculo de MI reutiliza el CSV generado por Lizard y aplica la fórmula:

```text
MI = (171 - 5.2*ln(tokens) - 0.23*avgCCN - 16.2*ln(nloc)) * 100 / 171
```

El resultado se guarda en:

- `resultados/<codigo>_resumen_mi.txt`
- Solapa `Resumen`: columnas `MI` y `Nivel de MI`
- Solapa `Mantenibilidad`: `Código`, `NLOC MI`, `Cantidad de funciones MI`, `Tokens código`, `MI`, `Nivel de MI`, `Interpretación MI`

Los niveles se toman desde `configuracion.json`, en la clave `umbrales_mi`.

## Bugs y smells

El análisis de bugs y smells genera:

- `resultados/<codigo>_resumen_bugs_smells.txt`
- Solapa `Resumen`: columnas `Issues/KLOC` y `Nivel de Issues`
- Solapa `Bugs_Smells`: `Código`, `Analizador`, `Total de issues`, `Issues/KLOC`, `Nivel de Issues`, `Interpretación de Issues`, `Cantidad baja`, `Cantidad media`, `Cantidad alta`, `Top de reglas violadas`

Los niveles se toman desde `configuracion.json`, en la clave `umbrales_issues`.


## Índice de Severidad de Issues (ISI)

El programa calcula el ISI con la fórmula:

```text
ISI = (Alta * 5 + Media * 2 + Baja * 1) / Total de issues
```

La severidad crítica no se usa como categoría separada: los issues críticos se consideran de severidad alta.
