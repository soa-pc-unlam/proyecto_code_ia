# Maze Chase

Videojuego de laberinto inspirado en Pac-Man para estudiar concurrencia con cuatro
trabajadores `threading`. Python 3.11 o posterior; dependencias administradas con UV.
Se usa **pygame-ce**, distribución compatible que proporciona el módulo `pygame`, y
`psutil` para medir memoria RSS y CPU del proceso. No requiere imágenes ni audio externos.

## Instalación y ejecución

```sh
uv sync --locked
uv run maze-chase --mode concurrent
uv run maze-chase --mode sequential
```

Si el entorno restringe la caché del usuario, anteponer `UV_CACHE_DIR=.uv-cache` a los
comandos. `uv.lock` fija las versiones utilizadas. Es necesario un escritorio con SDL
para jugar; los benchmarks sin ventana funcionan también en servidores.

- Flechas: dirección solicitada. El giro se conserva hasta encontrar un pasillo;
  el personaje continúa en su dirección actual mientras el giro esté bloqueado.
- Enter / Espacio: comenzar, pausar y continuar.
- R: nueva partida; Esc o cerrar la ventana: salir.
- F3: mostrar u ocultar FPS, modo, respuestas pendientes y tiempo de poder.
- Puntos: 10; potenciadores: 50; fantasmas vulnerables: 200. Tres vidas.
- Comer todos los puntos y potenciadores gana la partida; perder las tres vidas la termina.

```sh
uv run maze-chase --tick-hz 8 --fps 60 --power-seconds 6 --output results/partida.json
```

Un movimiento equivale a una celda por tick. El intervalo es `1 / tick_hz` segundos;
renderizado y lógica tienen frecuencias independientes. Los temporizadores avanzan
sólo al jugar. El modo concurrente es el predeterminado.

## Pruebas

```sh
uv run pytest -q
uv run ruff check .
```

Las pruebas de interfaz usan SDL `dummy`: crean y dibujan una superficie real y
simulan eventos del teclado, pero no sustituyen una prueba humana en un escritorio.
Se verifican conectividad del nivel, paredes, puntuación, cruces, victoria/derrota,
pausa, estrategias, mensajes inmutables, descarte de generaciones, duplicados,
colas saturadas, errores, cierre y respuesta del coordinador ante IA lenta.

## Experimentos reproducibles

```sh
uv run python scripts/benchmark.py --ticks 240 --repeats 3 --tick-hz 30
uv run python scripts/benchmark.py --ticks 240 --repeats 3 --render
uv run maze-chase --mode concurrent --headless --ticks 240 --tick-hz 30
```

Cada ensayo corre en un proceso nuevo, alternando el orden de los modos entre
repeticiones. La entrada cambia cada 12 ticks según una secuencia fija de ocho
direcciones; si hay victoria/derrota, la siguiente iteración inicia otra partida.
Los ticks totales y contadores se conservan. Ambos modos usan la misma secuencia,
frecuencia y reglas. Los experimentos se ejecutan a tiempo real, no a máxima velocidad.
`--render` incluye el coste de dibujar. Para un servidor puede combinarse con
`SDL_VIDEODRIVER=dummy`; ese FPS no representa el rendimiento de una ventana visible.

La carpeta de salida contiene un JSON por ensayo, `runs.json`, `runs.csv` y
`comparison.json` con promedios por modo y diferencias concurrente menos secuencial.
Los ejemplos reales incluidos están en `results/example/`. El FPS es `null` cuando
no se renderiza. No se deben comparar como equivalentes ensayos con distinta
configuración, versión de Python, hardware o carga externa.

## Interpretación de métricas

- CPU media: tiempo de CPU de todo el proceso / tiempo de pared × 100. Puede superar
  100% si se utilizan varios núcleos. CPU máxima: máximo de intervalos muestreados.
- RSS: memoria residente del proceso en MiB; media aritmética y máximo de muestras
  aproximadamente cada 100 ms, incluyendo una muestra inicial y otra final.
- FPS: frames renderizados / duración total, incluidos inicio y cierre.
- Actualización: duración de `Controller.tick`; incluye cálculo IA en secuencial y
  publicación/recogida en concurrente. No incluye renderizado ni espera entre ticks.
- Decisión: duración de la llamada a la estrategia; no incluye tiempo en cola.
  `ai_seconds_total` suma esas duraciones, que pueden solaparse entre trabajadores;
  no es tiempo de CPU ni debe restarse del tiempo total para inferir otros costes.
- `tasks_sent`, `tasks_processed`, reemplazos/cancelaciones y descartes por cola
  llena tienen significados diferentes; no se supone que enviados = procesados.
- `counts.stale`: respuestas descartadas por generación o invalidación explícita.
  `duplicates`: segunda respuesta de un fantasma en la generación pendiente.
  `missed`: fantasma sin respuesta al vencer un tick; su acción es quedarse quieto.
- `invalid`, `invariant_violations`, `worker_errors`, `thread_failures` y
  `clean_shutdown` permiten evaluar correctitud. Si un hilo no termina, sus
  acumuladores no se leen y `worker_statistics_complete` es falso.

El cierre cancela tareas aún en cola y deja terminar el cálculo en curso. Una
respuesta de la última generación puede ser procesada sin aplicarse al juego;
`counts.applied` cuenta únicamente decisiones aceptadas durante ticks de juego.

## Alcance y limitaciones

Es un nivel educativo con movimiento por celdas y gráficos propios. Las estrategias
están inspiradas en los cuatro fantasmas originales; no reproducen exactamente la
máquina arcade. La IA usa BFS determinista y huida local durante los potenciadores.
No hay túneles, audio ni múltiples niveles, que no son requisitos del plan.

Las decisiones que cumplen el plazo se aplican en orden estable, pero la planificación
del sistema operativo puede causar omisiones en concurrente. Por eso reproducir las
entradas no garantiza partidas idénticas cuando hay respuestas tardías. El benchmark
registra las omisiones; no inventa equivalencia ni mejoras de rendimiento.

Los hilos benefician la separación y coordinación del trabajo; con el GIL no se
espera acelerar BFS en Python. Un hilo Python arbitrario que no termina no puede
cancelarse de forma segura: el cierre tiene límite global de un segundo, declara
fallo y no espera indefinidamente. Las estrategias incluidas son finitas y no
realizan I/O. Véanse [CONCURRENCY.md](CONCURRENCY.md) y [ARCHITECTURE.md](ARCHITECTURE.md).

Referencias de API: [Pygame Clock](https://www.pygame.org/docs/ref/time.html) y
[Pygame display](https://www.pygame.org/docs/ref/display.html). Se utiliza `Clock.tick`
para limitar frames sin espera activa.
