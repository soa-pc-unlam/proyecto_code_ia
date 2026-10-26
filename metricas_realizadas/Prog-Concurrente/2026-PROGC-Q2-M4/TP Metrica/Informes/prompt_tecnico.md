# Prompt técnico — Simulador de Centro Logístico Concurrente

Grupo M4 · Programación Concurrente (UNLaM) · Actividad Práctica Obligatoria N.° 1

## Paradigma

Se combinaron tres técnicas: **asignación de rol** (el modelo actúa como arquitecto
de software especializado en concurrencia), **meta-prompting** (el prompt describe la
estructura de la solución antes que su contenido) y **encadenamiento de razonamiento**
(un plan explícito paso a paso). Las restricciones se derivaron de los criterios de las
rúbricas de concurrencia y funcionalidad de la cátedra.

## 1. Prompt de inicialización (fase 0)

```text
Actúa como un Arquitecto de Software Senior especializado en programación concurrente
en Python. Tu objetivo es inicializar la estructura base y el código fundacional para
un "Simulador de Centro Logístico e Inventario".

CONTEXTO DEL SISTEMA:
El programa debe implementar patrones clásicos de concurrencia en un entorno unificado:
- Productores (Camiones que insertan lotes de paquetes en una cola).
- Consumidores/Escritores (Operarios que extraen paquetes de la cola y actualizan un
  inventario central).
- Lectores (Monitor/Dashboard UI que lee el estado del inventario y la cola en tiempo real).

RESTRICCIONES ARQUITECTÓNICAS ESTRICTAS:
1. Sincronización: Prohibido el "busy waiting". Debes utilizar `queue.Queue` de la
   librería estándar para la transferencia segura entre productores y consumidores.
2. Exclusión Mutua: Todo acceso al estado compartido (el inventario general y
   estadísticas) debe estar protegido mediante contextos `with threading.Lock():` o
   `threading.RLock():`.
3. Tolerancia a Fallos y Deadlocks: Configura timeouts en operaciones bloqueantes y
   asegura un "Graceful Shutdown" (cierre limpio) utilizando `threading.Event` para
   señalar a los hilos cuándo detenerse.
4. Calidad de Código: Aplica Type Hints estrictos, Docstrings (formato Google) y un
   manejo global de excepciones dentro del método `run()` de cada hilo para evitar
   fallos silenciosos.

PLAN DE EJECUCIÓN (Piensa paso a paso):

PASO 1: Genera la estructura de directorios del proyecto en formato de árbol,
separando claramente la lógica de dominio (core), hilos (workers), interfaz (ui) y
pruebas (tests).
PASO 2: Escribe el código de `core/inventory.py`. Implementa la clase `Inventory` que
encapsule los datos compartidos y los Locks necesarios para operaciones seguras de
lectura y escritura.
PASO 3: Escribe el código de `workers/base.py`. Crea una clase base abstracta que
herede de `threading.Thread`, inyecte un `threading.Event` para controlar su ciclo de
vida y contenga un bloque try/except general.
PASO 4: Escribe el esqueleto de `main.py`. Instancia los recursos compartidos,
inicializa 2 camiones y 3 operarios, arráncalos y configura la captura de
`KeyboardInterrupt` para activar el evento de apagado y hacer un `.join()` ordenado de
todos los hilos.

Importante: No implementes la UI de Rich todavía. Concéntrate exclusivamente en sentar
bases de concurrencia robustas, analizables y libres de condiciones de carrera.
```

Refinamiento posterior de la fase 0: *"agregá el segundo evento para drenar la cola al
cerrar. Hagamos atomic commits según las features."*

## 2. Prompts incrementales por fase

Cada fase se desarrolló en una rama propia a partir de `DesarrolloAPO`, con commits
atómicos.

### Fase 1 — Panel en tiempo real (lectores) · `feature/ui-dashboard`

