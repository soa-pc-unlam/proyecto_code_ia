# Concurrencia y garantías de diseño

## Propiedad del estado

El hilo principal es el único propietario de `Game`, sus conjuntos de consumibles,
la configuración de la partida y los contadores del coordinador. También es el único
que llama a Pygame. Cuatro objetos `Worker`, uno por `Ghost`, calculan decisiones:
Blinky persigue al jugador; Pinky anticipa cuatro celdas; Inky refleja el punto dos
celdas adelante respecto a Blinky; Clyde persigue a distancia Manhattan mayor que
seis y se dispersa hacia la esquina inferior izquierda en caso contrario.

Los trabajadores comparten sólo referencias de lectura a `Board`, `Snapshot` y
`Request`, dataclasses congeladas compuestas por tuplas, cadenas, números y enums.
No reciben `Game`, diccionarios mutables ni objetos Pygame. Cada respuesta `Result`
es congelada y lleva fantasma, generación, dirección, duración, éxito y error.
Los acumuladores de cada trabajador son privados: el principal los lee únicamente
una vez terminado el hilo. La observabilidad en vivo usa resultados transferidos,
no lecturas de esos acumuladores.

## Comunicación, capacidad y sincronización

Cada trabajador tiene una `queue.Queue(maxsize=1)` de entrada y otra de resultados
con capacidad dos. El único productor de tareas es el principal y el único productor
de resultados es su trabajador. Si la entrada está llena, el principal elimina la
tarea pendiente y coloca la nueva; nunca interrumpe un cálculo ya iniciado. Si la
salida está llena, el trabajador descarta la respuesta nueva y cuenta el descarte.
El principal drena como máximo dos resultados por trabajador y llamada, para acotar
su trabajo. Ninguna publicación espera a tener espacio.

`Queue` encapsula exclusión y notificación. Un `threading.Event` comunica la orden
de terminar. `get()` bloquea al trabajador ocioso: no hay sondeo activo. No se añade
`Lock` propio porque no existe otro recurso mutable de acceso concurrente que lo
requiera. No se mantiene un bloqueo de cola durante el cálculo, logging ni joins.
Las constantes de módulo son inmutables; no hay estado global mutable de partida.

## Ciclo lógico y política temporal

La frecuencia se configura con `--tick-hz`; el renderizado mantiene otro reloj.
Se utiliza un pipeline de una generación:

1. Entre frames se recogen respuestas sin bloquear.
2. Al inicio de un tick se recogen las disponibles y se cierra la generación pendiente.
3. Sólo se aceptan respuestas de esa generación, como máximo una por fantasma.
4. Se validan destinos y se aplican en orden Blinky, Pinky, Inky, Clyde. Una respuesta
   ausente, errónea o inválida equivale a `STOP`; no hay esperas adicionales.
5. Se mueve el jugador, se consumen objetos, se aplican los movimientos de fantasmas
   y se resuelven coincidencias y cruces. Varias colisiones cuestan una sola vida.
6. Se captura una nueva instantánea consistente y se publican las cuatro tareas.

La primera actualización no tiene propuestas previas. El secuencial calcula al
publicar y retiene los resultados hasta la próxima actualización, manteniendo la
misma latencia lógica. Una respuesta antigua llegada tras el cierre no se reaplica.
La generación es monótona incluso al reiniciar, pausar o continuar; esas acciones
invalidan las propuestas pendientes. Al perder una vida se publican posiciones
reiniciadas. La pausa congela lógica y temporizadores, pero continúa renderizando.
Tras una suspensión larga se limita la recuperación a un tick de atraso.

El orden de llegada no decide el orden de aplicación. Esto no implica determinismo
frente a plazos vencidos: los conjuntos de respuestas disponibles pueden diferir por
planificación del SO. El benchmark usa entradas iguales y registra omisiones para
hacer visible esta limitación. En pruebas con todas las respuestas disponibles sí
se puede comparar la trayectoria completa entre modos.

## Carreras y deadlocks

La transferencia de mensajes evita que los trabajadores observen una mezcla de
posiciones de dos ticks o modifiquen posiciones mientras se dibujan. Generaciones y
registro por fantasma previenen doble aplicación y respuestas posteriores a reinicios.
La capacidad acotada evita acumular trabajo obsoleto indefinidamente.

No hay locks anidados propios ni ciclos de espera: los trabajadores no esperan al
principal para publicar resultados y el principal no espera a un trabajador durante
el juego. El único bloqueo indefinido normal es `get()` del trabajador ocioso, que
el cierre despierta mediante un centinela. Esta argumentación describe las garantías
del protocolo; aprobar tests no demuestra ausencia universal de carreras o deadlocks
ante cambios futuros, fallos del intérprete o estrategias externas arbitrarias.

## Excepciones y finalización

`calculate` transforma excepciones ordinarias en respuestas de error; el trabajador
continúa sirviendo tareas. Se registra un mensaje con fantasma, generación y error.
La muerte inesperada de un hilo se detecta desde el principal y se cuenta una sola
vez. No se reemplaza silenciosamente un hilo muerto.

El cierre activa el Event, cancela las tareas pendientes, inserta un centinela en
cada cola y ejecuta joins con un presupuesto **global de un segundo**. Se termina el
cálculo en curso antes de salir. Pygame se cierra en `finally`, aun ante errores.
Para los cálculos incluidos, BFS recorre como máximo las celdas del tablero, y las
pruebas comprueban que no queden trabajadores vivos. Los hilos son daemon como último
recurso ante una estrategia ajena que no termine: no garantiza su cancelación. En
ese caso el cierre se informa como incorrecto, el CLI devuelve error y no se leen
sus estadísticas mutables. No se promete eliminar por la fuerza un hilo de Python.

## GIL y elección de tecnología

En CPython con GIL, código Python intensivo en CPU no ejecuta bytecode en paralelo
entre núcleos. El intercambio de contexto y las colas pueden hacer concurrente más
lento que secuencial. La concurrencia aquí permite estudiar comunicación, aislamiento,
plazos y coordinación, no demostrar aceleración. La versión de Python se registra
para interpretar experimentos en otros intérpretes o builds sin GIL.

`threading` satisface el requisito de cuatro trabajadores y permite compartir
instantáneas inmutables sin serialización. `multiprocessing` podría paralelizar BFS
pero añadiría procesos, copias y un protocolo de cierre distinto. `asyncio` resulta
apropiado para I/O cooperativo, pero un BFS sin puntos de suspensión bloquearía su
bucle. El modo secuencial no crea hilos de fantasmas y usa exactamente las mismas
estrategias, mensajes y reglas de aplicación.
