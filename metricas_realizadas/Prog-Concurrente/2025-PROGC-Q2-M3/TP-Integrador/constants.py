# Importación condicional de pygame para evitar errores en el servidor
try:
    import pygame
    PYGAME_AVAILABLE = True
except ImportError:
    PYGAME_AVAILABLE = False
    
import os

DEFAULT_SERVER_HOST = "localhost"
DEFAULT_SERVER_PORT = 8888
NETWORK_BUFFER_SIZE = 1024
try:
    import pygame
    PYGAME_AVAILABLE = True
except ImportError:
    PYGAME_AVAILABLE = False

import os

DEFAULT_SERVER_HOST = "localhost"
DEFAULT_SERVER_PORT = 8888
NETWORK_BUFFER_SIZE = 1024
NETWORK_TIMEOUT = 1.0

MIN_WINDOW_WIDTH = 1200
MIN_WINDOW_HEIGHT = 800
INITIAL_WINDOW_WIDTH = 1200
INITIAL_WINDOW_HEIGHT = 800

TARGET_FPS = 60

GRID_SIZE = 10
BOARD_SIZE_DEFAULT = 450
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

CONNECTION_INPUT_WIDTH = 450
CONNECTION_INPUT_HEIGHT = 55
CONNECTION_BUTTON_WIDTH = 200
CONNECTION_BUTTON_HEIGHT = 55
MAX_HOST_LENGTH = 50
MAX_PORT_LENGTH = 5
CURSOR_BLINK_INTERVAL = 500

MENU_BUTTON_WIDTH = 300
MENU_BUTTON_HEIGHT = 60
MUTE_BUTTON_WIDTH = 100
MUTE_BUTTON_HEIGHT = 40
MUTE_BUTTON_MARGIN = 20

GAME_OVER_BUTTON_WIDTH = 200
GAME_OVER_BUTTON_HEIGHT = 60

FONT_SIZE_LARGE = 96
FONT_SIZE_TITLE = 56
FONT_SIZE_BOARD_TITLE = 42
FONT_SIZE_DIALOG_TITLE = 48
FONT_SIZE_NORMAL = 32
FONT_SIZE_INFO = 28
FONT_SIZE_SMALL = 24
FONT_SIZE_COORD = 20

PANEL_PADDING = 15
COORD_SPACE = 40
TITLE_SPACE = 80
BOARD_TITLE_SPACE = 60
INFO_SPACE = 150
BOARD_SPACING = 150
TITLE_SPACING = 15
WINDOW_MARGINS = 160

MUSIC_VOLUME_MENU = 0.3
MUSIC_VOLUME_GAME = 0.2
MISSILE_SOUND_VOLUME = 0.3
SPLASH_SOUND_VOLUME = 0.25

MIXER_FREQUENCY = 44100
MIXER_SIZE = -16
MIXER_CHANNELS = 2
MIXER_BUFFER = 1024

COLOR_OCEAN_START = (30, 60, 90)
COLOR_OCEAN_END = (70, 130, 200)
COLOR_WATER = (70, 130, 180)
COLOR_WATER_DARK = (50, 100, 150)
COLOR_SHIP = (101, 67, 33)
COLOR_HIT = (220, 20, 60)
COLOR_MISS = (255, 255, 255)
COLOR_GRID = (30, 60, 100)
COLOR_HOVER = (255, 255, 0, 100)

COLOR_WHITE = (255, 255, 255)
COLOR_BLACK = (0, 0, 0)
COLOR_GREEN = (0, 255, 0)
COLOR_RED = (255, 0, 0)
COLOR_YELLOW = (255, 255, 0)

COLOR_BUTTON_CONNECT = (70, 130, 180)
COLOR_BUTTON_CONNECT_HOVER = (100, 149, 237)
COLOR_BUTTON_CONNECT_ACTIVE = (34, 139, 34)
COLOR_BUTTON_START = (34, 139, 34)
COLOR_BUTTON_START_HOVER = (50, 205, 50)
COLOR_BUTTON_DISABLED = (100, 100, 100)
COLOR_BUTTON_CANCEL = (180, 70, 70)
COLOR_BUTTON_CANCEL_HOVER = (220, 100, 100)
COLOR_BUTTON_MUTE = (70, 70, 70)
COLOR_BUTTON_MUTE_HOVER = (100, 100, 100)

COLOR_PANEL_MY_BOARD = (20, 40, 60)
COLOR_PANEL_ENEMY_BOARD = (40, 20, 20)
COLOR_PANEL_INFO = (25, 45, 85)
COLOR_PANEL_MY_SHIPS = (20, 60, 40)
COLOR_PANEL_ENEMY_SHIPS = (60, 20, 20)

