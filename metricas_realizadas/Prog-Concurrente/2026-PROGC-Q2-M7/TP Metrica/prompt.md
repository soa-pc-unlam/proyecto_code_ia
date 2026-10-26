## 1. Contexto y objetivo

Actúa como un Ingeniero de Software Senior especializado en Python, programación concurrente, arquitectura de software y desarrollo de videojuegos con Pygame.

Tu tarea es diseñar e implementar un videojuego inspirado en **Pac-Man**, utilizando Python y Pygame, con especial énfasis en la aplicación correcta de conceptos de programación concurrente.

El objetivo principal no es únicamente desarrollar un videojuego funcional, sino implementar un sistema concurrente de complejidad no trivial que permita estudiar, probar y medir:

- Ejecución concurrente de múltiples tareas.
- Comunicación entre hilos.
- Sincronización y coordinación.
- Condiciones de carrera.
- Ausencia de deadlocks.
- Consumo de CPU y memoria.

## 2. Requisitos funcionales

Implementa un juego de Pac-Man 2D utilizando Pygame.

### 2.1. Tablero

- Representar un laberinto mediante una matriz bidimensional.
- Implementar paredes, pasillos, puntos y potenciadores.
- Impedir movimientos que atraviesen paredes.
- Utilizar un sistema de coordenadas basado en celdas.
- Separar la representación lógica del tablero de su renderizado.
- Incorporar al menos un nivel jugable.

### 2.2. Pac-Man

- Controlar al personaje mediante las teclas direccionales.
- Implementar movimiento continuo basado en un intervalo configurable.
- Detectar colisiones contra paredes.
- Permitir consumir puntos y potenciadores.
- Mantener puntuación y vidas.
- Implementar condiciones de victoria y derrota.

### 2.3. Fantasmas

Implementar cuatro fantasmas con comportamientos diferentes:

1. Blinky: persigue directamente la posición actual de Pac-Man.
2. Pinky: intenta anticipar la dirección de movimiento del jugador.
3. Inky: utiliza una estrategia de persecución basada en posiciones relativas.
4. Clyde: alterna entre persecución y dispersión según la distancia al jugador.

Todos los fantasmas deben respetar las reglas del tablero y evitar movimientos inválidos.

### 2.4. Mecánicas adicionales

- Estados de inicio, pausa, ejecución, victoria y derrota.
- Reinicio de partida.
- Temporizador de potenciadores.
- Fantasmas vulnerables temporalmente.
- Indicadores visuales de puntuación, vidas y estado.
- Visualización opcional de información técnica: FPS, modo de ejecución y decisiones pendientes.

## 3. Requisito principal: concurrencia

El videojuego debe permitir ejecutar la inteligencia artificial de los fantasmas utilizando **cuatro hilos trabajadores**, uno por fantasma.

Utiliza el módulo estándar `threading` de Python.

### 3.1. Hilo principal

El hilo principal debe ser responsable exclusivamente de:

- Gestionar el bucle de Pygame.
- Capturar eventos del teclado.
- Actualizar el estado autoritativo del juego.
- Validar y aplicar movimientos.
- Resolver colisiones.
- Actualizar puntuación, vidas y temporizadores.
- Renderizar el tablero y los personajes.
- Coordinar los ciclos de actualización.

El hilo principal nunca debe bloquearse esperando indefinidamente a un trabajador.

### 3.2. Hilos de los fantasmas

Cada fantasma debe contar con un trabajador independiente.

Cada trabajador debe:

1. Esperar una solicitud de cálculo.
2. Recibir una instantánea inmutable del estado necesario.
3. Calcular su próximo movimiento según su estrategia.
4. Enviar una propuesta de movimiento mediante una cola.
5. Esperar una nueva solicitud sin realizar ciclos de espera activa.

Los trabajadores no pueden modificar directamente el estado del juego ni llamar a funciones de renderizado de Pygame.

### 3.3. Comunicación entre hilos

Utilizar `queue.Queue` para comunicar tareas y resultados entre el hilo principal y los trabajadores.

Cada solicitud debe incluir:

- Identificador del fantasma.
- Identificador de generación o tick lógico.
- Posición de Pac-Man.
- Dirección de Pac-Man.
- Posiciones relevantes de los fantasmas.
- Estado del tablero.
- Estado actual de los potenciadores.

