# Cliente (Godot 4.5)

Cliente implementado en Godot 4.5 que se conecta vía WebSocket al servidor para jugar ajedrez en tiempo real. La arquitectura está diseñada con singletons autoload para centralizar la lógica de networking y estado global, minimizando el acoplamiento entre escenas. Utiliza un sistema reactivo basado en señales que permite extensiones futuras como chat, reloj de juego o historial de movimientos. Exportable a Web (WASM) y Windows.

## Tech Stack
| Componente          | Uso                                          |
|---------------------|----------------------------------------------|
| Godot Engine (4.5)  | Motor principal, rendering y lógica de juego |
| GDScript            | Lenguaje de scripting                        |
| WebSocket           | Comunicación en tiempo real con el servidor  |
| HTML5/WASM (export) | Deployment web multiplataforma               |

## Arquitectura

El cliente utiliza dos **autoload singletons** definidos en `project.godot` que actúan como capa de abstracción:

### Networking (`Networking.gd`)
Responsable de toda la comunicación WebSocket:
- Gestiona la conexión mediante `WebSocketPeer`
- Parsea mensajes JSON entrantes en `_on_message()`
- Emite señales semánticas para cada tipo de mensaje recibido: `connected`, `game_state`, `move_made`, `opponent_left`, `error`, etc.
- Polling continuo en `_process()` mediante `peer.poll()` para procesar frames del socket
- Provee métodos públicos para enviar acciones: `send_create_game()`, `send_join_game()`, `send_make_move()`, `send_leave_game()`

### Store (`Store.gd`)
Mantiene el estado global de la partida:
- Estado del tablero (`board`): representación de todas las piezas
- Movimientos permitidos (`allowed_moves`): calculados por el servidor
- Turno actual (`player_turn`)
- Último movimiento (`last_move`)
- Flags de fin de juego (checkmate, stalemate)
- Emite señales cuando el estado cambia: `board_changed`, `turn_changed`, `game_over`, `state_changed`

### Flujo de Datos

**Proceso completo:**

1. **Acción del usuario:** La escena de menú o tablero invoca métodos de `Networking` (ej: `send_create_game()`, `send_make_move()`)
2. **Envío al servidor:** `Networking` serializa y envía el mensaje JSON vía WebSocket
3. **Respuesta del servidor:** El servidor procesa la acción y responde con mensajes como `game_state`, `move_made`, etc.
4. **Parsing:** `Networking._on_message()` parsea el JSON y emite señales semánticas
5. **Actualización de estado:** `Store` escucha estas señales y ejecuta `apply_state()` para normalizar y actualizar estructuras internas
6. **Notificación:** `Store` emite señales específicas (`board_changed`, `turn_changed`, etc.)
7. **Renderizado:** Las escenas del tablero escuchan estas señales y actualizan la UI

**Validación local:** Antes de enviar movimientos, `Store.try_move()` valida contra `allowed_moves` y turno actual para prevenir peticiones inválidas al servidor

### Sistema Reactivo

El diseño basado en señales elimina la necesidad de polling manual del estado:

| Señal                         | Emisor        | Propósito                                  |
|-------------------------------|---------------|--------------------------------------------|
| `board_changed(board)`        | Store         | Actualizar sprites de piezas en el tablero |
| `highlight_moves(highlights)` | Store         | Resaltar casillas válidas para movimiento  |
| `turn_changed(player_id)`     | Store         | Mostrar indicador de turno                 |
| `game_over(winner_color)`     | Store         | Mostrar overlay de fin de juego            |
| `connected(player_id)`        | Networking    | Confirmación de conexión establecida       |
| `opponent_left`               | Networking    | Notificar abandono del oponente            |
| `player_left  `               | Networking    | Notificar abandono de jugador a espectador |
| `left_game`                   | Networking    | Confirmacion de abandono de partida        |
| `joined_as_spectator`         | Networking    | Confirmacion de ingreso como espectador    |
| `joined_game`                 | Networking    | Confirmacion de ingreso como jugador       |
| `games_list(payload)`         | Networking    | Recepcion de lista de partidas activas     |
| `back_pressed()`              | GameListPanel | Notifica volver al menú.                   |
| `refresh_pressed()`           | GameListPanel | Solicita actualizar la lista de partidas.  |
| `join_game_requested(game_id)`| GameListPanel | Pedido para unirse como jugador.           |
| `join_game_viewer(game_id)`   | GameListPanel | Pedido para unirse como espectador.        |
| `last_move_changed(last_move)` | Store        | Notifica último movimiento.                |
| `new_game_started()`           | Store        | Notifica que una nueva partida ha comenzado|

Las escenas solo se suscriben a las señales relevantes, promoviendo bajo acoplamiento y fácil extensibilidad.

## Estructura de Archivos

```
client/
├── project.godot          # Configuración del proyecto, autoloads
├── scripts/
│   ├── Networking.gd      # Singleton WebSocket, parsing y señales
│   └── Store.gd           # Singleton de estado global
├── logic/                 # Lógica específica de cada escena
└── scenes/                # Escenas Godot (menú, tablero, piezas, etc.)
```

## Protocolo WebSocket

El cliente utiliza el mismo protocolo que el servidor. Ver README del servidor para detalles completos.

### Mensajes Salientes
- `create_game` - Crear nueva partida
- `join_game` - Unirse a partida existente
- `make_move` - Ejecutar movimiento
- `leave_game` - Abandonar partida
- `join_viewer_game` - Unirse a la partida como espectador
- `subscribe_list_games` - Suscribirse para recibir la lista de partidas activas
- `unsubscribe_list_games` - Desuscribirse para dejar de recibir la lista de partidas activas
- `exit_game` - Finalizar la conexión.

### Validación Local

`Store.try_move(from, to)` implementa validación pre-envío:
- Verifica que el movimiento exista en `allowed_moves`
- Confirma que sea el turno del jugador actual
- Previene spam de movimientos inválidos

La autoridad final siempre reside en el servidor.

## Requirements

- **Desarrollo:** Godot Engine 4.5+
- **Web:** Navegador moderno con soporte WebAssembly y WebSocket
- **Windows:** Windows 10+ (x64)
- **Servidor:** Instancia del servidor corriendo y accesible (ver README del servidor)

## Development

### Setup
1. Abrir Godot 4.5+ y cargar `client/project.godot`
2. **Importante:** Asegurarse de tener el servidor corriendo localmente (ver README del servidor)

### Run
- Presionar **F5** o botón "Play" en el editor de Godot
- Ejecutar escena específica: **F6** o botón "Play Scene"

El cliente intentará conectarse automáticamente al servidor en el arranque.

## Build

### Windows
1. En Godot: **Project → Export**
2. Seleccionar template de Windows
3. Configurar nombre del ejecutable (ej: `Chess-M6.exe`)
4. Click en **Export Project**

### Web (WASM)
1. En Godot: **Project → Export**
2. Seleccionar template HTML5
3. Exportar genera archivos: `.html`, `.wasm`, `.pck`
4. Servir archivos estáticos con cualquier servidor HTTP:

```bash
# Python
python -m http.server 8000

# Node.js
npx http-server
```

5. Acceder desde navegador: `http://localhost:8000`

**Nota:** El servidor WebSocket debe ser accesible desde el navegador.