COLOR_BORDER_MY_BOARD = (100, 149, 237)
COLOR_BORDER_ENEMY_BOARD = (220, 20, 60)
COLOR_BORDER_INFO = (120, 160, 255)
COLOR_BORDER_MY_SHIPS = (100, 200, 120)
COLOR_BORDER_ENEMY_SHIPS = (200, 100, 100)

PANEL_ALPHA = 100
PANEL_ALPHA_STRONG = 130
PANEL_ALPHA_INFO = 180
OVERLAY_ALPHA = 120
PREVIEW_ALPHA = 60
GAME_OVER_ALPHA = 180

SHIP_HULL_COLOR = (45, 55, 65)
SHIP_DECK_COLOR = (120, 100, 80)
SHIP_METAL_COLOR = (85, 85, 85)
SHIP_CANNON_COLOR = (40, 40, 40)
SHIP_DETAIL_COLOR = (200, 180, 140)
SHIP_WINDOW_COLOR = (100, 150, 200)
SHIP_RADAR_COLOR = (60, 60, 60)
SHIP_WATER_LINE_COLOR = (65, 75, 85)

EXPLOSION_COLORS = [
    (255, 0, 0),
    (255, 100, 0),
    (255, 200, 0),
    (255, 255, 100)
]

SPLASH_COLORS = [
    (100, 150, 255),
    (150, 200, 255),
    (200, 230, 255)
]

MISSILE_SIZE_HIT = 3
MISSILE_SIZE_MISS = 4

SHIP_STATUS_PANEL_WIDTH = 200
SHIP_STATUS_PANEL_HEIGHT = 300
SHIP_STATUS_MARGIN = 10
SHIP_STATUS_Y_OFFSET_START = 40
SHIP_STATUS_Y_OFFSET_INCREMENT = 45

COORD_BG_MIN_WIDTH = 25
COORD_BG_MIN_HEIGHT = 20
COORD_BG_MARGIN = 8

IMAGE_MENU_PATH = "assets/images/menu.png"

SOUND_BACKGROUND_MENU = "assets/sounds/piratas.mp3"
SOUND_BACKGROUND_GAME = "assets/sounds/background.mp3"
SOUND_MISSILE_HIT = "assets/sounds/misil.mp3"
SOUND_WATER_SPLASH = "assets/sounds/waterSplash.mp3"

SERVER_READ_TIMEOUT = 1.0
SERVER_CLOSE_TIMEOUT = 5
JSON_DECODE_MAX_RETRIES = 3

UUID_SHORT_LENGTH = 8

MESSAGE_TYPE_MAX_LENGTH = 50
ERROR_MESSAGE_MAX_LENGTH = 200
PLAYER_ID_LENGTH = 8

LOG_FORMAT = '%(asctime)s - %(levelname)s - %(message)s'

MENU_BUTTON_Y_CONNECT = 400
MENU_BUTTON_Y_START = 500
MENU_STATUS_Y = 620

GAME_TITLE_Y = 35
GAME_INFO_Y_OFFSET = 75
GAME_STATUS_Y_OFFSET = 45

MIN_PORT_NUMBER = 1
MAX_PORT_NUMBER = 65535
MIN_COORDINATE = 0
MAX_COORDINATE = 9

BOARD_SIZE_SCALE_FACTOR = 0.8
MIN_COORD_FONT_SIZE = 20
MAX_COORD_FONT_SIZE = 32
COORD_FONT_CELL_RATIO = 2

CONNECTION_CHECK_INTERVAL = 1.0
ANIMATION_FRAME_TIME = 16

DEBUG_NETWORK = False
DEBUG_GAME_STATE = False
DEBUG_UI = False

BANNER_WIDTH = 80
FIREWALL_TCP_PROTOCOL = '/tcp'
DEFAULT_HOST_ALL_INTERFACES = "0.0.0.0"
DEFAULT_HOST_LOCALHOST = "localhost"
DEFAULT_HOST_LOOPBACK = "127.0.0.1"
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

