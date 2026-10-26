# Arquitectura

## Módulos y dependencias

```text
domain.py ← ai.py ← execution.py
    ↑         ↑          ↑
    └──── application.py ┘
               ↑
      ui.py / metrics.py
               ↑
             cli.py
```

- **Dominio (`domain.py`)**: matriz `Board`, posiciones por celda, enums `Direction`,
  `Ghost`, `State`, estado `Game`, consumo, temporizadores, colisiones e invariantes.
  La representación lógica no depende del dibujo.
- **IA (`ai.py`)**: snapshots, solicitudes, resultados y función de estrategia.
  BFS elige la celda alcanzable más próxima al objetivo y conserva el primer paso.
  Esto permite objetivos anticipados dentro de paredes o fuera de la matriz sin
  producir movimientos inválidos. El orden de exploración rompe empates de forma fija.
- **Aplicación (`application.py`)**: configuración centralizada validada, comandos de
  pausa/reinicio, generaciones y coordinación. Depende del `Protocol Executor`, no
  de un trabajador concreto. Revalida movimientos antes de llamar al dominio.
- **Concurrencia (`execution.py`)**: implementaciones `Sequential` y `Concurrent`,
  objetos `Worker`, colas acotadas, Event, cálculo protegido y estadísticas privadas.
- **Infraestructura (`ui.py`)**: eventos y dibujo Pygame con figuras y tipografía
  local; no requiere assets. La interfaz muestra estado, puntuación, vidas y poder.
- **Infraestructura (`metrics.py`)**: CPU, RSS, latencias, contadores, entorno y
  exportación JSON/CSV. No se crea un hilo adicional de muestreo.
- **Composición (`cli.py`)**: argumentos, construcción de dependencias, bucle con
  reloj fijo, entradas automatizadas y limpieza con `finally`.
- **Experimentos (`scripts/benchmark.py`)**: orquesta procesos independientes con
  el mismo intérprete que UV seleccionó, alterna modos y calcula diferencias reales.

No se introducen repositorios, buses o contenedores de dependencias innecesarios.
El Protocol delimita el único punto donde intercambiar implementaciones aporta una
comparación directa. Los mensajes frozen explicitan el contrato entre capas.

## Estados y reglas

Inicio → jugando con Enter/Espacio; jugando ↔ pausa con la misma tecla. Reiniciar
crea una partida en ejecución desde cualquier estado. Victoria y derrota permanecen
visibles hasta R. Esc/cierre termina desde cualquier estado. Los temporizadores se
expresan en tiempo de simulación, por lo que no se consumen durante la pausa.

Se permite que fantasmas compartan celdas. Los destinos de cada uno se calculan
sobre la misma instantánea; no se bloquean unos a otros. Las colisiones del jugador
se detectan tras aplicar todos los destinos y también al intercambiar las celdas
anteriores. Comer un potenciador en ese tick vuelve vulnerables a los fantasmas antes
de resolver la colisión. Si coinciden varios y no hay poder, se descuenta una vida.
Una derrota tiene prioridad sobre ganar con el último punto en ese mismo tick.

## Validación

Las pruebas del dominio verifican nivel conectado, paredes, giros, consumo,
temporizadores, colisiones y finales. Las de concurrencia ejercitan trabajadores
reales con plazos de prueba acotados e inyección de lentitud y excepciones. Las de
integración dibujan con SDL dummy, inyectan eventos y revisan métricas exportadas y
cierre de Pygame. Los benchmarks incluidos provienen de ejecuciones del CLI y
registran el entorno exacto. Las limitaciones del GIL, de los plazos y de la
cancelación están descritas en CONCURRENCY.md.
