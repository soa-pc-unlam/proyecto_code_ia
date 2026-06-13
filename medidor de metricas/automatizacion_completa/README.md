# Evaluador de Métricas de Calidad de Código IA

Programa en Python para evaluar métricas de calidad sobre proyectos generados por IA.

## Funcionalidades

- Lee uno o más proyectos desde `proyectos.json`.
- Lee los criterios de clasificación desde `configuracion.json`.
- Ejecuta Lizard para calcular complejidad ciclomática.
- Genera archivos `.csv` y `.txt` por cada proyecto analizado.
- Genera o actualiza un archivo Excel con:
  - Solapa `Resumen`
  - Solapa `Complejidad`
  - Solapa `Errores`
- Actualiza filas existentes si el código del proyecto ya existe.
- Genera gráficos comparativos en Excel.
- Guarda logs de ejecución.

## Instalación

```bash
pip install -r requirements.txt
```

También se requiere tener instalado Lizard:

```bash
pip install lizard
```

## Ejecución

```bash
python main.py
```

## Archivos principales

- `main.py`: punto de entrada.
- `proyectos.json`: proyectos a evaluar.
- `configuracion.json`: umbrales de clasificación.
- `lizard_analyzer.py`: ejecución y procesamiento de Lizard.
- `excel_manager.py`: generación y actualización del Excel.
- `config_manager.py`: lectura y validación de JSON.
- `models.py`: estructuras de datos.
- `utils.py`: funciones auxiliares.
