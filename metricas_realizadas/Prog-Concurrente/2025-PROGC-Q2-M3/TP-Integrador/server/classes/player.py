import asyncio
import json
import sys
import os
from typing import List, Dict, Optional, Any

sys.path.append(os.path.join(os.path.dirname(__file__), '..', '..', 'game', 'classes'))
from ship import Ship

sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from constants import *
from classes.enums import MessageType

class Player:
    
    def __init__(self, player_id: str, writer: asyncio.StreamWriter):
        self.player_id = player_id
        self.writer = writer
        self.ships_placed = False
        self.grid = self._initialize_grid()
        self.ships = []
        
    def _initialize_grid(self) -> List[List[int]]:
        return [[CELL_EMPTY for _ in range(GRID_SIZE)] for _ in range(GRID_SIZE)]
    
    async def send_message(self, message_type: MessageType, data: Optional[Any] = None) -> bool:
        try:
            message = self._create_message(message_type, data)
            await self._send_raw_message(message)
            return True
        except (ConnectionResetError, BrokenPipeError):
            return False
        except Exception:
            return False
            
    def _create_message(self, message_type: MessageType, data: Optional[Any]) -> str:
        message = {
            'type': message_type.value,
            'data': data
        }
        return json.dumps(message) + JSON_MESSAGE_DELIMITER
        
    async def _send_raw_message(self, message: str) -> None:
        self.writer.write(message.encode(UTF8_ENCODING))
        await self.writer.drain()
        
    def place_ship(self, positions: List[tuple]) -> None:
        valid_positions = self._validate_ship_positions(positions)
        
        if valid_positions:
            self._create_and_add_ship(valid_positions)
             
    def _validate_ship_positions(self, positions: List[tuple]) -> List[tuple]:
        valid_positions = []
        for x, y in positions:
            if self._is_valid_position(x, y):
                self.grid[y][x] = CELL_SHIP
                valid_positions.append((x, y))
            
        return valid_positions
        
    def _is_valid_position(self, x: int, y: int) -> bool:
        return MIN_COORDINATE <= x < GRID_SIZE and MIN_COORDINATE <= y < GRID_SIZE
        
    def _create_and_add_ship(self, positions: List[tuple]) -> None:
        ship = Ship(positions=positions)
        self.ships.append(ship)
        
    def find_ship_containing(self, x: int, y: int) -> Optional[Ship]:
        for ship in self.ships:
            if ship.contains_position(x, y):
                return ship
        return None
    
    def receive_shot(self, x: int, y: int) -> Dict[str, Any]:
        if not self._is_valid_position(x, y):
            return {'result': SHOT_RESULT_MISS}
            
        current_cell = self.grid[y][x]
        
        if current_cell == CELL_EMPTY:
            return self._process_water_hit(x, y)
        elif current_cell == CELL_SHIP:
            return self._process_ship_hit(x, y)
        else:
            return self._process_already_hit(x, y)
            
    def _process_water_hit(self, x: int, y: int) -> Dict[str, str]:
        self.grid[y][x] = CELL_WATER_HIT
        return {'result': SHOT_RESULT_MISS}
        
    def _process_ship_hit(self, x: int, y: int) -> Dict[str, Any]:
        self.grid[y][x] = CELL_HIT
        ship = self.find_ship_containing(x, y)
        
        if ship is None:
            return {'result': SHOT_RESULT_HIT}
            
        ship.hit(x, y)
        
        if ship.is_sunk():
            return self._create_sunk_ship_result(ship)
        else:
            return {'result': SHOT_RESULT_HIT}
            
    def _create_sunk_ship_result(self, ship: Ship) -> Dict[str, Any]:
        return {
            'result': SHOT_RESULT_SUNK,
            'ship_info': {
                'name': ship.ship_type,
                'size': ship.size,
                'positions': list(ship.positions)
            }
        }
        
    def _process_already_hit(self, x: int, y: int) -> Dict[str, Any]:
        current_cell = self.grid[y][x]
        
        if current_cell == CELL_HIT:
            ship = self.find_ship_containing(x, y)
            if ship and ship.is_sunk():
                return self._create_sunk_ship_result(ship)
            else:
                return {'result': SHOT_RESULT_HIT}
        elif current_cell == CELL_WATER_HIT:
            return {'result': SHOT_RESULT_MISS}
        else:
            return {'result': SHOT_RESULT_MISS}
    
    def all_ships_sunk(self) -> bool:
        if not self.ships:
            return False
        return all(ship.is_sunk() for ship in self.ships)