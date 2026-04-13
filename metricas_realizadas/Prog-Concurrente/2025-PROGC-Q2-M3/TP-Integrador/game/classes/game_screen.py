import pygame
import os
import sys
import asyncio
from typing import Optional, Dict, Any, List, Tuple

sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from constants import (SHIP_SIZES, FONT_SIZE_NORMAL, COLOR_WHITE, GAME_TITLE_SPACE, GAME_BOARD_TITLE_SPACE,
                      GAME_INFO_SPACE, GAME_SCREEN_MARGIN, GAME_BOARD_SPACING, GAME_BOARD_SIZE_REDUCTION_FACTOR,
                      GAME_SCREEN_DIVISION_FACTOR, GAME_PHASE_PLACEMENT, GAME_PHASE_WAITING_BATTLE,
                      GAME_PHASE_BATTLE, DEFAULT_SHIP_SIZE, SHIP_HORIZONTAL_DEFAULT, INITIAL_SHIP_INDEX,
                      PANEL_PADDING, COORD_SPACE, TITLE_SPACING, GAME_PANEL_ALPHA, MY_PANEL_COLOR,
                      ENEMY_PANEL_COLOR, PANEL_BORDER_WIDTH, OCEAN_COLOR_TOP, OCEAN_COLOR_BOTTOM,
                      MOUSE_LEFT_BUTTON, MOUSE_RIGHT_BUTTON, KEY_ROTATE, GAME_TEXT,
                      SHIP_STATUS_COLORS, GAME_FONT_SIZES, SPECIAL_ATTACK_BUTTON_WIDTH, 
                      SPECIAL_ATTACK_BUTTON_HEIGHT, SPECIAL_ATTACK_BUTTON_MARGIN, 
                      SPECIAL_ATTACK_BUTTON_Y_OFFSET, COLOR_BUTTON_BOMB, COLOR_BUTTON_BOMB_HOVER,
                      COLOR_BUTTON_AIR_STRIKE, COLOR_BUTTON_AIR_STRIKE_HOVER, FONT_SIZE_SMALL,
                      MENU_BUTTON_BORDER_WIDTH, AVAILABLE_BOMBS, AVAILABLE_AIR_STRIKES,
                      BOMB_ATTACK_AREA_SIZE, BOMB_ATTACK_START_OFFSET, AIR_STRIKE_WIDTH, AIR_STRIKE_CENTER_OFFSET,
                      GRID_INIT, GRID_SIZE, BUTTON_SELECTED_EXPANSION, BUTTON_SELECTED_EXPANSION_TOTAL,
                      COLOR_BUTTON_SELECTED, COLOR_BUTTON_SELECTED_BORDER, BUTTON_SELECTED_BORDER_WIDTH,
                      COLOR_BUTTON_SELECTED_TEXT)
from .game_board import GameBoard

