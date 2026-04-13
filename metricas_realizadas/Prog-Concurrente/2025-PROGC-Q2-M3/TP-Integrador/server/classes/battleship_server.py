import asyncio
import json
import random
import uuid
import sys
import os
from typing import Dict, Optional, Any

sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from constants import *
from classes.enums import GameState, MessageType
from classes.player import Player

class BattleshipServer:
    
    def __init__(self, host: str = DEFAULT_HOST_ALL_INTERFACES, port: int = DEFAULT_SERVER_PORT):
        self.host = host
        self.port = port
        self.players: Dict[str, Player] = {}
        self.max_players = MAX_PLAYERS
        self.game_state = GameState.WAITING_PLAYERS
        self.current_turn: Optional[str] = None
        
    async def start_server(self) -> None:
        print(f"Starting Battleship server on {self.host}:{self.port}...")
        server = await asyncio.start_server(
            self.handle_client, self.host, self.port
        )
        
        async with server:
            await server.serve_forever()

    async def handle_client(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        writer.get_extra_info('peername')
        player_id = self._generate_player_id()
        
        if not await self._validate_new_connection(writer):
            return
            
        await self._create_and_register_player(player_id, writer)
        await self._handle_client_communication(player_id, reader)
        
    def _generate_player_id(self) -> str:
        return str(uuid.uuid4())[:UUID_SHORT_LENGTH]
        
    async def _validate_new_connection(self, writer: asyncio.StreamWriter) -> bool:
        if len(self.players) >= self.max_players:
            await self.send_error(writer, CONNECTION_ERROR_MESSAGES['SERVER_FULL'])
            writer.close()
            await writer.wait_closed()
            return False
        return True
        
    async def _create_and_register_player(self, player_id: str, writer: asyncio.StreamWriter) -> Player:
        player = Player(player_id, writer)
        self.players[player_id] = player
        
        await player.send_message(MessageType.PLAYER_CONNECT, {'player_id': player_id})
        await self.broadcast_players_status()
        
        return player
        
    async def _handle_client_communication(self, player_id: str, reader: asyncio.StreamReader) -> None:
        try:
            await self._client_message_loop(player_id, reader)
        except asyncio.CancelledError:
            pass
        except Exception as e:
            pass
        finally:
            await self._cleanup_client_connection(player_id)
            
    async def _client_message_loop(self, player_id: str, reader: asyncio.StreamReader) -> None:
        while self.players.get(player_id) is not None:
            try:
                line = await asyncio.wait_for(reader.readline(), timeout=SERVER_READ_TIMEOUT)
                
                if not line:
                    break
                    
                await self._process_client_message(player_id, line)
                
            except asyncio.TimeoutError:
                continue
            except ConnectionResetError:
                break
            except Exception as e:
                break
                
    async def _process_client_message(self, player_id: str, line: bytes) -> None:
        raw_data = line.decode(UTF8_ENCODING).strip()
        
        if not raw_data:
            return
            
        try:
            message = json.loads(raw_data)
            await self.process_message(player_id, message)
        except json.JSONDecodeError as e:
            return
        except Exception as e:
            return
            
    async def _cleanup_client_connection(self, player_id: str) -> None:
        await self.disconnect_player(player_id)
        
        if player_id in self.players:
            try:
                writer = self.players[player_id].writer
                writer.close()
                await writer.wait_closed()
            except Exception as e:
                pass

    async def send_error(self, writer: asyncio.StreamWriter, error_message: str) -> None:
        try:
            message = self._create_error_message(error_message)
            await self._send_raw_error_message(writer, message)
        except Exception as e:
            pass
            
    def _create_error_message(self, error_message: str) -> str:
        message = {
            'type': MessageType.ERROR.value,
            'data': {'error': error_message}
        }
        return json.dumps(message) + JSON_MESSAGE_DELIMITER
        
    async def _send_raw_error_message(self, writer: asyncio.StreamWriter, message: str) -> None:
        writer.write(message.encode(UTF8_ENCODING))
        await writer.drain()

    async def disconnect_player(self, player_id: str) -> None:
        if player_id not in self.players:
            return
            
        if self._should_notify_opponent(player_id):
            await self._notify_opponent_disconnection(player_id)
            
        self._remove_player_and_reset_game(player_id)
        await self.broadcast_players_status()
        
    def _should_notify_opponent(self, player_id: str) -> bool:
        active_game_states = [GameState.PLACEMENT_PHASE, GameState.BATTLE_PHASE]
        return (self.game_state in active_game_states and 
                len(self.players) == MAX_PLAYERS)
                
    async def _notify_opponent_disconnection(self, player_id: str) -> None:
        opponent_id = self._find_opponent_id(player_id)
        if opponent_id:
            await self._send_disconnection_message(opponent_id, player_id)
            
    def _find_opponent_id(self, player_id: str) -> Optional[str]:
        for pid in self.players:
            if pid != player_id:
                return pid
        return None
        
    async def _send_disconnection_message(self, opponent_id: str, disconnected_player_id: str) -> None:
        opponent = self.players[opponent_id]
        
        success = await opponent.send_message(MessageType.PLAYER_DISCONNECT, {
            'disconnected_player': disconnected_player_id,
            'message': CONNECTION_ERROR_MESSAGES['OPPONENT_DISCONNECTED'],
            'return_to_menu': True
        })
            
    def _remove_player_and_reset_game(self, player_id: str) -> None:
        del self.players[player_id]
        
        if len(self.players) < MAX_PLAYERS:
            self.game_state = GameState.WAITING_PLAYERS
            self.current_turn = None

    async def broadcast_players_status(self) -> None:
        message_data = self._create_players_status_message()
        
        for player in self.players.values():
            await player.send_message(MessageType.PLAYERS_READY, message_data)
            
    def _create_players_status_message(self) -> Dict[str, Any]:
        players_ready = len(self.players) >= MAX_PLAYERS
        return {
            'connected_players': len(self.players),
            'max_players': self.max_players,
            'players_ready': players_ready,
            'game_state': self.game_state.value
        }

    async def process_message(self, player_id: str, message: Dict[str, Any]) -> None:
        message_type = message.get('type')
        data = message.get('data', {})

        if player_id not in self.players:
            return
        
        player = self.players[player_id]
        
        message_handlers = {
            'place_ships': lambda: self.handle_place_ships(player, data),
            'shot': lambda: self.handle_shot(player_id, data),
            'bomb_attack': lambda: self.handle_bomb_attack(player_id, data),
            'air_strike': lambda: self.handle_air_strike(player_id, data),
            'start_game': lambda: self.handle_start_game()
        }
        
        handler = message_handlers.get(message_type)
        if handler:
            await handler()

    async def handle_place_ships(self, player: Player, data: Dict[str, Any]) -> None:
        try:
            ships_data = data.get('ships', [])
            self._clear_player_ships(player)
            self._place_player_ships(player, ships_data)
            
            player.ships_placed = True
            
            await self._check_and_start_battle_if_ready()
            
        except Exception as e:
            await player.send_message(MessageType.ERROR, 
                                    {'error': CONNECTION_ERROR_MESSAGES['SHIPS_PLACEMENT_ERROR']})
            
    def _clear_player_ships(self, player: Player) -> None:
        player.ships = []
        player.grid = [[CELL_EMPTY for _ in range(GRID_SIZE)] for _ in range(GRID_SIZE)]
        
    def _place_player_ships(self, player: Player, ships_data: list) -> None:
        for ship_positions in ships_data:
            if isinstance(ship_positions, list) and len(ship_positions) > 0:
                player.place_ship(ship_positions)
                
    async def _check_and_start_battle_if_ready(self) -> None:
        players_ready = self.all_players_ready()
        
        if players_ready:
            await self.start_battle_phase()

    async def handle_bomb_attack(self, shooter_id: str, data: Dict[str, Any]) -> None:
        if not self._validate_shot_conditions(shooter_id):
            return
            
        targets = data.get('targets', [])
        opponent_id = self._find_opponent_id(shooter_id)
        bomb_results = []
        for target in targets:
            x, y = target[FIRST_COORDINATE], target[SECOND_COORDINATE]
            if self._validate_shot_coordinates(x, y):
                shot_result = await self._process_shot_result(shooter_id, opponent_id, x, y)
                if shot_result is None:
                    return
                
                bomb_results.append(shot_result)
        self.current_turn = opponent_id
        await self.broadcast_game_state()

    async def handle_air_strike(self, shooter_id: str, data: Dict[str, Any]) -> None:
        if not self._validate_shot_conditions(shooter_id):
            return
            
        targets = data.get('targets', [])
        opponent_id = self._find_opponent_id(shooter_id)
        air_strike_results = []
        for target in targets:
            if len(target) >= 2:
                x, y = target[FIRST_COORDINATE], target[SECOND_COORDINATE]
                if self._validate_shot_coordinates(x, y):
                    shot_result = await self._process_shot_result(shooter_id, opponent_id, x, y)
                    
                    if shot_result is None:
                        return
                    
                    air_strike_results.append(shot_result)
        self.current_turn = opponent_id
        await self.broadcast_game_state()

    async def handle_shot(self, shooter_id: str, data: Dict[str, Any]) -> None:
        if not self._validate_shot_conditions(shooter_id):
            return
            
        x, y = data.get('x'), data.get('y')
        if not self._validate_shot_coordinates(x, y):
            return
        
        opponent_id = self._find_opponent_id(shooter_id)
        if not opponent_id:
            return
 
        shot_result = await self._process_shot_result(shooter_id, opponent_id, x, y)
        
        if shot_result is None:
            return
        
        should_change_turn = shot_result == SHOT_RESULT_MISS
        
        if should_change_turn:
            self.current_turn = opponent_id
        await self.broadcast_game_state()
        
    def _validate_shot_conditions(self, shooter_id: str) -> bool:
        if self.game_state != GameState.BATTLE_PHASE:
            return False
            
        if self.current_turn != shooter_id:
            asyncio.create_task(self.players[shooter_id].send_message(
                MessageType.ERROR, {'error': CONNECTION_ERROR_MESSAGES['NOT_YOUR_TURN']}
            ))
            return False
            
        return True
        
    def _validate_shot_coordinates(self, x: Any, y: Any) -> bool:
        return isinstance(x, int) and isinstance(y, int)
        
    async def _process_shot_result(self, shooter_id: str, opponent_id: str, x: int, y: int) -> Optional[str]:
        opponent = self.players[opponent_id]
        shot_result = opponent.receive_shot(x, y)
        result = shot_result['result']
        
        shot_data = self._create_shot_data(x, y, result, shooter_id, opponent_id, shot_result)
        
        await self._broadcast_shot_result(shot_data)
        
        if opponent.all_ships_sunk():
            await self.end_game(shooter_id)
            return None
            
        return result
            
    def _create_shot_data(self, x: int, y: int, result: str, shooter_id: str, 
                         opponent_id: str, shot_result: Dict[str, Any]) -> Dict[str, Any]:
        shot_data = {
            'x': x, 'y': y, 'result': result,
            'shooter': shooter_id, 'target': opponent_id
        }
        
        if result == SHOT_RESULT_SUNK and 'ship_info' in shot_result:
            shot_data['ship_info'] = shot_result['ship_info']
            
        return shot_data
        
    async def _broadcast_shot_result(self, shot_data: Dict[str, Any]) -> None:
        for player in self.players.values():
            await player.send_message(MessageType.SHOT_RESULT, shot_data)
            
    async def _handle_turn_change(self, result: str, opponent_id: str) -> None:
        if result == SHOT_RESULT_MISS:
            self.current_turn = opponent_id
        await self.broadcast_game_state()

    async def handle_start_game(self) -> None:
        if len(self.players) == MAX_PLAYERS:
            await self._start_game_for_all_players()
            
    async def _start_game_for_all_players(self) -> None:
        self.game_state = GameState.PLACEMENT_PHASE
        
        start_message = self._create_game_start_message()
        
        for player_id, player in self.players.items():
            await self._send_game_start_to_player(player_id, player, start_message)
        
    def _create_game_start_message(self) -> Dict[str, Any]:
        return {
            'phase': 'placement',
            'message': GAME_MESSAGES['GAME_STARTED'],
            'redirect_to_game': True
        }
        
    async def _send_game_start_to_player(self, player_id: str, player: Player, 
                                       start_message: Dict[str, Any]) -> None:
        await player.send_message(MessageType.GAME_START, start_message)

    async def start_battle_phase(self) -> None:
        self.game_state = GameState.BATTLE_PHASE
        
        player_ids = list(self.players.keys())
        
        if not self._validate_battle_start(player_ids):
            return
            
        self.current_turn = self._choose_starting_player(player_ids)
        
        await self.broadcast_game_state()
        
    def _validate_battle_start(self, player_ids: list) -> bool:
        if len(player_ids) < MAX_PLAYERS:
            return False
        return True
        
    def _choose_starting_player(self, player_ids: list) -> str:
        return random.choice(player_ids)
        
    async def broadcast_game_state(self) -> None:
        game_data = self._create_game_state_data()
        
        for player_id, player in self.players.items():
            await self._send_game_state_to_player(player_id, player, game_data)
            
    def _create_game_state_data(self) -> Dict[str, Any]:
        return {
            'phase': self.game_state.value,
            'current_turn': self.current_turn,
            'players': {pid: {'ready': p.ships_placed} for pid, p in self.players.items()}
        }
        
    async def _send_game_state_to_player(self, player_id: str, player: Player, 
                                       game_data: Dict[str, Any]) -> None:
        await player.send_message(MessageType.GAME_UPDATE, game_data)

    async def end_game(self, winner_id: str) -> None:
        self.game_state = GameState.GAME_OVER
        
        for player_id, player in self.players.items():
            await self._send_game_over_message(player_id, player, winner_id)
            
    async def _send_game_over_message(self, player_id: str, player: Player, winner_id: str) -> None:
        is_winner = player_id == winner_id
        message = GAME_MESSAGES['WINNER'] if is_winner else GAME_MESSAGES['LOSER']
        
        await player.send_message(MessageType.GAME_OVER, {
            'winner': winner_id,
            'is_winner': is_winner,
            'message': message
        })

    def all_players_ready(self) -> bool:
        return (len(self.players) == MAX_PLAYERS and 
                all(player.ships_placed for player in self.players.values()))