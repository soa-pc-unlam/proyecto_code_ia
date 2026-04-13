# Server (Kotlin + Jetty)

Servidor WebSocket que gestiona partidas de ajedrez en tiempo real, incluyendo creación de partidas,
unión de jugadores, validación de movimientos mediante motor de reglas,
difusión de estado a todos los participantes y limpieza automática de partidas inactivas.
La arquitectura está diseñada para manejar múltiples conexiones concurrentes de forma eficiente, garantizando la integridad de las reglas del ajedrez y evitando condiciones de carrera mediante el uso de estructuras thread-safe.

## Tech Stack
| Componente                            | Uso                                         |
|---------------------------------------|---------------------------------------------|
| Kotlin JVM (2.2.0)                    | Lenguaje utilizado                          |
| Jetty 11 (server, servlet, websocket) | Gestión de HTTP/WebSocket y threads de I/O  |
| Jackson                               | Serialización/deserialización JSON          |
| chesslib                              | Motor de reglas y validación de movimientos |
| Logback                               | Logging                                     |
| GraalVM Native (opcional)             | Compilación nativa para distribución        |

## Concurrencia y Manejo de Threads

El servidor utiliza el modelo de threading de Jetty para procesar conexiones WebSocket de manera eficiente:

**Gestión de Conexiones:**
- Cuando se establece una conexión WebSocket, se crea una nueva instancia de `PlayerConnection` en un thread gestionado por Jetty
- Cada mensaje subsecuente en esa conexión es procesado en otro thread del pool de Jetty, invocando los métodos `onWebSocketText()` y `onWebSocketClose()` de `PlayerConnection`
- Jetty mantiene un pool de threads que escala dinámicamente según la demanda, típicamente entre 8 y 200 threads

**Estructuras Thread-Safe:**
- `ConcurrentHashMap` en `GameStore.games` para almacenamiento concurrente de partidas
- `CopyOnWriteArrayList` para listas de jugadores y espectadores en `GameHandler` (optimizado para lecturas frecuentes, escrituras ocasionales)
- `CopyOnWriteArraySet` en LobbyManager.subscribers Maneja la lista de jugadores suscritos al lobby para recibir la lista de partidas. Ideal para muchas iteraciones de broadcast y pocas suscripciones/desuscripciones.
- Campo `@Volatile lastActivityMillis` garantiza visibilidad entre threads al evaluar inactividad

**Limpieza Programada:**
- `ScheduledExecutorService` (single-thread) ejecuta cada 60 segundos el método `startCleaner()`
- Elimina automáticamente partidas vacías o inactivas por más de 30 minutos

**Broadcast de Estado:**
- El envío de estado a jugadores y espectadores se realiza mediante las sesiones WebSocket de cada conexión

## Estructura de Archivos

```
src/main/kotlin/
├── Main.kt                 # Punto de entrada, configuración del servidor y mapping /ws
├── PlayerConnection.kt     # Ciclo de vida de conexión, parsing y delegación de mensajes
├── GameHandler.kt          # Estado de partida, validación y broadcast de game_state
├── GameStore.kt            # Registro global de partidas + limpiador concurrente
├── LobbyManager            # Registro de suscriptores a la lista de partidas activas y broadcast de la misma. 
├── IncomingMessages.kt     # Modelos tipados para mensajes entrantes
└── DataClasses.kt          # Modelos de datos (Player, Spectator, SimpleMove)
```

## Protocolo WebSocket

### Mensajes Entrantes
Todos los mensajes requieren el campo `type`:

| Tipo | Payload | Descripción |
|------|---------|-------------|
| `create_game` | - | Crea una nueva partida |
| `join_game` | `{ gameId }` | Une al jugador a una partida existente |
| `join_viewer_game` |  `{ gameId }` | Une al espectador a una partida |
| `make_move` | `{ from, to }` | Ejecuta un movimiento (notación algebraica) |
| `leave_game` | - | Abandona la partida actual |
| `subscribe_list_games` | - | El jugador solicita suscribirse para recibir lista de partidas activas |
| `unsubscribe_list_games` | - | El jugador solicita desuscribirse para no recibir la lista de partidas |


### Mensajes Salientes

| Tipo | Payload | Descripción |
|------|---------|-------------|
| `connected` | `{ playerId }` | Confirmación de conexión establecida |
| `game_created` | `{ gameId }` | Partida creada exitosamente |
| `joined_game` | `{ gameId }` | Unión exitosa a partida |
| `move_made` | `{ from, to }` | Movimiento ejecutado |
| `game_state` | `{ ...estado completo... }` | Estado actualizado de la partida |
| `left_game` | - | Confirmación de abandono |
| `opponent_left` | - | Notificación de abandono del oponente |
| `games_list` | `{ games: [{id, players, spectators}, ...] }` | Lista de partidas activas |
| `error` | `{ mensaje }` | Error en el procesamiento |

## Requirements

- **Java:** JDK 21 o superior

## Development

### Run en Modo Desarrollo
```cmd
cd server

gradlew.bat run
```

El servidor estará disponible en `ws://localhost:3000/ws`

## Build

### JAR Ejecutable
```cmd
gradlew.bat build
```

El archivo JAR se genera en `build/libs/server-1.0.0.jar`

### Run desde JAR
```cmd
java -jar build\libs\server-1.0.0.jar --port=3000
```

Configuración de puerto disponible mediante:
- Argumento CLI: `--port=8080`
- Variable de entorno: `PORT=8080`

## Native Compile (Opcional)

> ⚠️ La compilación nativa no ha sido completamente testeada y puede presentar errores en runtime.

Requiere instalación previa de GraalVM con Native Image.

```cmd
gradlew.bat nativeCompile
```

El binario nativo se genera en:
- **Windows:** `build\native\nativeCompile\ChessServer.exe`
- **Linux/macOS:** `build/native/nativeCompile/ChessServer`

El ejecutable nativo ofrece arranque más rápido y menor consumo de memoria en comparación con la JVM.
