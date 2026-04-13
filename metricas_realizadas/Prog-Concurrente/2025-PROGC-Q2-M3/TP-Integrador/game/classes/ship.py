import sys
import os
from typing import List, Tuple, Set, Optional

sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from constants import SHIP_NAMES, ERROR_MESSAGES_SHIP, SHIP_ORIENTATION_HORIZONTAL

class Ship:
    def __init__(self, size: Optional[int] = None, ship_type: Optional[str] = None, 
                 positions: Optional[List[Tuple[int, int]]] = None):
        self._initialize_common_attributes()
        
        if positions is not None:
            self._initialize_from_positions(positions, ship_type)
        elif size is not None:
            self._initialize_from_size(size, ship_type)
        else:
            raise ValueError(ERROR_MESSAGES_SHIP['MISSING_INIT_PARAMS'])
            
    def _initialize_common_attributes(self) -> None:
        self.hits = set()
        self.sunk = False
        self.horizontal = SHIP_ORIENTATION_HORIZONTAL
        
    def _initialize_from_positions(self, positions: List[Tuple[int, int]], ship_type: Optional[str]) -> None:
        if not positions:
            raise ValueError(ERROR_MESSAGES_SHIP['EMPTY_POSITIONS'])
            
        self.positions = list(positions)
        self.size = len(positions)
        self.ship_type = ship_type or self.get_ship_name_by_size(self.size)
        self.name = self.ship_type
        
    def _initialize_from_size(self, size: int, ship_type: Optional[str]) -> None:
        self.size = size
        self.positions = []
        self.ship_type = ship_type or self.get_ship_name_by_size(size)
        self.name = self.ship_type
    def get_ship_name_by_size(self, size: int) -> str:
        return SHIP_NAMES.get(size, f"Barco de {size} casillas")
    
    def contains_position(self, x: int, y: int) -> bool:
        return (x, y) in self.positions
    
    def hit(self, x: int, y: int) -> bool:
        if not self.contains_position(x, y):
            return False
            
        self._register_hit(x, y)
        self._update_sunk_status()
        return True
        
    def _register_hit(self, x: int, y: int) -> None:
        self.hits.add((x, y))
        
    def _update_sunk_status(self) -> None:
        if self.is_sunk():
            self.sunk = True
    def is_sunk(self) -> bool:
        return bool(self.positions) and len(self.hits) >= len(self.positions)
    
    def get_remaining_positions(self) -> Set[Tuple[int, int]]:
        return set(self.positions) - self.hits
    
    def get_hit_positions(self) -> Set[Tuple[int, int]]:
        return self.hits.copy()
    
    def set_positions(self, positions: List[Tuple[int, int]]) -> None:
        self.positions = positions
        
    def add_position(self, x: int, y: int) -> None:
        if (x, y) not in self.positions:
            self.positions.append((x, y))
    
    def set_horizontal(self, horizontal: bool) -> None:
        self.horizontal = horizontal
        
    def __str__(self) -> str:
        status = self._get_ship_status_string()
        return f"{self.ship_type} [{status}]"
        
    def _get_ship_status_string(self) -> str:
        if self.sunk or self.is_sunk():
            return "Hundido"
        return f"{len(self.hits)}/{len(self.positions)} golpeado"
    
    def __repr__(self) -> str:
        return (f"Ship(size={self.size}, type={self.ship_type}, "
                f"positions={self.positions}, hits={list(self.hits)})")