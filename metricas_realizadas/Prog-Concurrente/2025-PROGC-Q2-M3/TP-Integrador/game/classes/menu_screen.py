import pygame
import os
import sys
from typing import Optional, Tuple, Dict, Any

sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from constants import (COLOR_WHITE, MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT, MENU_BUTTON_Y_CONNECT,
                      MENU_BUTTON_Y_START, COLOR_BUTTON_CONNECT, COLOR_BUTTON_CONNECT_HOVER,
                      COLOR_BUTTON_START, COLOR_BUTTON_START_HOVER, COLOR_BUTTON_DISABLED,
                      MUTE_BUTTON_WIDTH, MUTE_BUTTON_HEIGHT, MUTE_BUTTON_MARGIN, COLOR_BUTTON_MUTE,
                      COLOR_BUTTON_MUTE_HOVER, FONT_SIZE_NORMAL, FONT_SIZE_SMALL, COLOR_BUTTON_CONNECT_ACTIVE,
                      COLOR_GREEN, COLOR_YELLOW, MENU_STATUS_Y, MUTED_VOLUME, MUSIC_VOLUME_MENU,
                      MENU_BUTTON_DIVISION_FACTOR, MENU_BUTTON_BORDER_WIDTH, MUTE_BUTTON_BORDER_WIDTH,
                      MENU_FONT_SIZE_DEFAULT, MOUSE_BUTTON_LEFT, MENU_STATE_DISCONNECTED,
                      MENU_STATE_PLAYERS_NOT_READY, MENU_TEXT, MENU_STATUS_COLOR_DISCONNECTED,
                      MENU_BACKGROUND_COLOR_DEFAULT, MENU_EVENTS)

