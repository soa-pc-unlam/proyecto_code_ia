import pygame
import sys
import os
import asyncio

sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from constants import *

INFINITE_LOOP = -1
MUTED_VOLUME = 0.0
PIRATE_MUSIC_FILE = 'piratas.mp3'
BACKGROUND_MUSIC_FILE = 'background.mp3'

from .menu_screen import MenuScreen
from .game_screen import GameScreen
from .network_manager import NetworkManager
from .game_over_screen import GameOverScreen

class BattleshipClient:
    def __init__(self):
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)
        self.server_host = DEFAULT_SERVER_HOST
        self.server_port = DEFAULT_SERVER_PORT
        self._initialize_pygame_systems()
        self._setup_window_configuration()
        self._initialize_game_state()
        self._initialize_screens_and_managers()
        self._setup_audio_system()
    

    def set_connection_params(self, host=None, port=None):
        if host:
            self.server_host = host
        if port:
            self.server_port = port

    def _initialize_pygame_systems(self):
        pygame.init()
        self._initialize_audio_mixer()
    
    def _initialize_audio_mixer(self):
        pygame.mixer.pre_init(
            frequency=MIXER_FREQUENCY, 
            size=MIXER_SIZE, 
            channels=MIXER_CHANNELS, 
            buffer=MIXER_BUFFER
        )
        pygame.mixer.init()
    
    def _setup_window_configuration(self):
        self.min_width = MIN_WINDOW_WIDTH
        self.min_height = MIN_WINDOW_HEIGHT
        self._create_game_window()
    
    def _create_game_window(self):
        pygame.display.set_caption("Batalla Naval - Cliente")
        self.screen = pygame.display.set_mode(
            (INITIAL_WINDOW_WIDTH, INITIAL_WINDOW_HEIGHT), 
            pygame.RESIZABLE
        )
        self._finalize_window_setup()
    
    def _finalize_window_setup(self):
        pygame.display.flip()
        self.width = self.screen.get_width()
        self.height = self.screen.get_height()
        self.clock = pygame.time.Clock()
    
    def _initialize_game_state(self):
        self.current_state = "menu"
        self.running = True
    
    def _initialize_screens_and_managers(self):
        self.network_manager = NetworkManager()
        self.menu_screen = MenuScreen(self.screen)
        self.game_screen = GameScreen(self.screen, self.network_manager, self.loop)
        self.game_over_screen = None
        self.setup_network_callbacks()
    
    def _setup_audio_system(self):
        self.init_background_music()
    
    def init_background_music(self):
        try:
            self._load_and_play_menu_music()
        except pygame.error as e:
            print(f"Error al cargar música de fondo: {e}")
        except FileNotFoundError:
            print(f"No se encontró el archivo {PIRATE_MUSIC_FILE}")
    
    def _load_and_play_menu_music(self):
        music_path = os.path.join("assets", "sounds", PIRATE_MUSIC_FILE)
        pygame.mixer.music.load(music_path)
        pygame.mixer.music.set_volume(MUSIC_VOLUME_MENU)
        pygame.mixer.music.play(INFINITE_LOOP)
   
    def run(self):
        try:
            while self.running:
                self._process_tasks()
                self._process_game_events()
                self._check_connection_status()
                self._render_current_state()
                self._update_display_and_clock()
        finally:
            self._cleanup_and_exit()
    
    def _process_tasks(self):
        try:
            self.loop.run_until_complete(asyncio.sleep(0))
        except RuntimeError:
            pass
    
    def _process_game_events(self):
        events = pygame.event.get()
        for event in events:
            self._handle_system_events(event)
            self._handle_state_specific_events(event)
    
    def _handle_system_events(self, event):
        if event.type == pygame.QUIT or (event.type == pygame.KEYDOWN and event.key == pygame.K_ESCAPE):
            self.running = False
        elif event.type == pygame.VIDEORESIZE:
            self._handle_window_resize(event)
    
    def _handle_window_resize(self, event):
        self._update_window_dimensions(event)
        self._preserve_and_recreate_screens()
    
    def _update_window_dimensions(self, event):
        self.width = max(event.w, self.min_width)
        self.height = max(event.h, self.min_height)
        self.screen = pygame.display.set_mode(
            (self.width, self.height), pygame.RESIZABLE
        )
    
    def _preserve_and_recreate_screens(self):
        if self._should_preserve_game_state():
            self._preserve_and_restore_game_state()
        else:
            self._recreate_game_screen()
        self._recreate_other_screens()
    
    def _should_preserve_game_state(self):
        return (hasattr(self, 'game_screen') and 
                self.current_state == "game")
    
    def _preserve_and_restore_game_state(self):
        saved_state = self._save_current_game_state()
        self._recreate_game_screen()
        self._restore_game_state(saved_state)
    
    def _save_current_game_state(self):
        return {
            'game_phase': self.game_screen.game_phase,
            'current_ship_index': self.game_screen.current_ship_index,
            'ship_horizontal': self.game_screen.ship_horizontal,
            'my_turn': self.game_screen.my_turn,
            'my_ships': self._get_my_ships_copy(),
            'enemy_shots': self._get_enemy_shots_copy(),
            'my_board_shots': self._get_my_board_shots_copy(),
            'enemy_sunk_ships': self.game_screen.enemy_sunk_ships.copy(),
            'enemy_sunk_ships_info': self.game_screen.enemy_sunk_ships_info.copy(),
        }
    
    def _get_my_ships_copy(self):
        if hasattr(self.game_screen, 'my_board'):
            return self.game_screen.my_board.ships.copy()
        return {}
    
    def _get_my_board_shots_copy(self):
        if hasattr(self.game_screen, 'my_board'):
            return self.game_screen.my_board.shots.copy()  # Disparos en TU tablero
        return {}
    
    def _get_enemy_shots_copy(self):
        if hasattr(self.game_screen, 'enemy_board'):
            return self.game_screen.enemy_board.shots.copy()
        return {}
    
    def _recreate_game_screen(self):
        self.game_screen = GameScreen(self.screen, self.network_manager, self.loop)
    
    def _restore_game_state(self, saved_state):
        self.game_screen.game_phase = saved_state['game_phase']
        self.game_screen.current_ship_index = saved_state['current_ship_index']
        self.game_screen.ship_horizontal = saved_state['ship_horizontal']
        self.game_screen.my_turn = saved_state['my_turn']
        self.game_screen.my_board.ships = saved_state['my_ships']
        self.game_screen.enemy_board.shots = saved_state['enemy_shots']
        self.game_screen.my_board.shots = saved_state['my_board_shots']
        self.game_screen.enemy_sunk_ships = saved_state.get('enemy_sunk_ships', [])
        self.game_screen.enemy_sunk_ships_info = saved_state.get('enemy_sunk_ships_info', {})
    
    def _recreate_other_screens(self):
        self.menu_screen = MenuScreen(self.screen)
        self.setup_network_callbacks()
        self._recreate_game_over_screen_if_needed()
    
    def _recreate_game_over_screen_if_needed(self):
        if self.game_over_screen is not None:
            is_winner = self.game_over_screen.is_winner
            self.game_over_screen = GameOverScreen(self.screen, is_winner)
        
    def _handle_state_specific_events(self, event):
        if self.current_state == "menu":
            self._handle_menu_events(event)
        elif self.current_state == "game":
            self.game_screen.handle_event(event)
        elif self.current_state == "game_over":
            self._handle_game_over_events(event)
    
    def _handle_menu_events(self, event):
        action = self.menu_screen.handle_event(event)
        self._process_menu_action(action)
    
    def _process_menu_action(self, action):
        if action == "connect":
            self.loop.create_task(self._handle_connect_action())
        elif action == "start_game":
            self.loop.create_task(self._handle_start_game_action())
        elif action == "toggle_music":
            self._handle_toggle_music_action()
    
    async def _handle_connect_action(self):
        await self.connect_to_server(self.server_host, self.server_port)
    
    def _attempt_server_connection(self, config):
        host, port = config['host'], config['port']
        return self.connect_to_server(host, port)
    
    async def connect_to_server(self, host, port):
        return await self.network_manager.connect_to_server(host, port)
    
    async def _handle_start_game_action(self):
        await self.network_manager.start_game()
    
    def _handle_toggle_music_action(self):
        self.menu_screen.toggle_music_mute()
    
    def _handle_game_over_events(self, event):
        action = self.game_over_screen.handle_event(event)
        if action == "accept" or self.game_over_screen.auto_return:
            self.loop.create_task(self._handle_game_over_accept())
    
    async def _handle_game_over_accept(self):
        await self._disconnect_after_game()
        self._reset_menu_state()
        self._restart_menu_music()
        self._return_to_menu()
    
    async def _disconnect_after_game(self):
        if self.network_manager.connected:
            await self.network_manager.disconnect()
    
    def _reset_menu_state(self):
        self.menu_screen.set_connection_status(False, False)
    
    def _restart_menu_music(self):
        self.init_background_music()
        self._apply_mute_if_needed()
    
    def _apply_mute_if_needed(self):
        if hasattr(self.menu_screen, 'music_muted') and self.menu_screen.music_muted:
            pygame.mixer.music.set_volume(MUTED_VOLUME)
    
    def _return_to_menu(self):
        self.current_state = "menu"
        self.game_over_screen = None
    
    def _check_connection_status(self):
        if self._should_check_connection():
            if not self.network_manager.connected:
                self.on_server_disconnect()
    
    def _should_check_connection(self):
        return (self.current_state != "menu" and 
                hasattr(self.network_manager, 'connected') and
                self.current_state in ["game", "game_over"])
    
    def _render_current_state(self):
        if self.current_state == "menu":
            self._render_menu_state()
        elif self.current_state == "game":
            self._render_game_state()
        elif self.current_state == "game_over":
            self._render_game_over_state()
    
    def _render_menu_state(self):
        self.menu_screen.update()
        self.menu_screen.draw()
    
    def _render_game_state(self):
        self.game_screen.update()
        self.game_screen.draw()
    
    def _render_game_over_state(self):
        self.game_screen.update()
        self.game_screen.draw_without_preview()
        self.game_over_screen.draw()
    
    def _update_display_and_clock(self):
        pygame.display.flip()
        self.clock.tick(TARGET_FPS)
    
    def _cleanup_and_exit(self):
        pygame.mixer.music.stop()
        
        # Limpiar tareas asyncio pendientes
        pending_tasks = [task for task in asyncio.all_tasks(self.loop) if not task.done()]
        for task in pending_tasks:
            task.cancel()
        
        # Cerrar el loop asyncio
        if not self.loop.is_closed():
            self.loop.close()
            
        pygame.mixer.quit()
        pygame.quit()
        sys.exit()
    
    def setup_network_callbacks(self):
        self.network_manager.set_players_ready_callback(self.on_players_ready)
        self.network_manager.set_game_start_callback(self.on_game_start)
        self.network_manager.set_game_update_callback(self.on_game_update)
        self.network_manager.set_shot_result_callback(self.on_shot_result)
        self.network_manager.set_game_over_callback(self.on_game_over)
        self.network_manager.set_server_disconnect_callback(self.on_server_disconnect)
    
    def on_players_ready(self, data):
        connected = data.get('connected_players', 0) > 0
        players_ready = data.get('players_ready', False)
        self.menu_screen.set_connection_status(connected, players_ready)
    
    def on_game_start(self, data):
        self._transition_audio_to_game()
        self._reset_game_state_safely("Error reseteando pantalla de juego")
        self.current_state = "game"
    
    def _transition_audio_to_game(self):
        pygame.mixer.music.stop()
        self._start_game_music()
    
    def _start_game_music(self):
        try:
            self._load_and_play_game_music()
        except FileNotFoundError:
            print(f"No se encontró el archivo {BACKGROUND_MUSIC_FILE}")
    
    def _load_and_play_game_music(self):
        game_music_path = os.path.join("assets", "sounds", BACKGROUND_MUSIC_FILE)
        pygame.mixer.music.load(game_music_path)
        pygame.mixer.music.set_volume(MUSIC_VOLUME_GAME)
        pygame.mixer.music.play(INFINITE_LOOP)
    
    def _reset_game_state_safely(self, error_msg):
        if hasattr(self, 'game_screen') and self.game_screen is not None:
            try:
                self.game_screen.reset_game_state()
            except Exception as e:
                print(f"{error_msg}: {e}")
    
    def on_game_update(self, data):
        phase = data.get('phase')
        current_turn = data.get('current_turn')
        
        if phase == 'battle_phase':
            self.game_screen.start_battle_phase()
            is_my_turn = current_turn == self.network_manager.player_id
            self.game_screen.set_my_turn(is_my_turn)
    
    def on_shot_result(self, data):
        try:
            if hasattr(self.game_screen, 'handle_shot_result') and data:
                self.game_screen.handle_shot_result(data)
        except Exception:
            pass
    
    def on_game_over(self, data):
        self._stop_game_music()
        self._reset_game_state_safely("Error reseteando pantalla tras game over")
        self._create_and_show_game_over_screen(data)
    
    def _stop_game_music(self):
        pygame.mixer.music.stop()
    
    def _create_and_show_game_over_screen(self, data):
        is_winner = data.get('is_winner', False)
        self.game_over_screen = GameOverScreen(self.screen, is_winner)
        self.current_state = "game_over"
    
    def on_server_disconnect(self):
        pygame.mixer.music.stop()
        self._reset_network_manager_state()
        self._reset_menu_and_restart_music()
        self._return_to_menu_after_disconnect()
    
    def _reset_network_manager_state(self):
        self.network_manager.connected = False
        self.network_manager.player_id = None
    
    def _reset_menu_and_restart_music(self):
        self.menu_screen.set_connection_status(False, False)
        self.init_background_music()
        self._apply_mute_if_needed()
    
    def _return_to_menu_after_disconnect(self):
        self.current_state = "menu"
        self.game_over_screen = None