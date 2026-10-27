# Sistema concurrente de reservas de cine

Aplicación de escritorio con Python 3.12+, threads y Tkinter.

## Requisitos

- [uv](https://docs.astral.sh/uv/) instalado en el sistema.

## Configuración (solo la primera vez)

Desde la carpeta del proyecto:

```bash
uv python install 3.12
uv sync
```

`uv` descarga Python 3.12 para este proyecto y crea el entorno virtual en `.venv`.

## Ejecutar

```bash
uv run python main.py
```

Con cantidad de workers (opcional):

```bash
uv run python main.py 8
```

Prueba de concurrencia sin interfaz:

```bash
uv run python verificar_concurrencia.py
```

Métricas CPU/memoria (psutil) para la planilla del TP:

```bash
uv sync
uv run python medir_metricas.py --codigo "M3 - Tu etiqueta"
```

Parámetros opcionales (mismos defaults que la UI: 10 productores, 5 solicitudes, 4 workers):

```bash
uv run python medir_metricas.py --productores 10 --solicitudes 5 --workers 4
```

## Activar el entorno manualmente (opcional)

Windows PowerShell:

```powershell
.\.venv\Scripts\Activate.ps1
python main.py
```
