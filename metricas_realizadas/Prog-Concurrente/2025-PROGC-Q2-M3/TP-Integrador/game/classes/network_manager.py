import asyncio
import json
import sys
import os
from typing import Optional, Callable, Any, Dict

sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from constants import (DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT, NETWORK_BUFFER_SIZE, 
                      NETWORK_ENCODING, MESSAGE_BUFFER_SPLIT_LIMIT,
                      MESSAGE_TYPES, NETWORK_LOG_MESSAGES, JSON_MESSAGE_DELIMITER)

class NetworkManager:
    def __init__(self):
        self._initialize_connection_attributes()
        self._initialize_server_config()
        self._initialize_callbacks()
        
    def _initialize_connection_attributes(self) -> None:
        self.reader: Optional[asyncio.StreamReader] = None
        self.writer: Optional[asyncio.StreamWriter] = None
        self.connected: bool = False
        self.player_id: Optional[str] = None
        self.receive_task: Optional[asyncio.Task] = None
        
    def _initialize_server_config(self) -> None:
        self.server_host: str = DEFAULT_SERVER_HOST
        self.server_port: int = DEFAULT_SERVER_PORT
        
    def _initialize_callbacks(self) -> None:
        self.on_players_ready: Optional[Callable[[Dict[str, Any]], None]] = None
        self.on_game_start: Optional[Callable[[Dict[str, Any]], None]] = None
        self.on_game_update: Optional[Callable[[Dict[str, Any]], None]] = None
        self.on_shot_result: Optional[Callable[[Dict[str, Any]], None]] = None
        self.on_game_over: Optional[Callable[[Dict[str, Any]], None]] = None
        self.on_server_disconnect: Optional[Callable[[], None]] = None
    
    async def connect_to_server(self, host: Optional[str] = None, port: Optional[int] = None) -> bool:
        self._update_server_config(host, port)
        
        try:
            return await self._establish_connection()
        except Exception as e:
            self.connected = False
            return False
            
    def _update_server_config(self, host: Optional[str], port: Optional[int]) -> None:
        if host:
            self.server_host = host
        if port:
            self.server_port = port
            
    async def _establish_connection(self) -> bool:
        self.reader, self.writer = await asyncio.open_connection(self.server_host, self.server_port)
        self.connected = True
        
        self._start_receive_task()
        return True
        
    def _start_receive_task(self) -> None:
        self.receive_task = asyncio.create_task(self.receive_messages())

    async def disconnect(self) -> None:
        if self.writer:
            self.connected = False
            if self.receive_task:
                self.receive_task.cancel()
            self.writer.close()
            await self.writer.wait_closed()
            self.writer = None
            self.reader = None
            
    async def send_message(self, message_type: str, data: Optional[Dict[str, Any]] = None) -> bool:
        if not self.connected:
            return False
            
        try:
            message = self._create_message(message_type, data)
            return await self._send_raw_message(message)
        except (ConnectionResetError, ConnectionAbortedError):
            return await self._handle_connection_error()
        except Exception as e:
            return False
            
    def _create_message(self, message_type: str, data: Optional[Dict[str, Any]]) -> str:
        message = {
            'type': message_type,
            'player_id': self.player_id,
            'data': data
        }
        return json.dumps(message) + JSON_MESSAGE_DELIMITER
        
    async def _send_raw_message(self, message_json: str) -> bool:
        self.writer.write(message_json.encode(NETWORK_ENCODING))
        await self.writer.drain()
        return True
        
    async def _handle_connection_error(self) -> bool:
        self.connected = False
        if self.on_server_disconnect:
            self.on_server_disconnect()
        return False
    
    async def receive_messages(self) -> None:
        buffer: str = ""
        
        while self.connected:
            buffer = await self._process_incoming_data(buffer)
            
    async def _process_incoming_data(self, buffer: str) -> str:
        try:
            data = await self._receive_server_data()
            if not data:
                return buffer
                
            buffer += data
            return self._process_message_buffer(buffer)
            
        except Exception as e:
            await self._handle_receive_error(e)
            return buffer
            
    async def _receive_server_data(self) -> str:
        try:
            data = await self.reader.read(NETWORK_BUFFER_SIZE)
            if not data:
                await self._handle_server_disconnection()
                return ""
                
            return data.decode(NETWORK_ENCODING)
            
        except (ConnectionResetError, ConnectionAbortedError):
            await self._handle_server_disconnection_error()
            return ""
            
    async def _handle_server_disconnection(self) -> None:
        self.connected = False
        if self.on_server_disconnect:
            self.on_server_disconnect()
            
    async def _handle_server_disconnection_error(self) -> None:
        self.connected = False
        if self.on_server_disconnect:
            self.on_server_disconnect()
            
    def _process_message_buffer(self, buffer: str) -> str:
        while JSON_MESSAGE_DELIMITER in buffer:
            message, buffer = buffer.split(JSON_MESSAGE_DELIMITER, MESSAGE_BUFFER_SPLIT_LIMIT)
            if message.strip():
                self._handle_complete_message(message)
        return buffer
        
    def _handle_complete_message(self, message: str) -> None:
        try:
            parsed_message = json.loads(message)
            self.handle_server_message(parsed_message)
        except json.JSONDecodeError as e:
            pass
            
    async def _handle_receive_error(self, error: Exception) -> None:
        self.connected = False
        if self.on_server_disconnect:
            self.on_server_disconnect()
    
    def handle_server_message(self, message: Dict[str, Any]) -> None:
        message_type = message.get('type')
        data = message.get('data', {})
        
        handler_map = {
            MESSAGE_TYPES['PLAYER_CONNECT']: self._handle_player_connect,
            MESSAGE_TYPES['PLAYERS_READY']: self._handle_players_ready,
            MESSAGE_TYPES['GAME_START']: self._handle_game_start,
            MESSAGE_TYPES['GAME_UPDATE']: self._handle_game_update,
            MESSAGE_TYPES['SHOT_RESULT']: self._handle_shot_result,
            MESSAGE_TYPES['GAME_OVER']: self._handle_game_over,
            MESSAGE_TYPES['PLAYER_DISCONNECT']: self._handle_player_disconnect,
            MESSAGE_TYPES['ERROR']: self._handle_error
        }
        
        handler = handler_map.get(message_type)
        if handler:
            handler(data)
        else:
            pass
            
    def _handle_player_connect(self, data: Dict[str, Any]) -> None:
        self.player_id = data.get('player_id')
        
    def _handle_players_ready(self, data: Dict[str, Any]) -> None:
        if self.on_players_ready:
            self.on_players_ready(data)
            
    def _handle_game_start(self, data: Dict[str, Any]) -> None:
        if self.on_game_start:
            self.on_game_start(data)
        else:
            pass
            
    def _handle_game_update(self, data: Dict[str, Any]) -> None:
        if self.on_game_update:
            self.on_game_update(data)
            
    def _handle_shot_result(self, data: Dict[str, Any]) -> None:
        if self.on_shot_result:
            self.on_shot_result(data)
            
    def _handle_game_over(self, data: Dict[str, Any]) -> None:
        if self.on_game_over:
            self.on_game_over(data)
            
    def _handle_player_disconnect(self, data: Dict[str, Any]) -> None:
        disconnected_player = data.get('disconnected_player', NETWORK_LOG_MESSAGES['UNKNOWN_PLAYER'])
        message = data.get('message', NETWORK_LOG_MESSAGES['DEFAULT_DISCONNECT_MESSAGE'])
        
        if self.on_server_disconnect:
            self.on_server_disconnect()
        else:
            pass
            
    def _handle_error(self, data: Dict[str, Any]) -> None:
        error_msg = data.get('error', NETWORK_LOG_MESSAGES['DEFAULT_ERROR_MESSAGE'])
    
    async def place_ships(self, ship_positions: Dict[str, Any]) -> bool:
        return await self.send_message(MESSAGE_TYPES['PLACE_SHIPS'], {'ships': ship_positions})
    
    async def make_shot(self, x: int, y: int) -> bool:
        return await self.send_message(MESSAGE_TYPES['SHOT'], {'x': x, 'y': y})
    
    async def make_bomb_attack(self, targets: list) -> bool:
        return await self.send_message(MESSAGE_TYPES['BOMB_ATTACK'], {'targets': targets})
    
    async def make_air_strike(self, targets: list) -> bool:
        return await self.send_message(MESSAGE_TYPES['AIR_STRIKE'], {'targets': targets})
    
    async def start_game(self) -> bool:
        if not self._validate_connection():
            return False
            
        result = await self.send_message(MESSAGE_TYPES['START_GAME'], {})
        return result
        
    def _log_start_game_info(self) -> None:
        pass
        
    def _validate_connection(self) -> bool:
        if not self.connected:
            return False
        return True
    
    def set_players_ready_callback(self, callback: Callable[[Dict[str, Any]], None]) -> None:
        self.on_players_ready = callback
    
    def set_game_start_callback(self, callback: Callable[[Dict[str, Any]], None]) -> None:
        self.on_game_start = callback
    
    def set_game_update_callback(self, callback: Callable[[Dict[str, Any]], None]) -> None:
        self.on_game_update = callback
    
    def set_shot_result_callback(self, callback: Callable[[Dict[str, Any]], None]) -> None:
        self.on_shot_result = callback
    
    def set_game_over_callback(self, callback: Callable[[Dict[str, Any]], None]) -> None:
        self.on_game_over = callback
    
    def set_server_disconnect_callback(self, callback: Callable[[], None]) -> None:
        self.on_server_disconnect = callback