class MenuScreen:
    def __init__(self, screen: pygame.Surface) -> None:
        self._initialize_screen_properties(screen)
        self.load_assets()
        self._initialize_menu_states()
        self.setup_buttons()
        
    def _initialize_screen_properties(self, screen: pygame.Surface) -> None:
        self.screen = screen
        self.width = screen.get_width()
        self.height = screen.get_height()
        
    def _initialize_menu_states(self) -> None:
        self.server_connected = MENU_STATE_DISCONNECTED
        self.players_ready = MENU_STATE_PLAYERS_NOT_READY
        self.music_muted = MENU_STATE_DISCONNECTED
        
    def load_assets(self) -> None:
        try:
            self._load_menu_background()
        except pygame.error:
            self._handle_asset_loading_error()
            
    def _load_menu_background(self) -> None:
        menu_path = os.path.join("assets", "images", "menu.png")
        self.menu_image = pygame.image.load(menu_path)
        self.menu_image = pygame.transform.scale(self.menu_image, (self.width, self.height))
        
    def _handle_asset_loading_error(self) -> None:
        self.menu_image = None
    
    def setup_buttons(self) -> None:
        self._setup_main_buttons()
        self._setup_mute_button()
        self._setup_button_list_and_fonts()
        
    def _setup_main_buttons(self) -> None:
        center_x = self.width // MENU_BUTTON_DIVISION_FACTOR
        self.connect_button = self._create_connect_button(center_x)
        self.start_button = self._create_start_button(center_x)
        
    def _create_connect_button(self, center_x: int) -> Dict[str, Any]:
        return {
            'rect': pygame.Rect(center_x - MENU_BUTTON_WIDTH // MENU_BUTTON_DIVISION_FACTOR, 
                              MENU_BUTTON_Y_CONNECT, MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT),
            'text': MENU_TEXT['CONNECT_DEFAULT'],
            'enabled': True,
            'color': COLOR_BUTTON_CONNECT,
            'hover_color': COLOR_BUTTON_CONNECT_HOVER,
            'text_color': COLOR_WHITE
        }
        
    def _create_start_button(self, center_x: int) -> Dict[str, Any]:
        return {
            'rect': pygame.Rect(center_x - MENU_BUTTON_WIDTH // MENU_BUTTON_DIVISION_FACTOR, 
                              MENU_BUTTON_Y_START, MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT),
            'text': MENU_TEXT['START_GAME'],
            'enabled': False,
            'color': COLOR_BUTTON_START,
            'hover_color': COLOR_BUTTON_START_HOVER,
            'disabled_color': COLOR_BUTTON_DISABLED,
            'text_color': COLOR_WHITE
        }
        
    def _setup_mute_button(self) -> None:
        self.mute_button = {
            'rect': pygame.Rect(self.width - MUTE_BUTTON_WIDTH - MUTE_BUTTON_MARGIN, 
                              MUTE_BUTTON_MARGIN, MUTE_BUTTON_WIDTH, MUTE_BUTTON_HEIGHT),
            'text': MENU_TEXT['MUTE_MUSIC'],
            'text_muted': MENU_TEXT['UNMUTE_MUSIC'],
            'color': COLOR_BUTTON_MUTE,
            'hover_color': COLOR_BUTTON_MUTE_HOVER,
            'text_color': COLOR_WHITE
        }
        
    def _setup_button_list_and_fonts(self) -> None:
        self.buttons = [self.connect_button, self.start_button]
        self.font = pygame.font.Font(MENU_FONT_SIZE_DEFAULT, FONT_SIZE_NORMAL)
        self.mute_font = pygame.font.Font(MENU_FONT_SIZE_DEFAULT, FONT_SIZE_SMALL)
        
    def handle_event(self, event: pygame.event.Event) -> Optional[str]:
        if self._is_left_mouse_click(event):
            return self._handle_mouse_click()
        return None
        
    def _is_left_mouse_click(self, event: pygame.event.Event) -> bool:
        return event.type == pygame.MOUSEBUTTONDOWN and event.button == MOUSE_BUTTON_LEFT
        
    def _handle_mouse_click(self) -> Optional[str]:
        mouse_pos = pygame.mouse.get_pos()
        
        if self._is_button_clicked(self.connect_button, mouse_pos):
            return MENU_EVENTS['CONNECT']
            
        if self._is_button_clicked(self.start_button, mouse_pos):
            return MENU_EVENTS['START_GAME']
            
        if self.mute_button['rect'].collidepoint(mouse_pos):
            return MENU_EVENTS['TOGGLE_MUSIC']
            
        return None
        
    def _is_button_clicked(self, button: Dict[str, Any], mouse_pos: Tuple[int, int]) -> bool:
        return button['rect'].collidepoint(mouse_pos) and button['enabled']
    
    def update(self) -> None:
        pass
    
    def draw_background(self) -> None:
        if self.menu_image:
            self.screen.blit(self.menu_image, (0, 0))
        else:
            self.screen.fill(MENU_BACKGROUND_COLOR_DEFAULT)

    def draw_button(self, button: Dict[str, Any], color: Tuple[int, int, int]) -> None:
        pygame.draw.rect(self.screen, color, button['rect'])
        pygame.draw.rect(self.screen, COLOR_WHITE, button['rect'], MENU_BUTTON_BORDER_WIDTH)
        self._draw_button_text(button)
        
    def _draw_button_text(self, button: Dict[str, Any]) -> None:
        text_surface = self.font.render(button['text'], True, button['text_color'])
        text_rect = text_surface.get_rect(center=button['rect'].center)
        self.screen.blit(text_surface, text_rect)

    def update_connection_status(self) -> Tuple[str, Tuple[int, int, int]]:
        return self._get_connected_status() if self.server_connected else self._get_disconnected_status()
            
    def _get_connected_status(self) -> Tuple[str, Tuple[int, int, int]]:
        self._update_connect_button_connected()
        
        return self._get_players_ready_status() if self.players_ready else self._get_waiting_players_status()
            
    def _update_connect_button_connected(self) -> None:
        self.connect_button['text'] = MENU_TEXT['CONNECT_CONNECTED']
        self.connect_button['enabled'] = False
        self.connect_button['color'] = COLOR_BUTTON_CONNECT_ACTIVE
        
    def _get_players_ready_status(self) -> Tuple[str, Tuple[int, int, int]]:
        self.start_button['enabled'] = True
        return MENU_TEXT['STATUS_READY'], COLOR_GREEN
        
    def _get_waiting_players_status(self) -> Tuple[str, Tuple[int, int, int]]:
        self.start_button['enabled'] = False
        return MENU_TEXT['STATUS_CONNECTING'], COLOR_YELLOW
        
    def _get_disconnected_status(self) -> Tuple[str, Tuple[int, int, int]]:
        self.connect_button['text'] = MENU_TEXT['CONNECT_DEFAULT']
        self.connect_button['enabled'] = True
        self.connect_button['color'] = COLOR_BUTTON_CONNECT
        self.start_button['enabled'] = False
        return MENU_TEXT['STATUS_DISCONNECTED'], MENU_STATUS_COLOR_DISCONNECTED

    def render_all_buttons(self, mouse_pos: Tuple[int, int]) -> None:
        for button in self.buttons:
            color = self._get_button_color(button, mouse_pos)
            self.draw_button(button, color)
            
    def _get_button_color(self, button: Dict[str, Any], mouse_pos: Tuple[int, int]) -> Tuple[int, int, int]:
        if not button['enabled']:
            return button.get('disabled_color', (100, 100, 100))
        
        return button['hover_color'] if button['rect'].collidepoint(mouse_pos) else button['color']

    def draw(self) -> None:
        self.draw_background()
        
        mouse_pos = pygame.mouse.get_pos()
        self.render_all_buttons(mouse_pos)
        
        self._draw_connection_status()
        self.draw_mute_button(mouse_pos)
        
    def _draw_connection_status(self) -> None:
        status_text, status_color = self.update_connection_status()
        
        status_font = pygame.font.Font(MENU_FONT_SIZE_DEFAULT, FONT_SIZE_NORMAL)
        status_surface = status_font.render(status_text, True, status_color)
        status_rect = status_surface.get_rect(center=(self.width // MENU_BUTTON_DIVISION_FACTOR, MENU_STATUS_Y))
        self.screen.blit(status_surface, status_rect)
        
    
    def draw_mute_button(self, mouse_pos: Tuple[int, int]) -> None:
        color = self._get_mute_button_color(mouse_pos)
        self._draw_mute_button_rectangle(color)
        self._draw_mute_button_text()
        
    def _get_mute_button_color(self, mouse_pos: Tuple[int, int]) -> Tuple[int, int, int]:
        return (self.mute_button['hover_color'] 
                if self.mute_button['rect'].collidepoint(mouse_pos) 
                else self.mute_button['color'])
            
    def _draw_mute_button_rectangle(self, color: Tuple[int, int, int]) -> None:
        pygame.draw.rect(self.screen, color, self.mute_button['rect'])
        pygame.draw.rect(self.screen, COLOR_WHITE, self.mute_button['rect'], MUTE_BUTTON_BORDER_WIDTH)
        
    def _draw_mute_button_text(self) -> None:
        text = self._get_mute_button_text()
        text_surface = self.mute_font.render(text, True, self.mute_button['text_color'])
        text_rect = text_surface.get_rect(center=self.mute_button['rect'].center)
        self.screen.blit(text_surface, text_rect)
        
    def _get_mute_button_text(self) -> str:
        return self.mute_button['text_muted'] if self.music_muted else self.mute_button['text']
    
    def toggle_music_mute(self) -> None:
        self.music_muted = not self.music_muted
        self._apply_music_volume()
        self._print_music_status()
        
    def _apply_music_volume(self) -> None:
        volume = MUTED_VOLUME if self.music_muted else MUSIC_VOLUME_MENU
        pygame.mixer.music.set_volume(volume)
        
    def _print_music_status(self) -> None:
        pass
    
    def set_connection_status(self, connected: bool, players_ready: bool = False) -> None:
        self.server_connected = connected
        self.players_ready = players_ready