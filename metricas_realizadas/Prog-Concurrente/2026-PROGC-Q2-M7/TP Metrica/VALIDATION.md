# Verificación del plan

Validación realizada con UV, Python 3.14.5 y pygame-ce 2.5.8 en macOS.

| Requisito de prompt.md | Evidencia implementada y comprobada |
| --- | --- |
| Matriz, paredes, puntos, potenciadores y nivel jugable | `domain.Board`, `Game`; prueba de conectividad de todos los consumibles y posiciones iniciales |
| Movimiento por flechas continuo y configurable | `View.events`, `Config.tick_hz`, prueba de giro bloqueado y eventos de teclado |
| Puntuación, vidas, victoria y derrota | Pruebas de consumo, colisiones simultáneas, cruces de celdas y estados finales |
| Cuatro comportamientos | Objetivos de Blinky, Pinky, Inky y Clyde comprobados; movimientos válidos sobre todas las posiciones transitables del nivel |
| Inicio, pausa, reinicio y poder temporal | Pruebas de estados, congelación y expiración; generaciones monótonas al reiniciar |
| Cuatro trabajadores reales | Test de identidad y vida de cuatro hilos y ausencia de hilos restantes al cerrar |
| Mensajes frozen, Queue acotadas y Event | `ai.py`, `execution.py`; prueba de inmutabilidad, sustitución y saturación |
| Hilo principal responsivo | Cien ticks durante un cálculo bloqueado con Event, con límite de 0,5 segundos para el conjunto |
| Resultados antiguos, duplicados e inválidos | Inyección de resultados en pruebas del coordinador, incluyendo reinicio |
| Orden determinista y modos equivalentes con respuestas a tiempo | Inversión del orden de respuestas y comparación de 60 ticks completos entre modos |
| Excepciones y muerte inesperada | Estrategia que falla conserva el trabajador; terminación inyectada se cuenta una sola vez |
| Cierre acotado | Cola de salida llena y estrategia no cooperativa; timeout global comprobado, liberando después el hilo de prueba |
| Arquitectura, tipado, Protocol y documentación | Módulos separados; README, ARCHITECTURE y CONCURRENCY; lint configurado en pyproject |
| Métricas CPU/RSS/FPS/IA/correctitud | JSON real por ejecución; pruebas de lectura de exportación y valores de memoria/renderizado |
| Experimentos repetibles | Seis ensayos: 120 ticks × 3 repeticiones × 2 modos, procesos nuevos, configuración e inputs iguales |
| Interfaz Pygame | Pruebas SDL dummy de ambos modos, captura `results/preview.png` inspeccionada y ejecución de ventana real |
| Entregables y dependencias | Código fuente, pyproject, uv.lock, script benchmark, JSON/CSV reales y documentación |

Comandos de validación:

```sh
uv run pytest -q
uv run ruff check .
uv run python scripts/benchmark.py --ticks 120 --repeats 3 --tick-hz 30 --output-dir results/example
uv run maze-chase --mode concurrent --ticks 24 --tick-hz 8 --output results/window-smoke.json
```

Resultado de pruebas: **24 passed**. Revisión estática: **All checks passed**.
La prueba gráfica dentro del sandbox falló inicialmente por ausencia de pantallas;
al ejecutarla con acceso autorizado al escritorio, abrió y cerró correctamente.
La captura se generó con SDL dummy. No se realizó una partida humana completa.

Las pruebas no constituyen una demostración universal de ausencia de carreras ni
deadlocks. Las garantías se fundamentan en propiedad exclusiva del estado, mensajes
inmutables, colas acotadas sin publicación bloqueante y ausencia de ciclos de locks;
los límites de esas garantías se explican en CONCURRENCY.md.

Los ejemplos de benchmark son mediciones breves del entorno disponible, no evidencia
estadística de una mejora de rendimiento. Se regeneraron tras terminar las pruebas,
sin ejecutar la suite ni otras verificaciones en paralelo con los ensayos.

## Resultados observados en los ejemplos finales

Los seis ensayos completaron 120 ticks y finalizaron correctamente, con cero errores
de trabajadores, muertes inesperadas, movimientos inválidos, duplicados o violaciones
de invariantes. La repetición concurrente 2 registró **7 decisiones ausentes al
vencimiento**; las restantes registraron cero. La política neutral mantuvo la
coherencia del juego. Los datos completos están en `results/example/runs.json`.

En estos ensayos, la CPU media promediada entre repeticiones fue 15,41% en secuencial
y 19,29% en concurrente. La actualización del hilo principal promedió 4,32 ms y
0,26 ms respectivamente; esta diferencia refleja que el cálculo se traslada a los
trabajadores, no una aceleración de la IA. La RSS media fue 25,71 y 25,90 MiB.
Son muestras breves sujetas a carga del sistema y planificación, no conclusiones
estadísticas. `comparison.json` contiene los valores sin redondear.

La ejecución gráfica final completó 24 ticks con el controlador nativo `cocoa`,
50,97 FPS medios y cierre correcto (`results/window-smoke.json`).
