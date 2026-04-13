import os

DEFAULT_SERVER_HOST = "localhost"
DEFAULT_SERVER_PORT = 8888
DEFAULT_HOST_ALL_INTERFACES = "0.0.0.0"
DEFAULT_HOST_LOCALHOST = "localhost"
DEFAULT_HOST_LOOPBACK = "127.0.0.1"
NETWORK_BUFFER_SIZE = 1024
NETWORK_TIMEOUT = 1.0

SERVER_READ_TIMEOUT = 1.0
SERVER_CLOSE_TIMEOUT = 5
JSON_DECODE_MAX_RETRIES = 3
CONNECTION_CHECK_INTERVAL = 1.0

GRID_SIZE = 10
CELL_MARGIN = 2

SHIP_SIZES = [5, 4, 3, 3, 2]
SHIP_NAMES = {
    5: "Portaaviones",
    4: "Destructor Acorazado",
    3: "Barco de Ataque",
    2: "Lancha Rapida"
}

CELL_EMPTY = 0
CELL_SHIP = 1
CELL_HIT = 2
CELL_WATER_HIT = 3

MAX_PLAYERS = 2

MIN_PORT_NUMBER = 1
MAX_PORT_NUMBER = 65535
MIN_COORDINATE = 0
MAX_COORDINATE = 9

MESSAGE_TYPE_MAX_LENGTH = 50
ERROR_MESSAGE_MAX_LENGTH = 200
PLAYER_ID_LENGTH = 8

UUID_SHORT_LENGTH = 8

LOG_FORMAT = '%(asctime)s - %(levelname)s - %(message)s'

DEBUG_NETWORK = False
DEBUG_GAME_STATE = False

BANNER_WIDTH = 80
FIREWALL_TCP_PROTOCOL = '/tcp'
MAX_METHOD_LINES = 15
ARG_VALIDATION_EXIT_SUCCESS = 0
ARG_VALIDATION_EXIT_ERROR = 1
USER_INPUT_YES = 's'
USER_INPUT_NO = 'n'

UTF8_ENCODING = 'utf-8'
JSON_MESSAGE_DELIMITER = '\n'
SHOT_RESULT_HIT = 'hit'
SHOT_RESULT_MISS = 'miss'
SHOT_RESULT_SUNK = 'sunk'

SHOT_RESULTS = {
    'HIT': 'hit',
    'MISS': 'miss',
    'SUNK': 'sunk'
}

LOCALHOST_HOST = "localhost"
CONNECTION_ERROR_MESSAGES = {
    'SERVER_FULL': "Servidor lleno. Máximo 2 jugadores.",
    'NOT_YOUR_TURN': 'No es tu turno',
    'SHIPS_PLACEMENT_ERROR': 'Error colocando barcos',
    'OPPONENT_DISCONNECTED': 'Tu oponente se ha desconectado'
}
GAME_MESSAGES = {
    'GAME_STARTED': 'El juego ha comenzado - Pantalla de juego activa',
    'WINNER': '¡Ganaste!',
    'LOSER': 'Perdiste'
}

SHIP_POSITION_NOT_HIT_THRESHOLD = 0
SHIP_INITIAL_HITS = 0
SHIP_ORIENTATION_HORIZONTAL = True
SHIP_ORIENTATION_VERTICAL = False
ERROR_MESSAGES_SHIP = {
    'EMPTY_POSITIONS': "Las posiciones no pueden estar vacías",
    'MISSING_INIT_PARAMS': "Debe proporcionar 'size' o 'positions' para inicializar el barco"
}

THREAD_DAEMON_MODE = True
NETWORK_ENCODING = 'utf-8'
MESSAGE_BUFFER_SPLIT_LIMIT = 1
MESSAGE_TYPES = {
    'PLAYER_CONNECT': 'player_connect',
    'PLAYERS_READY': 'players_ready',
    'GAME_START': 'game_start',
    'GAME_UPDATE': 'game_update',
    'SHOT_RESULT': 'shot_result',
    'GAME_OVER': 'game_over',
    'PLAYER_DISCONNECT': 'player_disconnect',
    'ERROR': 'error',
    'PLACE_SHIPS': 'place_ships',
    'SHOT': 'shot',
    'BOMB_ATTACK': 'bomb_attack',
    'AIR_STRIKE': 'air_strike',
    'START_GAME': 'start_game'
}
NETWORK_LOG_MESSAGES = {
    'NOT_CONNECTED': "No conectado al servidor",
    'SEND_SUCCESS': "Mensaje enviado exitosamente",
    'SERVER_DISCONNECTED': "Error: Servidor desconectado durante envío",
    'NO_DATA_RECEIVED': "Servidor desconectado - No se recibieron más datos",
    'CONNECTION_RESET': "Servidor desconectado - Conexión resetteada/abortada",
    'NOTIFYING_DISCONNECT': "Notificando desconexión del servidor",
    'NO_DISCONNECT_CALLBACK': "No hay callback configurado para server_disconnect",
    'NO_GAME_START_CALLBACK': "No hay callback configurado para game_start",
    'NO_PLAYER_DISCONNECT_CALLBACK': "No hay callback configurado para player_disconnect",
    'UNKNOWN_PLAYER': 'desconocido',
    'DEFAULT_DISCONNECT_MESSAGE': 'Jugador desconectado',
    'DEFAULT_ERROR_MESSAGE': 'Error desconocido',
    'NO_CONNECTION': "ERROR: No hay conexión al servidor"
}

ANIMATION_FRAME_TIME = 16

GAME_PHASE_PLACEMENT = "placement"
GAME_PHASE_WAITING_BATTLE = "waiting_for_battle"
GAME_PHASE_BATTLE = "battle"

DEFAULT_SHIP_SIZE = 2
SHIP_HORIZONTAL_DEFAULT = True
SHIP_VERTICAL = False
INITIAL_SHIP_INDEX = 0

EXPECTED_ENEMY_SHIPS = [
    {"name": "Portaaviones", "size": 5, "sunk": False},
    {"name": "Destructor Acorazado", "size": 4, "sunk": False},
    {"name": "Barco de Ataque #1", "size": 3, "sunk": False},
    {"name": "Barco de Ataque #2", "size": 3, "sunk": False},
    {"name": "Lancha Rapida", "size": 2, "sunk": False}
]

BOARD_SIZE_DEFAULT = 450

SERVER_TEXT = {
    'GAME_STARTED': 'El juego ha comenzado - Pantalla de juego activa',
    'WINNER': '¡Ganaste!',
    'LOSER': 'Perdiste',
    'SHIPS_SENT': "Barcos enviados al servidor",
    'BATTLE_START': "Iniciando fase de batalla...",
    'SUNK_ENEMY_SHIP': "¡HUNDISTE EL {} ENEMIGO!",
    'ENEMY_SUNK_MY_SHIP': "¡El enemigo hundió tu {}!",
    'GAME_RESET': "Estado del juego reseteado para nueva partida"
}

PLAYER_STATE_CONNECTED = "connected"
PLAYER_STATE_READY = "ready"
PLAYER_STATE_PLAYING = "playing"
PLAYER_STATE_DISCONNECTED = "disconnected"

GAME_STATE_WAITING_PLAYERS = "waiting_players"
GAME_STATE_PLACEMENT = "placement"
GAME_STATE_BATTLE = "battle"
GAME_STATE_FINISHED = "finished"

FIRST_COORDINATE = 0
SECOND_COORDINATE = 1