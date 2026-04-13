from enum import Enum

class GameState(Enum):
    WAITING_PLAYERS = "waiting_players"
    PLACEMENT_PHASE = "placement_phase"
    BATTLE_PHASE = "battle_phase" 
    GAME_OVER = "game_over"

class MessageType(Enum):
    PLAYER_CONNECT = "player_connect"
    PLAYER_DISCONNECT = "player_disconnect"
    PLAYERS_READY = "players_ready"
    PLACE_SHIPS = "place_ships"
    SHOT = "shot"
    SHOT_RESULT = "shot_result"
    GAME_START = "game_start"
    GAME_UPDATE = "game_update"
    GAME_OVER = "game_over"
    ERROR = "error"