```text
Checkout to a new branch 'feature/ui-dashboard' from 'DesarrolloAPO'. Create
ui/dashboard.py. Implement a Dashboard class inheriting from workers.base.BaseWorker.
In its _step() method, it should clear the terminal and cleanly print the
inventory.snapshot() metrics and dock.qsize() every 0.5 seconds without holding any
locks longer than necessary. Then, integrate this new worker into main.py so it starts
and stops alongside trucks and operators. Commit the changes atomically.
```

### Fase 2 — Pausas aleatorias (chaos engineering) · `feature/chaos-monkey-interrupts`

```text
Checkout to a new branch 'feature/chaos-monkey-interrupts' from 'DesarrolloAPO'.
Implement a global pause mechanism using a new maintenance_event (a threading.Event).
Create a Supervisor worker in workers/supervisor.py that randomly sets and clears this
event. Update the _step() methods in Truck and Operator to wait for this event to be
clear before processing, using proper timeouts to avoid deadlocks. Finally, create a
stress test in tests/test_chaos.py verifying that random pauses do not cause package
loss. Commit the features atomically.
```

### Fase 3 — Persistencia segura · `feature/async-persistence`

```text
Checkout a new branch 'feature/async-persistence' from 'DesarrolloAPO'.

Create workers/exporter.py with an ExportWorker(BaseWorker). Every
config.EXPORT_INTERVAL_SECONDS (new constant, default 5.0) it takes
inventory.snapshot() and writes it to a JSON file (path injected via the constructor,
default config.EXPORT_PATH = "logistics_report.json").

Requirements:
- No lock is held during serialization or disk I/O: only snapshot() locks.
- Atomic write: write to a temp file in the same directory
  (tempfile.NamedTemporaryFile(delete=False, dir=...)), flush + os.fsync, then
  os.replace() onto the target. Clean up the temp file on failure.
- JSON content: timestamp (ISO 8601), packages_received, units_received, stock per
  SKU, and dock size.
- Waits use self._stop_event.wait(interval) so shutdown interrupts them.
- In main.py: add the exporter to `first_phase`, and after shutdown() (once the dock
  is drained) perform one final export so the file matches the final report.
- Follow the existing style: Google docstrings, type hints, 2-space indent, constants
  in config.py.

Tests in tests/test_export.py (unittest, use tempfile.TemporaryDirectory):
- the file is valid JSON with the expected keys and values;
- a reader thread repeatedly loading the file while the exporter writes never sees a
  partial/invalid JSON;
- stopping the worker mid-interval returns promptly (< 1s).

Run the full test suite before each commit. Atomic commits: feat(workers),
test(export), feat(app). Never stage or commit the .venv directory.
```

Refinamiento: corrección del exportador en Windows (`PermissionError` en `os.replace()`).

### Fase 4 — Configuración por línea de comandos · `feature/cli-arguments`

```text
Checkout a new branch 'feature/cli-arguments' from 'DesarrolloAPO'.

Add argparse to main.py via a parse_args(argv: list[str] | None) function returning a
frozen dataclass SimulationSettings. Defaults come from config.py; do NOT mutate config
at runtime (constants are Final and some are bound as default parameter values at
import time).

Arguments: --trucks, --operators, --queue-size, --processing-seconds, --export-path,
--export-interval, --no-chaos (disable the Supervisor), --log-level (default WARNING so
logs don't break the dashboard), --log-file (optional; send logs there instead of
stderr).

Validate with argparse types: counts >= 1, durations > 0 (parser.error on invalid
values). Pass the settings explicitly into build_trucks/build_operators/Queue/Operator/
ExportWorker. Make -h descriptive, with an example in the epilog.

Tests in tests/test_cli.py: defaults equal config values, overrides are applied,
invalid values exit with SystemExit, --no-chaos skips the supervisor. The existing
suite must keep passing.

Atomic commits: feat(config), refactor(app), test(cli). Never stage or commit the .venv
directory.
```

Refinamientos: correcciones señaladas por el revisor automático (cierre ante una
segunda interrupción, prueba del cierre real, docstring de `Truck`) e impresión del
resumen final independiente del nivel de log.