class GameScreen:
    def __init__(self, screen: pygame.Surface, network_manager: Optional[Any] = None, loop: Optional[asyncio.AbstractEventLoop] = None) -> None:
        self._initialize_screen_properties(screen, network_manager, loop)
        self._calculate_board_dimensions()
        self._setup_game_boards()
        self._initialize_game_state()
        self._initialize_ship_tracking()
        self._setup_fonts()
        
    def _initialize_screen_properties(self, screen: pygame.Surface, network_manager: Optional[Any], loop: Optional[asyncio.AbstractEventLoop]) -> None:
        self.screen = screen
        self.width = screen.get_width()
        self.height = screen.get_height()
        self.network_manager = network_manager
        self.loop = loop or asyncio.get_event_loop()
        
    def _calculate_board_dimensions(self) -> None:
        available_height = self.height - GAME_TITLE_SPACE - GAME_BOARD_TITLE_SPACE - GAME_INFO_SPACE
        available_width = self.width - GAME_SCREEN_MARGIN
        
        max_board_width = (available_width - GAME_BOARD_SPACING) // GAME_SCREEN_DIVISION_FACTOR
        max_board_height = available_height
        
        self.board_size = int(min(max_board_width, max_board_height) * GAME_BOARD_SIZE_REDUCTION_FACTOR)
        self.start_x = self._calculate_board_start_x()
        self.board_y = self._calculate_board_y()
        
    def _calculate_board_start_x(self) -> int:
        total_width = self.board_size * GAME_SCREEN_DIVISION_FACTOR + GAME_BOARD_SPACING
        return (self.width - total_width) // GAME_SCREEN_DIVISION_FACTOR
        
    def _calculate_board_y(self) -> int:
        total_board_area = self.board_size + GAME_BOARD_TITLE_SPACE
        available_vertical = self.height - GAME_TITLE_SPACE - GAME_INFO_SPACE
        return GAME_TITLE_SPACE + (available_vertical - total_board_area) // GAME_SCREEN_DIVISION_FACTOR + GAME_BOARD_TITLE_SPACE
        
    def _setup_game_boards(self) -> None:
        self.my_board = GameBoard(self.start_x, self.board_y, self.board_size)
        enemy_board_x = self.start_x + self.board_size + GAME_BOARD_SPACING
        self.enemy_board = GameBoard(enemy_board_x, self.board_y, self.board_size)
        
    def _initialize_game_state(self) -> None:
        self.game_phase = GAME_PHASE_PLACEMENT
        self.selected_ship_size = DEFAULT_SHIP_SIZE
        self.ship_horizontal = SHIP_HORIZONTAL_DEFAULT
        self.my_turn = False
        self.ships_to_place = SHIP_SIZES.copy()
        self.current_ship_index = INITIAL_SHIP_INDEX
        self.bomb_attack_mode = False
        self.air_strike_mode = False
        self.bombs_available = AVAILABLE_BOMBS
        self.air_strikes_available = AVAILABLE_AIR_STRIKES
        
    def _initialize_ship_tracking(self) -> None:
        self.enemy_sunk_ships: List[str] = []
        self.enemy_sunk_ships_info: Dict[Tuple[int, int], str] = {}
        
    def _setup_fonts(self) -> None:
        self.font = pygame.font.Font(None, FONT_SIZE_NORMAL)
        
    def _setup_special_attack_buttons(self) -> None:
        self._create_bomb_button()
        self._create_air_strike_button()
        
    def _create_bomb_button(self) -> None:
        button_x = self._calculate_bomb_button_x()
        button_y = self._calculate_special_buttons_y()
        
        self.bomb_button = {
            'rect': pygame.Rect(button_x, button_y, SPECIAL_ATTACK_BUTTON_WIDTH, SPECIAL_ATTACK_BUTTON_HEIGHT),
            'text': f"{GAME_TEXT['BOMB_BUTTON']} {self.bombs_available}/{AVAILABLE_BOMBS}",
            'color': COLOR_BUTTON_BOMB,
            'hover_color': COLOR_BUTTON_BOMB_HOVER,
            'text_color': COLOR_WHITE
        }
        
    def _create_air_strike_button(self) -> None:
        button_x = self._calculate_air_strike_button_x()
        button_y = self._calculate_special_buttons_y()
        
        self.air_strike_button = {
            'rect': pygame.Rect(button_x, button_y, SPECIAL_ATTACK_BUTTON_WIDTH, SPECIAL_ATTACK_BUTTON_HEIGHT),
            'text': f"{GAME_TEXT['AIR_STRIKE_BUTTON']} {self.air_strikes_available}/{AVAILABLE_AIR_STRIKES}",
            'color': COLOR_BUTTON_AIR_STRIKE,
            'hover_color': COLOR_BUTTON_AIR_STRIKE_HOVER,
            'text_color': COLOR_WHITE
        }
    
    def _update_attack_buttons_text(self) -> None:
        """Actualiza el texto de los botones con los contadores actuales de munición"""
        if hasattr(self, 'bomb_button'):
            self.bomb_button['text'] = f"{GAME_TEXT['BOMB_BUTTON']} {self.bombs_available}/{AVAILABLE_BOMBS}"
        if hasattr(self, 'air_strike_button'):
            self.air_strike_button['text'] = f"{GAME_TEXT['AIR_STRIKE_BUTTON']} {self.air_strikes_available}/{AVAILABLE_AIR_STRIKES}"
    
    def handle_event(self, event: pygame.event.Event) -> None:
        if event.type == pygame.MOUSEBUTTONDOWN:
            self._handle_mouse_event(event)
        elif event.type == pygame.KEYDOWN:
            self._handle_keyboard_event(event)
            
    def _handle_mouse_event(self, event: pygame.event.Event) -> None:
        if event.button == MOUSE_LEFT_BUTTON:
            self.handle_left_click(event.pos)
        elif event.button == MOUSE_RIGHT_BUTTON:
            self.handle_right_click(event.pos)
            
    def _handle_keyboard_event(self, event: pygame.event.Event) -> None:
        if event.key == KEY_ROTATE:
            self.ship_horizontal = not self.ship_horizontal
    
    def handle_left_click(self, mouse_pos: Tuple[int, int]) -> None:
        if self._is_placement_phase():
            self._handle_ship_placement(mouse_pos)
        elif self._is_battle_phase_my_turn():
            if self._check_special_attack_buttons(mouse_pos):
                return
            self._handle_battle_shot(mouse_pos)
            
    def _is_placement_phase(self) -> bool:
        return (self.game_phase == GAME_PHASE_PLACEMENT and 
               self.current_ship_index < len(self.ships_to_place))
               
    def _is_battle_phase_my_turn(self) -> bool:
        return self.game_phase == GAME_PHASE_BATTLE and self.my_turn
        
    def _check_special_attack_buttons(self, mouse_pos: Tuple[int, int]) -> bool:
        if hasattr(self, 'bomb_button') and self.bomb_button['rect'].collidepoint(mouse_pos) and self.bombs_available > 0:
            if self.bomb_attack_mode:
                self.bomb_attack_mode = False
            else:
                self.bomb_attack_mode = True
                self.air_strike_mode = False
            return True
        elif hasattr(self, 'air_strike_button') and self.air_strike_button['rect'].collidepoint(mouse_pos) and self.air_strikes_available > 0:
            if self.air_strike_mode:
                self.air_strike_mode = False
            else:
                self.air_strike_mode = True
                self.bomb_attack_mode = False
            return True
        return False
        
    def _handle_ship_placement(self, mouse_pos: Tuple[int, int]) -> None:
        cell = self.my_board.get_cell_from_mouse(mouse_pos)
        if cell:
            ship_size = self.ships_to_place[self.current_ship_index]
            if self.my_board.place_ship(ship_size, cell[0], cell[1], self.ship_horizontal):
                self._advance_ship_placement()
                
    def _advance_ship_placement(self) -> None:
        self.current_ship_index += 1
        if self.current_ship_index >= len(self.ships_to_place):
            self.game_phase = GAME_PHASE_WAITING_BATTLE
            self._setup_special_attack_buttons()
            self.send_ships_to_server()
            
    def _handle_battle_shot(self, mouse_pos: Tuple[int, int]) -> None:
            
            cell = self.enemy_board.get_cell_from_mouse(mouse_pos)
           
            if cell:
                if self.bomb_attack_mode:
                    self._execute_bomb_attack(cell[0], cell[1])
                elif self.air_strike_mode:
                    self._execute_air_strike(cell[0], cell[1])
                else:                  
                    self._execute_single_shot(cell[0], cell[1])

            
    def _can_shoot_at_cell(self, cell: Optional[Tuple[int, int]]) -> bool:
        return (cell is not None and 
               cell not in self.enemy_board.shots and 
               self.network_manager is not None)
               
    def _execute_bomb_attack(self, center_x: int, center_y: int) -> None:
        bomb_targets = self._generate_bomb_targets(center_x, center_y)

        if bomb_targets:
            self.loop.create_task(self._send_bomb_attack_async(bomb_targets))
            self.bomb_attack_mode = False
            self.bombs_available -= 1
            self._update_attack_buttons_text()
    
    def _execute_air_strike(self, center_x: int, center_y: int) -> None:
        air_strike_targets = self._generate_air_strike_targets(center_x, center_y)
        
        if air_strike_targets:
            self.loop.create_task(self._send_air_strike_async(air_strike_targets))
            self.air_strike_mode = False
            self.air_strikes_available -= 1
            self._update_attack_buttons_text()
    
    async def _send_bomb_attack_async(self, bomb_targets: List[Tuple[int, int]]) -> None:
        try:
            success = await self.network_manager.make_bomb_attack(bomb_targets)
            if not success:
                pass
        except Exception as e:
            pass
    
    async def _send_air_strike_async(self, air_strike_targets: List[Tuple[int, int]]) -> None:
        try:
            success = await self.network_manager.make_air_strike(air_strike_targets)
            if not success:
                pass
        except Exception as e:
            pass

            
    def _execute_single_shot(self, x: int, y: int) -> None:
        if self._can_shoot_at_cell((x, y)) and self.loop:
            self.loop.create_task(self.network_manager.make_shot(x, y))

            
    def _generate_bomb_targets(self, center_x: int, center_y: int) -> List[Tuple[int, int]]:
        targets = []
        
        for dx in range(BOMB_ATTACK_START_OFFSET, BOMB_ATTACK_AREA_SIZE):
            for dy in range(BOMB_ATTACK_START_OFFSET, BOMB_ATTACK_AREA_SIZE):
                target_x = center_x + dx
                target_y = center_y + dy
                
                if self._is_within_board_bounds(target_x, target_y):
                    targets.append((target_x, target_y))

        return targets
    
    def _generate_air_strike_targets(self, center_x: int, center_y: int) -> List[Tuple[int, int]]:
        targets = []
        start_x = center_x - AIR_STRIKE_CENTER_OFFSET
        for i in range(AIR_STRIKE_WIDTH):
            target_x = start_x + i
            target_y = center_y
            
            if self._is_within_board_bounds(target_x, target_y):
                targets.append((target_x, target_y))
        
        return targets
        
    def _is_within_board_bounds(self, x: int, y: int) -> bool:
        return GRID_INIT <= x < GRID_SIZE and GRID_INIT <= y < GRID_SIZE
    
    def handle_right_click(self, mouse_pos: Tuple[int, int]) -> None:
        self.ship_horizontal = not self.ship_horizontal
    
    def update(self) -> None:
        pass
    
    def draw(self) -> None:
        self.draw_ocean_background()
        self.draw_board_panels()
        self._draw_game_title()
        self._draw_board_titles()
        self._draw_game_boards()
        self._draw_ship_preview_if_needed()
        self._draw_game_info()
        
    def draw_without_preview(self) -> None:
        self.draw_ocean_background()
        self.draw_board_panels()
        self._draw_game_title()
        self._draw_board_titles()
        self._draw_game_boards()
        self._draw_game_info()
        
    def _draw_game_title(self) -> None:
        title_font = pygame.font.Font(None, GAME_FONT_SIZES['TITLE'])
        title_text = title_font.render(GAME_TEXT['TITLE'], True, COLOR_WHITE)
        title_rect = title_text.get_rect(center=(self.width // GAME_SCREEN_DIVISION_FACTOR, 35))
        self.screen.blit(title_text, title_rect)
        
    def _draw_board_titles(self) -> None:
        board_font = pygame.font.Font(None, GAME_FONT_SIZES['BOARD_TITLE'])
        title_y = self._calculate_board_title_y()
        
        self._draw_my_board_title(board_font, title_y)
        self._draw_enemy_board_title(board_font, title_y)
        
    def _calculate_board_title_y(self) -> int:
        panel_top = self.my_board.y - PANEL_PADDING - COORD_SPACE
        return panel_top - TITLE_SPACING
        
    def _draw_my_board_title(self, board_font: pygame.font.Font, title_y: int) -> None:
        my_title = board_font.render(GAME_TEXT['MY_FLEET'], True, SHIP_STATUS_COLORS['WHITE'])
        center_x = self.my_board.x + self.my_board.width // GAME_SCREEN_DIVISION_FACTOR
        my_title_rect = my_title.get_rect(center=(center_x, title_y))
        self.screen.blit(my_title, my_title_rect)
        
    def _draw_enemy_board_title(self, board_font: pygame.font.Font, title_y: int) -> None:
        enemy_title = board_font.render(GAME_TEXT['ENEMY'], True, SHIP_STATUS_COLORS['WHITE'])
        center_x = self.enemy_board.x + self.enemy_board.width // GAME_SCREEN_DIVISION_FACTOR
        enemy_title_rect = enemy_title.get_rect(center=(center_x, title_y))
        self.screen.blit(enemy_title, enemy_title_rect)
        
    def _draw_game_boards(self) -> None:
        self.my_board.draw(self.screen, show_ships=True)
        self.draw_enemy_board_with_sunk_ships()
        
        if self._should_show_ships_status():
            self.draw_ships_status()
            
    def _should_show_ships_status(self) -> bool:
        return (self.game_phase == GAME_PHASE_BATTLE or 
               self.game_phase == GAME_PHASE_WAITING_BATTLE)
               
    def _draw_ship_preview_if_needed(self) -> None:
        if self._should_show_ship_preview():
            mouse_pos = pygame.mouse.get_pos()
            cell = self.my_board.get_cell_from_mouse(mouse_pos)
            if cell:
                self.draw_ship_preview_realistic(cell[0], cell[1])
                
    def _should_show_ship_preview(self) -> bool:
        return (self.game_phase == GAME_PHASE_PLACEMENT and 
               self.current_ship_index < len(self.ships_to_place))
        
    def _draw_game_info(self) -> None:
        self.draw_info_panel()
        self._draw_status_text()
        self._draw_additional_info()
        self._draw_special_attack_buttons()
        
    def _draw_status_text(self) -> None:
        status_text = self._get_status_text()
        main_info_font = pygame.font.Font(None, GAME_FONT_SIZES['MAIN_INFO'])
        
        status_surface = main_info_font.render(status_text, True, SHIP_STATUS_COLORS['WHITE'])
        center_x = self.width // GAME_SCREEN_DIVISION_FACTOR
        status_rect = status_surface.get_rect(center=(center_x, self.height - 75))
        self.screen.blit(status_surface, status_rect)
        
    def _get_status_text(self) -> str:
        if self.game_phase == GAME_PHASE_PLACEMENT:
            return self._get_placement_status_text()
        elif self.game_phase == GAME_PHASE_BATTLE:
            return self._get_battle_status_text()
        elif self.game_phase == GAME_PHASE_WAITING_BATTLE:
            return GAME_TEXT['WAITING_BATTLE']
        else:
            return GAME_TEXT['PREPARING']
            
    def _get_placement_status_text(self) -> str:
        if self.current_ship_index < len(self.ships_to_place):
            ship_size = self.ships_to_place[self.current_ship_index]
            orientation = GAME_TEXT['HORIZONTAL'] if self.ship_horizontal else GAME_TEXT['VERTICAL']
            return GAME_TEXT['PLACING_SHIP'].format(ship_size, orientation)
        else:
            return GAME_TEXT['ALL_SHIPS_PLACED']
            
    def _get_battle_status_text(self) -> str:
        return GAME_TEXT['YOUR_TURN'] if self.my_turn else GAME_TEXT['OPPONENT_TURN']
            
    def _draw_additional_info(self) -> None:
        if self.game_phase == GAME_PHASE_PLACEMENT:
            remaining_ships = self.ships_to_place[self.current_ship_index:]
            if remaining_ships:
                self._draw_remaining_ships_info(remaining_ships)
                
    def _draw_remaining_ships_info(self, remaining_ships: List[int]) -> None:
        ships_text = GAME_TEXT['REMAINING_SHIPS'].format(remaining_ships)
        secondary_info_font = pygame.font.Font(None, GAME_FONT_SIZES['SECONDARY_INFO'])
        
        ships_surface = secondary_info_font.render(ships_text, True, SHIP_STATUS_COLORS['LIGHT_BLUE'])
        center_x = self.width // GAME_SCREEN_DIVISION_FACTOR
        ships_rect = ships_surface.get_rect(center=(center_x, self.height - 45))
        self.screen.blit(ships_surface, ships_rect)
        
    def _calculate_bomb_button_x(self) -> int:
        my_board_center = self.start_x + self.board_size // GAME_SCREEN_DIVISION_FACTOR
        return my_board_center - SPECIAL_ATTACK_BUTTON_WIDTH - SPECIAL_ATTACK_BUTTON_MARGIN // GAME_SCREEN_DIVISION_FACTOR
        
    def _calculate_air_strike_button_x(self) -> int:
        my_board_center = self.start_x + self.board_size // GAME_SCREEN_DIVISION_FACTOR
        return my_board_center + SPECIAL_ATTACK_BUTTON_MARGIN // GAME_SCREEN_DIVISION_FACTOR
        
    def _calculate_special_buttons_y(self) -> int:
        return self.board_y + self.board_size + SPECIAL_ATTACK_BUTTON_Y_OFFSET
        
    def _draw_special_attack_buttons(self) -> None:
        if (self.game_phase == GAME_PHASE_WAITING_BATTLE or 
            self.game_phase == GAME_PHASE_BATTLE):
            
            mouse_pos = pygame.mouse.get_pos()
            button_font = pygame.font.Font(None, FONT_SIZE_SMALL)

            bomb_color = COLOR_BUTTON_BOMB if self.bombs_available > 0 else (128, 128, 128)
            bomb_hover_color = COLOR_BUTTON_BOMB_HOVER if self.bombs_available > 0 else (128, 128, 128)
            
            air_strike_color = COLOR_BUTTON_AIR_STRIKE if self.air_strikes_available > 0 else (128, 128, 128)
            air_strike_hover_color = COLOR_BUTTON_AIR_STRIKE_HOVER if self.air_strikes_available > 0 else (128, 128, 128)
            
            self._draw_attack_button(self.bomb_button, bomb_color, bomb_hover_color, mouse_pos, button_font)
            self._draw_attack_button(self.air_strike_button, air_strike_color, air_strike_hover_color, mouse_pos, button_font)
    
    def _is_button_attack_selected(self, button: Dict[str, Any]) -> bool:
        is_bomb_button = GAME_TEXT['BOMB_BUTTON'] in button['text']
        is_air_strike_button = GAME_TEXT['AIR_STRIKE_BUTTON'] in button['text']
        return (is_bomb_button and self.bomb_attack_mode) or (is_air_strike_button and self.air_strike_mode)
    
    def _get_button_style(self, button: Dict[str, Any], is_selected: bool, normal_color: Tuple[int, int, int],
                         hover_color: Tuple[int, int, int], mouse_pos: Tuple[int, int]) -> Tuple[pygame.Rect, Tuple[int, int, int], Tuple[int, int, int], int]:
        if is_selected:
            expanded_rect = pygame.Rect(
                button['rect'].x - BUTTON_SELECTED_EXPANSION, 
                button['rect'].y - BUTTON_SELECTED_EXPANSION,
                button['rect'].width + BUTTON_SELECTED_EXPANSION_TOTAL, 
                button['rect'].height + BUTTON_SELECTED_EXPANSION_TOTAL
            )
            return expanded_rect, COLOR_BUTTON_SELECTED, COLOR_BUTTON_SELECTED_BORDER, BUTTON_SELECTED_BORDER_WIDTH
        else:
            button_color = hover_color if button['rect'].collidepoint(mouse_pos) else normal_color
            return button['rect'], button_color, COLOR_WHITE, MENU_BUTTON_BORDER_WIDTH
    
    def _render_button_graphics(self, rect: pygame.Rect, button_color: Tuple[int, int, int],
                               border_color: Tuple[int, int, int], border_width: int) -> None:
        pygame.draw.rect(self.screen, button_color, rect)
        pygame.draw.rect(self.screen, border_color, rect, border_width)
    
    def _render_button_text(self, button: Dict[str, Any], button_font: pygame.font.Font,
                           rect: pygame.Rect, is_selected: bool) -> None:
        text_color = COLOR_BUTTON_SELECTED_TEXT if is_selected else COLOR_WHITE
        button_text = button_font.render(button['text'], True, text_color)
        button_text_rect = button_text.get_rect(center=rect.center)
        self.screen.blit(button_text, button_text_rect)
            
    def _draw_attack_button(self, button: Dict[str, Any], normal_color: Tuple[int, int, int], 
                           hover_color: Tuple[int, int, int], mouse_pos: Tuple[int, int], 
                           button_font: pygame.font.Font) -> None:
        
        is_selected = self._is_button_attack_selected(button)
        expanded_rect, button_color, border_color, border_width = self._get_button_style(
            button, is_selected, normal_color, hover_color, mouse_pos
        )
        
        self._render_button_graphics(expanded_rect, button_color, border_color, border_width)
        self._render_button_text(button, button_font, expanded_rect, is_selected)

    
    def draw_ocean_background(self) -> None:
        for y in range(self.height):
            ratio = y / self.height
            color = self._calculate_ocean_color(ratio)
            pygame.draw.line(self.screen, color, (0, y), (self.width, y))
            
    def _calculate_ocean_color(self, ratio: float) -> Tuple[int, int, int]:
        r = int(OCEAN_COLOR_TOP['r'] + (OCEAN_COLOR_BOTTOM['r'] - OCEAN_COLOR_TOP['r']) * ratio)
        g = int(OCEAN_COLOR_TOP['g'] + (OCEAN_COLOR_BOTTOM['g'] - OCEAN_COLOR_TOP['g']) * ratio)
        b = int(OCEAN_COLOR_TOP['b'] + (OCEAN_COLOR_BOTTOM['b'] - OCEAN_COLOR_TOP['b']) * ratio)
        return (r, g, b)
    
    def draw_board_panels(self) -> None:
        self._draw_my_board_panel()
        self._draw_enemy_board_panel()
        self._draw_panel_borders()
        
    def _draw_my_board_panel(self) -> None:
        my_panel_rect = self._calculate_my_panel_rect()
        my_panel_surface = self._create_panel_surface(my_panel_rect, MY_PANEL_COLOR)
        self.screen.blit(my_panel_surface, (my_panel_rect.x, my_panel_rect.y))
        
    def _draw_enemy_board_panel(self) -> None:
        enemy_panel_rect = self._calculate_enemy_panel_rect()
        enemy_panel_surface = self._create_panel_surface(enemy_panel_rect, ENEMY_PANEL_COLOR)
        self.screen.blit(enemy_panel_surface, (enemy_panel_rect.x, enemy_panel_rect.y))
        
    def _calculate_my_panel_rect(self) -> pygame.Rect:
        return pygame.Rect(
            self.my_board.x - PANEL_PADDING - COORD_SPACE,
            self.my_board.y - PANEL_PADDING - COORD_SPACE,
            self.my_board.width + PANEL_PADDING * 2 + COORD_SPACE * 2,
            self.my_board.height + PANEL_PADDING * 2 + COORD_SPACE * 2
        )
        
    def _calculate_enemy_panel_rect(self) -> pygame.Rect:
        return pygame.Rect(
            self.enemy_board.x - PANEL_PADDING - COORD_SPACE,
            self.enemy_board.y - PANEL_PADDING - COORD_SPACE,
            self.enemy_board.width + PANEL_PADDING * 2 + COORD_SPACE * 2,
            self.enemy_board.height + PANEL_PADDING * 2 + COORD_SPACE * 2
        )
        
    def _create_panel_surface(self, panel_rect: pygame.Rect, color: Tuple[int, int, int]) -> pygame.Surface:
        panel_surface = pygame.Surface((panel_rect.width, panel_rect.height))
        panel_surface.set_alpha(GAME_PANEL_ALPHA)
        panel_surface.fill(color)
        return panel_surface
        
    def _draw_panel_borders(self) -> None:
        my_panel_rect = self._calculate_my_panel_rect()
        enemy_panel_rect = self._calculate_enemy_panel_rect()
        
        pygame.draw.rect(self.screen, (100, 149, 237), my_panel_rect, PANEL_BORDER_WIDTH)
        pygame.draw.rect(self.screen, (220, 20, 60), enemy_panel_rect, PANEL_BORDER_WIDTH)
    
    def draw_info_panel(self):
        panel_height = 110
        panel_y = self.height - panel_height - 15
        
        info_panel = pygame.Rect(60, panel_y, self.width - 120, panel_height)
        info_surface = pygame.Surface((info_panel.width, info_panel.height))
        info_surface.set_alpha(130)
        info_surface.fill((25, 45, 85))
        
        self.screen.blit(info_surface, (info_panel.x, info_panel.y))
        pygame.draw.rect(self.screen, (120, 160, 255), info_panel, 3)
    
    def draw_ships_status(self):
        ship_font = pygame.font.Font(None, 24)
        title_font = pygame.font.Font(None, 28)
        
        left_panel_x = 10
        left_panel_y = self.my_board.y + 50
        left_panel_width = 200
        left_panel_height = 300
        
        right_panel_x = self.width - 210
        right_panel_y = self.enemy_board.y + 50
        right_panel_width = 200
        right_panel_height = 300
        
        left_panel = pygame.Rect(left_panel_x, left_panel_y, left_panel_width, left_panel_height)
        left_surface = pygame.Surface((left_panel_width, left_panel_height))
        left_surface.set_alpha(180)
        left_surface.fill((20, 60, 40))
        self.screen.blit(left_surface, (left_panel_x, left_panel_y))
        pygame.draw.rect(self.screen, (100, 200, 120), left_panel, 2)
        
        my_title = title_font.render("MIS BARCOS", True, (255, 255, 255))
        self.screen.blit(my_title, (left_panel_x + 10, left_panel_y + 10))
        
        my_ships = self.my_board.get_ships_status()
        y_offset = 40
        for ship in my_ships:
            if ship['sunk']:
                color = (255, 100, 100)
                status = "HUNDIDO"
            else:
                color = (100, 255, 100)
                status = f"{ship['hits']}/{ship['total_hits_needed']} impactos"
            
            ship_text = ship_font.render(f"• {ship['name']}", True, color)
            status_text = ship_font.render(f"  {status}", True, (200, 200, 200))
            
            self.screen.blit(ship_text, (left_panel_x + 10, left_panel_y + y_offset))
            self.screen.blit(status_text, (left_panel_x + 15, left_panel_y + y_offset + 18))
            y_offset += 45
        
        right_panel = pygame.Rect(right_panel_x, right_panel_y, right_panel_width, right_panel_height)
        right_surface = pygame.Surface((right_panel_width, right_panel_height))
        right_surface.set_alpha(180)
        right_surface.fill((60, 20, 20))
        self.screen.blit(right_surface, (right_panel_x, right_panel_y))
        pygame.draw.rect(self.screen, (200, 100, 100), right_panel, 2)
        
        enemy_title = title_font.render("BARCOS ENEMIGOS", True, (255, 255, 255))
        self.screen.blit(enemy_title, (right_panel_x + 10, right_panel_y + 10))
        
        enemy_ships = self.get_enemy_ships_status()
        y_offset = 40
        for ship in enemy_ships:
            if ship['sunk']:
                color = (255, 100, 100)
                status = "HUNDIDO"
            else:
                color = (255, 200, 100)
                status = "ACTIVO"
            
            ship_text = ship_font.render(f" {ship['name']}", True, color)
            status_text = ship_font.render(f"  {status}", True, (200, 200, 200))
            
            self.screen.blit(ship_text, (right_panel_x + 10, right_panel_y + y_offset))
            self.screen.blit(status_text, (right_panel_x + 15, right_panel_y + y_offset + 18))
            y_offset += 45
    
    def get_enemy_ships_status(self):
        expected_ships = [
            {"name": "Portaaviones", "size": 5, "sunk": False},
            {"name": "Destructor Acorazado", "size": 4, "sunk": False},
            {"name": "Barco de Ataque #1", "size": 3, "sunk": False},
            {"name": "Barco de Ataque #2", "size": 3, "sunk": False},
            {"name": "Lancha Rapida", "size": 2, "sunk": False}
        ]
        
        for sunk_ship_name in self.enemy_sunk_ships:
            for ship in expected_ships:
                if ship['name'] == sunk_ship_name or (sunk_ship_name == "Barco de Ataque" and "Barco de Ataque" in ship['name'] and not ship['sunk']):
                    ship['sunk'] = True
                    break
        
        return expected_ships
    
    def draw_ship_preview_realistic(self, x, y):
        from .ship import Ship
        
        ship_size = self.ships_to_place[self.current_ship_index]
        
        can_place = self.my_board.can_place_ship(ship_size, x, y, self.ship_horizontal)
        
        temp_ship = Ship(ship_size)
        temp_ship.horizontal = self.ship_horizontal
        
        for i in range(ship_size):
            if self.ship_horizontal:
                temp_ship.positions.append((x + i, y))
            else:
                temp_ship.positions.append((x, y + i))
        
        preview_surface = pygame.Surface((self.screen.get_width(), self.screen.get_height()))
        preview_surface.set_alpha(60)
        preview_surface.fill((0, 0, 0, 0))
        
        temp_board = GameBoard(self.my_board.x, self.my_board.y, self.my_board.width)
        temp_board.ships = [temp_ship]
        temp_board.colors = self.my_board.colors.copy()
        
        if can_place:
            temp_board.colors['ship'] = (0, 255, 0)
        else:
            temp_board.colors['ship'] = (255, 50, 50)
        
        temp_board.draw_realistic_ship(preview_surface, temp_ship)
        
        self.screen.blit(preview_surface, (0, 0))
    
    def send_ships_to_server(self):
        if self.network_manager:
            ships_data = []
            for ship in self.my_board.ships:
                ships_data.append(ship.positions)
            
            if self.loop:
                self.loop.create_task(self.network_manager.place_ships(ships_data))
    
    def handle_shot_result(self, data):
        if not data:
            return
            
        x, y, result, shooter, ship_info = self._extract_shot_data(data)
        if not self._validate_shot_data(x, y, result, shooter):
            return
        
        self._play_shot_sound(result)
        
        if shooter == self.network_manager.player_id:
            self._handle_my_shot_result(x, y, result, ship_info)
        else:
            self._handle_opponent_shot_result(x, y, result)
    
    def _extract_shot_data(self, data):
        return (
            data.get('x'), 
            data.get('y'),
            data.get('result'),
            data.get('shooter'),
            data.get('ship_info')
        )
    
    def _validate_shot_data(self, x, y, result, shooter):
        return x is not None and y is not None and result and shooter
    
    def _play_shot_sound(self, result):
        if result == 'hit' or result == 'sunk':
            self.play_missile_sound()
        elif result == 'miss':
            self.play_water_splash_sound()
    
    def _handle_my_shot_result(self, x, y, result, ship_info):
        self.enemy_board.shots[(x, y)] = result
        
        if result == 'sunk' and ship_info:
            self._process_enemy_ship_sunk(ship_info)
    
    def _process_enemy_ship_sunk(self, ship_info):
        ship_name = ship_info.get('name')
        ship_positions = ship_info.get('positions', [])
        
        if ship_name:
            self.enemy_sunk_ships.append(ship_name)
            
            for pos in ship_positions:
                self.enemy_sunk_ships_info[tuple(pos)] = ship_name
                
            self.enemy_board.mark_enemy_ship_sunk(ship_info, ship_positions)
    
    def _handle_opponent_shot_result(self, x, y, result):
        self.my_board.shots[(x, y)] = result
        
        if result == 'hit' or result == 'sunk':
            self._update_my_ship_hit(x, y, result)
    
    def _update_my_ship_hit(self, x, y, result):
        for ship in self.my_board.ships:
            if (x, y) in ship.positions:
                ship.hit(x, y)
                break
    
    def set_my_turn(self, is_my_turn):
        try:
            self.my_turn = bool(is_my_turn)
        except Exception:
            self.my_turn = False
    
    def start_battle_phase(self):
        self.game_phase = "battle"
    

    
    def draw_enemy_board_with_sunk_ships(self):
        self.enemy_board.draw(self.screen, show_ships=False)
    
    def play_missile_sound(self):
        try:
            missile_sound_path = os.path.join("assets", "sounds", "misil.mp3")
            if os.path.exists(missile_sound_path):
                missile_sound = pygame.mixer.Sound(missile_sound_path)
                missile_sound.set_volume(0.3)
                missile_sound.play()
        except (pygame.error, FileNotFoundError, Exception):
            pass
    
    def play_water_splash_sound(self):
        try:
            splash_sound_path = os.path.join("assets", "sounds", "waterSplash.mp3")
            if os.path.exists(splash_sound_path):
                splash_sound = pygame.mixer.Sound(splash_sound_path)
                splash_sound.set_volume(0.25)
                splash_sound.play()
        except (pygame.error, FileNotFoundError, Exception):
            pass

    def reset_game_state(self):
        self.my_board = GameBoard(self.my_board.x, self.my_board.y, self.my_board.width)
        self.enemy_board = GameBoard(self.enemy_board.x, self.enemy_board.y, self.enemy_board.width)

        self.game_phase = "placement"
        self.selected_ship_size = 2
        self.ship_horizontal = True
        self.my_turn = False
        self.ships_to_place = [5, 4, 3, 3, 2]
        self.current_ship_index = 0
        self.bombs_available = AVAILABLE_BOMBS
        self.air_strikes_available = AVAILABLE_AIR_STRIKES
        self._update_attack_buttons_text()

        self.enemy_sunk_ships = []
        self.enemy_sunk_ships_info = {}