Cada respuesta debe incluir:

- Identificador del fantasma.
- Identificador del tick.
- Dirección o posición propuesta.
- Duración del cálculo.
- Estado de éxito o error.

Definir los mensajes mediante `dataclasses` inmutables y tipos explícitos.

### 3.4. Sincronización y consistencia

Implementar mecanismos que garanticen la correcta coordinación entre tareas.

Requisitos:

- Utilizar colas seguras para hilos.
- Utilizar `threading.Event` para solicitar la finalización de trabajadores.
- Utilizar `threading.Lock` solamente cuando exista un recurso mutable realmente compartido que requiera exclusión.
- Evitar el uso de variables globales mutables.
- No mantener bloqueos durante operaciones potencialmente lentas.
- Evitar deadlocks mediante un diseño sin dependencias circulares entre locks.
- Evitar condiciones de carrera utilizando transferencia de mensajes e instantáneas inmutables.
- Evitar el acceso simultáneo de múltiples hilos a las estructuras internas de Pygame.
- Garantizar que cada resultado sea aplicado como máximo una vez.
- Descartar resultados obsoletos provenientes de ticks anteriores.
- Implementar límites de capacidad en las colas y una política explícita de descarte o reemplazo de tareas obsoletas.
- Gestionar correctamente las excepciones de los trabajadores.

### 3.5. Modelo de actualización

Implementar un sistema de ticks lógicos a frecuencia fija.

En cada tick:

1. El hilo principal captura una instantánea consistente del juego.
2. Publica las tareas de cálculo de los fantasmas.
3. Recoge los resultados disponibles sin bloquear el renderizado.
4. Valida que los resultados pertenezcan a la generación correspondiente.
5. Aplica los movimientos válidos mediante una política determinista.
6. Resuelve colisiones y actualiza el estado del juego.

Definir explícitamente qué ocurre si un trabajador no entrega su respuesta a tiempo: conservar un movimiento válido anterior, utilizar una acción neutral o aplicar otra política documentada.

Evitar que el resultado dependa de un orden accidental de llegada a las colas.

### 3.6. Finalización segura

Al cerrar el juego:

- Señalizar la finalización de los hilos.
- Despertar a los trabajadores que estén esperando tareas.
- Liberar los recursos utilizados.
- Ejecutar `join()` con tiempos máximos razonables.
- Evitar hilos huérfanos.
- Cerrar Pygame ordenadamente.

## 4. Modos de ejecución

Implementar dos modos seleccionables mediante argumentos de línea de comandos.

**Modo secuencial (`--mode sequential`)**

- Calcular los movimientos de los cuatro fantasmas en el hilo principal.
- Utilizar las mismas estrategias de inteligencia artificial.
- No crear hilos de fantasmas.
- Servir como referencia para comparar rendimiento.

**Modo concurrente (`--mode concurrent`)**

- Utilizar los cuatro trabajadores independientes.
- Comunicar tareas y resultados mediante colas.
- Aplicar los mecanismos de sincronización definidos anteriormente.

Ambos modos deben compartir la misma lógica de negocio y permitir comparar resultados en condiciones equivalentes.

## 5. Arquitectura de software

Utilizar una estructura modular inspirada en Clean Architecture, adaptada a la escala del proyecto.

Separar claramente:

- Dominio: tablero, entidades, reglas y colisiones.
- Aplicación: coordinación de ticks, comandos y casos de uso.
- Concurrencia: trabajadores, colas, sincronización y ciclo de vida.
- Infraestructura: Pygame, mediciones, configuración y almacenamiento de resultados.

Aplicar principios SOLID cuando aporten claridad, evitando abstracciones innecesarias.

Utilizar:

- Type hints.
- `dataclasses`.
- Enumeraciones para estados y direcciones.
- Interfaces mediante `Protocol` cuando corresponda.
- Logging estructurado o consistente.
- Docstrings para componentes relevantes.
- Configuración centralizada.

Evitar dependencias externas innecesarias.

## 6. Métricas y observabilidad

Incorporar mecanismos para recopilar y exportar métricas del programa.

