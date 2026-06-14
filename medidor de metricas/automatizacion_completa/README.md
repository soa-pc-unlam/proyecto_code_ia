# Evaluador de Métricas de Calidad de Código IA

Programa en Python para evaluar métricas de calidad sobre proyectos generados por IA.

## Funcionalidades

- Lee uno o más proyectos desde `proyectos.json`.
- Lee los criterios de clasificación desde `configuracion.json`.
- Ejecuta Lizard para calcular complejidad ciclomática.
- Calcula el índice de mantenibilidad usando la fórmula definida en `calculate_mi.py`.
- Genera archivos `.csv` y `.txt` por cada proyecto analizado.
- Genera o actualiza un archivo Excel con las hojas `Resumen`, `Complejidad`, `Mantenibilidad` y `Errores`.
- Actualiza filas existentes si el código del proyecto ya existe.
- Genera gráficos comparativos en Excel.
- Guarda logs de ejecución.

## Estructura del proyecto

```text
automatizacion_completa_modular/
│
├── main.py
├── calculate_mi.py
├── configuracion.json
├── proyectos.json
├── requirements.txt
│
├── metricas/
│   ├── __init__.py
│   ├── complejidad.py
│   └── mantenibilidad.py
│
├── reportes/
│   ├── __init__.py
│   ├── excel.py
│   └── txt.py
│
├── util/
│   ├── __init__.py
│   ├── archivos.py
│   ├── configuracion.py
│   ├── lenguajes.py
│   ├── logging_config.py
│   └── modelos.py
│
├── resultados/
└── logs/
```

## Instalación

```bash
pip install -r requirements.txt
```

`requirements.txt` ya incluye `lizard` y `openpyxl`.

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