DEFAULT_HOST_ALL_INTERFACES = "0.0.0.0"
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
JSON_MESSAGE_DELIMITER = '\n'
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
    'START_GAME': 'start_game'
}
NETWORK_LOG_MESSAGES = {
    'NOT_CONNECTED': "❌ No conectado al servidor",
    'SEND_SUCCESS': "✅ Mensaje enviado exitosamente",
    'SERVER_DISCONNECTED': "🔌 Error: Servidor desconectado durante envío",
    'NO_DATA_RECEIVED': "🔌 Servidor desconectado - No se recibieron más datos",
    'CONNECTION_RESET': "🔌 Servidor desconectado - Conexión resetteada/abortada",
    'NOTIFYING_DISCONNECT': "📞 Notificando desconexión del servidor",
    'NO_DISCONNECT_CALLBACK': "⚠️ No hay callback configurado para server_disconnect",
    'NO_GAME_START_CALLBACK': "⚠️ No hay callback configurado para game_start",
    'NO_PLAYER_DISCONNECT_CALLBACK': "⚠️ No hay callback configurado para player_disconnect",
    'UNKNOWN_PLAYER': 'desconocido',
    'DEFAULT_DISCONNECT_MESSAGE': 'Jugador desconectado',
    'DEFAULT_ERROR_MESSAGE': 'Error desconocido',
    'NO_CONNECTION': "❌ ERROR: No hay conexión al servidor"
}

MENU_BUTTON_DIVISION_FACTOR = 2
MENU_BUTTON_BORDER_WIDTH = 3
MUTE_BUTTON_BORDER_WIDTH = 2
MENU_FONT_SIZE_DEFAULT = None
MOUSE_BUTTON_LEFT = 1

MENU_STATE_DISCONNECTED = False
MENU_STATE_CONNECTED = True
MENU_STATE_PLAYERS_NOT_READY = False
MENU_STATE_PLAYERS_READY = True

MENU_TEXT = {
    'CONNECT_DEFAULT': 'Conectar a Servidor',
    'CONNECT_CONNECTED': 'Conectado',
    'START_GAME': 'Iniciar Partida',
    'MUTE_MUSIC': 'Silenciar',
    'UNMUTE_MUSIC': 'Musica',
    'STATUS_DISCONNECTED': "Desconectado del servidor",
    'STATUS_CONNECTING': "Conectado - Esperando segundo jugador...",
    'STATUS_READY': "¡2 jugadores conectados! Listo para iniciar",
    'MUSIC_MUTED': "🔇 Música silenciada",
    'MUSIC_UNMUTED': "🔊 Música reactivada",
    'ASSET_ERROR': "No se pudo cargar menu.png, usando fondo de color"
}

MENU_STATUS_COLOR_DISCONNECTED = (255, 100, 100)
MENU_BACKGROUND_COLOR_DEFAULT = (30, 30, 60)

MENU_EVENTS = {
    'CONNECT': "connect",
    'START_GAME': "start_game",
    'TOGGLE_MUSIC': "toggle_music"
}

GAME_TITLE_SPACE = 80
GAME_BOARD_TITLE_SPACE = 60
GAME_INFO_SPACE = 150
GAME_SCREEN_MARGIN = 160
GAME_BOARD_SPACING = 150
GAME_BOARD_SIZE_REDUCTION_FACTOR = 0.8
GAME_SCREEN_DIVISION_FACTOR = 2

GAME_PHASE_PLACEMENT = "placement"
GAME_PHASE_WAITING_BATTLE = "waiting_for_battle"
GAME_PHASE_BATTLE = "battle"

DEFAULT_SHIP_SIZE = 2
SHIP_HORIZONTAL_DEFAULT = True
SHIP_VERTICAL = False
INITIAL_SHIP_INDEX = 0

PANEL_PADDING = 15
COORD_SPACE = 40
TITLE_SPACING = 15
GAME_PANEL_ALPHA = 100
MY_PANEL_COLOR = (20, 40, 60)
ENEMY_PANEL_COLOR = (40, 20, 20)
PANEL_BORDER_WIDTH = 3

INFO_PANEL_HEIGHT = 110
INFO_PANEL_MARGIN = 15
INFO_PANEL_SIDE_MARGIN = 60
INFO_PANEL_ALPHA = 130
INFO_PANEL_COLOR = (25, 45, 85)

SHIPS_PANEL_WIDTH = 200
SHIPS_PANEL_HEIGHT = 300
SHIPS_PANEL_X_MARGIN = 10
SHIPS_PANEL_Y_OFFSET = 50
SHIPS_PANEL_ALPHA = 180
MY_SHIPS_PANEL_COLOR = (20, 60, 40)
ENEMY_SHIPS_PANEL_COLOR = (60, 20, 20)
SHIPS_PANEL_BORDER_WIDTH = 2