### 6.1. CPU

Medir:

- Porcentaje de utilización de CPU del proceso.
- Promedio y máximo durante cada ejecución.
- Consumo de CPU por modo de ejecución.

### 6.2. Memoria

Medir:

- Consumo promedio y máximo.
- Diferencias de memoria entre modos.

### 6.3. Rendimiento

Medir:

- FPS promedio.
- Tiempo de actualización lógica por tick.
- Latencia de cálculo de decisiones.
- Cantidad de tareas enviadas.
- Cantidad de tareas procesadas.
- Cantidad de resultados obsoletos descartados.
- Cantidad de errores de trabajadores.

Diferenciar el tiempo de ejecución total del tiempo dedicado específicamente a calcular la inteligencia artificial.

### 6.4. Correctitud de concurrencia

Registrar:

- Cantidad de movimientos inválidos detectados.
- Violaciones de invariantes.
- Resultados duplicados.
- Resultados obsoletos.
- Fallos o terminaciones inesperadas de hilos.
- Finalización correcta o incorrecta de cada ejecución.

### 6.5. Reproducibilidad

Implementar una modalidad de benchmark automatizado que:

- Ejecute una cantidad configurable de ticks.
- Permita repetir experimentos varias veces.
- Utilice una secuencia de entradas predefinida.
- Exporte resultados comparables entre ambos modos.

Registrar las características del entorno: versión de Python, sistema operativo, procesador y versión de Pygame.

No inventar resultados de benchmark: reportar únicamente datos obtenidos mediante ejecuciones reales.

## 8. Entregables del proyecto

Generar una estructura de proyecto organizada que contenga:

- Código fuente completo.
- `pyproject.toml` con dependencias y configuración.
- `README.md` con instalación, ejecución y pruebas.
- Configuración de modos secuencial y concurrente.
- Scripts para ejecutar benchmarks.
- Ejemplos de exportación de métricas.
- Documento `CONCURRENCY.md` describiendo los mecanismos de concurrencia.
- Documento `ARCHITECTURE.md` describiendo los componentes y decisiones de diseño.

El archivo `CONCURRENCY.md` debe explicar:

- Qué tareas son concurrentes y por qué.
- Qué datos comparten los trabajadores.
- Cómo se comunican.
- Qué mecanismos de sincronización se emplean.
- Qué condiciones de carrera se previenen.
- Cómo se evitan los deadlocks.
- Qué limitaciones impone el GIL de CPython.
- Por qué se seleccionó `threading` frente a `multiprocessing` o `asyncio`.
- Qué diferencias existen entre los modos de ejecución.

## 9. Criterios de aceptación

La solución se considerará terminada cuando:

- El juego sea ejecutable y jugable.
- Existan cuatro fantasmas con comportamientos diferenciados.
- Los cuatro trabajadores concurrentes estén implementados y coordinados correctamente.
- El juego permanezca responsivo ante trabajadores lentos.
- No se produzcan modificaciones concurrentes no sincronizadas del estado del juego.
- La finalización sea ordenada.
- Los modos secuencial y concurrente funcionen.
- Las pruebas automatizadas pasen.
- Las métricas puedan exportarse.
- El código esté documentado y sea comprensible.
- Se puedan repetir experimentos comparativos.

No afirmar que el programa está libre de condiciones de carrera o deadlocks únicamente porque las pruebas hayan pasado. Explicar las garantías de diseño y las limitaciones de la validación realizada.

## 10. Forma de trabajo

Antes de implementar:

1. Presenta la arquitectura propuesta.
2. Identifica las tareas concurrentes.
3. Define los recursos compartidos.
4. Explica los mecanismos de sincronización.
5. Identifica potenciales condiciones de carrera y deadlocks.
6. Describe cómo se medirá el rendimiento.

Después:

1. Implementa el proyecto completo, sin dejar funciones vacías ni pseudocódigo.
2. Ejecuta las pruebas disponibles.
3. Corrige los errores detectados.
4. Verifica los criterios de aceptación.
5. Documenta las decisiones técnicas.
6. Presenta las limitaciones conocidas.

Si tu entorno no permite ejecutar alguna prueba, indícalo claramente y no afirmes haberla superado.