OCEAN_COLOR_TOP = {'r': 30, 'g': 60, 'b': 90}
OCEAN_COLOR_BOTTOM = {'r': 70, 'g': 130, 'b': 200}

SHIP_PREVIEW_ALPHA = 60
SHIP_PREVIEW_COLOR_VALID = (0, 255, 0)
SHIP_PREVIEW_COLOR_INVALID = (255, 50, 50)

MOUSE_LEFT_BUTTON = 1
MOUSE_RIGHT_BUTTON = 3
if PYGAME_AVAILABLE:
    KEY_ROTATE = pygame.K_r
else:
    KEY_ROTATE = 114

MISSILE_SOUND_VOLUME = 0.3
WATER_SPLASH_VOLUME = 0.25

GAME_TEXT = {
    'TITLE': "BATALLA NAVAL",
    'MY_FLEET': "MI FLOTA",
    'ENEMY': "ENEMIGO",
    'MY_SHIPS': "MIS BARCOS",
    'ENEMY_SHIPS': "BARCOS ENEMIGOS",
    'PLACING_SHIP': "Colocando barco de tamaño {} ({}) - Click derecho o R para rotar",
    'ALL_SHIPS_PLACED': "Todos los barcos colocados - Esperando al oponente...",
    'YOUR_TURN': "¡Tu turno! Haz click en el tablero enemigo para disparar",
    'OPPONENT_TURN': "Turno del oponente - Espera tu turno...",
    'WAITING_BATTLE': "Barcos colocados - Esperando que inicie la batalla...",
    'PREPARING': "Preparando juego...",
    'REMAINING_SHIPS': "Barcos restantes: {}",
    'HORIZONTAL': "Horizontal",
    'VERTICAL': "Vertical",
    'SUNK_STATUS': "HUNDIDO",
    'HITS_STATUS': "{}/{} impactos",
    'ACTIVE_STATUS': "ACTIVO",
    'SHIPS_SENT': "Barcos enviados al servidor",
    'BATTLE_START': "🚀 Iniciando fase de batalla...",
    'SUNK_ENEMY_SHIP': "🎯 ¡HUNDISTE EL {} ENEMIGO!",
    'ENEMY_SUNK_MY_SHIP': "💥 ¡El enemigo hundió tu {}!",
    'MISSILE_SOUND_PLAY': "🔊 Reproduciendo sonido de impacto de misil",
    'SPLASH_SOUND_PLAY': "🔊 Reproduciendo sonido de salpicadura de agua",
    'MISSILE_SOUND_ERROR': "❌ Error al reproducir sonido de misil: {}",
    'SPLASH_SOUND_ERROR': "❌ Error al reproducir sonido de salpicadura: {}",
    'MISSILE_FILE_NOT_FOUND': "❌ No se encontró el archivo misil.mp3",
    'SPLASH_FILE_NOT_FOUND': "❌ No se encontró el archivo waterSplash.mp3",
    'GAME_RESET': "🔄 Estado del juego reseteado para nueva partida",
    'REALISTIC_SHIPS_INIT': "✅ Sistema de barcos realistas inicializado"
}

SOUND_PATHS = {
    'MISSILE': os.path.join("assets", "sounds", "misil.mp3"),
    'WATER_SPLASH': os.path.join("assets", "sounds", "waterSplash.mp3")
}

SHIP_STATUS_COLORS = {
    'SUNK': (255, 100, 100),
    'ACTIVE': (100, 255, 100),
    'ENEMY_UNKNOWN': (255, 200, 100),
    'INFO_TEXT': (200, 200, 200),
    'WHITE': (255, 255, 255),
    'LIGHT_BLUE': (200, 220, 255)
}

GAME_FONT_SIZES = {
    'TITLE': FONT_SIZE_TITLE,
    'BOARD_TITLE': FONT_SIZE_BOARD_TITLE,
    'MAIN_INFO': 32,
    'SECONDARY_INFO': 28,
    'SHIP_STATUS': 24,
    'SHIP_TITLE': 28
}

EXPECTED_ENEMY_SHIPS = [
    {"name": "Portaaviones", "size": 5, "sunk": False},
    {"name": "Destructor Acorazado", "size": 4, "sunk": False},
    {"name": "Barco de Ataque #1", "size": 3, "sunk": False},
    {"name": "Barco de Ataque #2", "size": 3, "sunk": False},
    {"name": "Lancha Rapida", "size": 2, "sunk": False}
]

SHOT_RESULTS = {
    'HIT': 'hit',
    'MISS': 'miss',
    'SUNK': 'sunk'
}