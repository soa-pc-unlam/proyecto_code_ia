package SheriffsssPackage;

import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public final class GameConfig {
	private static final String DISPLAY_CONFIG_PATH = "saves/game.cfg";
	private static final String DISPLAY_CONFIG_FULLSCREEN = "fullscreen";
	private static final String DISPLAY_CONFIG_RESOLUTION = "resolution";
	private static final String DISPLAY_CONFIG_MUSIC_VOLUME = "musicVolume";
	private static final String DISPLAY_CONFIG_SFX_VOLUME = "sfxVolume";
	private static final char DISPLAY_CONFIG_SEPARATOR = '=';
	private static final Dimension[] WINDOW_RESOLUTIONS = {
		new Dimension(800, 704),
		new Dimension(1024, 768),
		new Dimension(1280, 720),
		new Dimension(1366, 768),
		new Dimension(1600, 900),
		new Dimension(1920, 1080)
	};

	public static final int TRAINING_MAX_ENEMIES = 50;
	public static final int TRAINING_DEFAULT_ENEMIES = 1;
	public static final int TRAINING_ARENA_TILES_WIDE = 100;
	public static final int TRAINING_ARENA_TILES_HIGH = 100;
	/** Lado del cuadrado de arena interior (dianas, cerco central); centrado en el mapa de entrenamiento. */
	public static final int TRAINING_INTERIOR_FENCE_TILES = 24;
	/** Probabilidad [0,1] por tile fuera de arena+cerco de intentar un arbusto o arbol. */
	public static final double TRAINING_WILDERNESS_PROP_PER_TILE = 0.07;
	public static final int TRAINING_TUTORIAL_SKIP_KEY = KeyEvent.VK_K;
	public static final int TRAINING_PANEL_INC_KEY = KeyEvent.VK_PAGE_UP;
	public static final int TRAINING_PANEL_DEC_KEY = KeyEvent.VK_PAGE_DOWN;
	public static final int TRAINING_PANEL_RESET_KEY = KeyEvent.VK_R;
	public static final double DEFAULT_RECOIL_ACCURACY_SCALE = 30.0;
	public static final int CAMERA_ZOOM_IN_KEY = KeyEvent.VK_PLUS;
	public static final int CAMERA_ZOOM_IN_KEY_ALT = KeyEvent.VK_EQUALS;
	public static final int CAMERA_ZOOM_IN_KEY_NUMPAD = KeyEvent.VK_ADD;
	public static final int CAMERA_ZOOM_OUT_KEY = KeyEvent.VK_MINUS;
	public static final int CAMERA_ZOOM_OUT_KEY_NUMPAD = KeyEvent.VK_SUBTRACT;
	public static final int BASE_SCREEN_WIDTH = 800;
	public static final int BASE_SCREEN_HEIGHT = 704;
	public static int SCREEN_WIDTH = BASE_SCREEN_WIDTH;
	public static int SCREEN_HEIGHT = BASE_SCREEN_HEIGHT;
	public static final int TARGET_FPS = 60;
	public static final int TILE_SIZE = 32;
	public static final double CAMERA_MIN_ZOOM = 1.0;
	public static final double CAMERA_MAX_ZOOM = 2.0;
	public static final double CAMERA_ZOOM_STEP = 0.1;
	public static final double CAMERA_ZOOM_HELD_STEP = 0.025;
	public static final int MAP_WIDTH_TILES = 500;
	public static final int MAP_HEIGHT_TILES = 500;
	public static final int RENDER_PADDING_TILES = 2;
	public static final double PLAYER_SPEED = 2.5;
	public static final int TOOL_RANGE_PIXELS = 92;
	public static final int DROP_PICKUP_RADIUS_PIXELS = 48;
	public static final double DROP_ATTRACT_SPEED_PIXELS = 7.5;
	public static final int INVENTORY_DROP_PICKUP_DELAY_TICKS = TARGET_FPS;
	public static final int INVENTORY_DROP_OFFSET_PIXELS = DROP_PICKUP_RADIUS_PIXELS + 24;
	public static final int MAX_LIGHT_RADIUS_TILES = 7;
	public static int SCREEN_CENTER_X = SCREEN_WIDTH / 2;
	public static int SCREEN_CENTER_Y = SCREEN_HEIGHT / 2;
	public static int PLAYER_DRAW_X = SCREEN_CENTER_X - TILE_SIZE / 2;
	public static int PLAYER_DRAW_Y = SCREEN_CENTER_Y - TILE_SIZE / 2;
	public static int SETTINGS_PANEL_X = 200;
	public static final int SETTINGS_PANEL_Y = 96;
	public static final int SETTINGS_PANEL_WIDTH = 400;
	public static final int SETTINGS_PANEL_HEIGHT = 550;
	public static int SETTINGS_SLIDER_X = 310;
	public static final int SETTINGS_SLIDER_WIDTH = 230;
	public static final int SETTINGS_MUSIC_SLIDER_Y = 190;
	public static final int SETTINGS_SFX_SLIDER_Y = 246;
	public static final int SETTINGS_RESOLUTION_SLIDER_Y = 302;
	public static int SETTINGS_BUTTON_X = 280;
	public static final int SETTINGS_BUTTON_WIDTH = 240;
	public static final int SETTINGS_BUTTON_HEIGHT = 42;
	public static final int SETTINGS_FULLSCREEN_BUTTON_Y = 346;
	public static final int SETTINGS_RESUME_BUTTON_Y = 394;
	public static final int SETTINGS_DEBUG_BUTTON_Y = 442;
	public static final int SETTINGS_MENU_BUTTON_Y = 490;
	public static final int SETTINGS_QUIT_BUTTON_Y = 538;
	public static final int TRAINING_SETTINGS_OVERLAY_Y_OFFSET = 48;
	public static final int DEATH_SPECTATE_DELAY_TICKS = TARGET_FPS * 2;
	public static int DEATH_SPECTATE_BUTTON_X = SCREEN_CENTER_X - 130;
	public static int DEATH_SPECTATE_BUTTON_Y = SCREEN_CENTER_Y + 92;
	public static final int DEATH_SPECTATE_BUTTON_WIDTH = 260;
	public static final int DEATH_SPECTATE_BUTTON_HEIGHT = 48;
	public static final int INFO_MESSAGE_TICKS = TARGET_FPS * 4;
	public static final int SPATIAL_SFX_FULL_VOLUME_RADIUS_PIXELS = TILE_SIZE * 8;
	public static final int SPATIAL_SFX_AUDIBLE_RADIUS_PIXELS = TILE_SIZE * 28;
	public static final int ENEMY_ATTACK_TARGET_TOLERANCE_PIXELS = TILE_SIZE / 2;
	private static int windowResolutionIndex;
	private static boolean fullscreenPreferred;
	private static double musicVolume = 0.5;
	private static double sfxVolume = 0.5;

	private GameConfig() {
	}

	public static void setViewportSize(int width, int height) {
		SCREEN_WIDTH = Math.max(1, width);
		SCREEN_HEIGHT = Math.max(1, height);
		SCREEN_CENTER_X = SCREEN_WIDTH / 2;
		SCREEN_CENTER_Y = SCREEN_HEIGHT / 2;
		PLAYER_DRAW_X = SCREEN_CENTER_X - TILE_SIZE / 2;
		PLAYER_DRAW_Y = SCREEN_CENTER_Y - TILE_SIZE / 2;
		SETTINGS_PANEL_X = SCREEN_CENTER_X - SETTINGS_PANEL_WIDTH / 2;
		SETTINGS_SLIDER_X = SCREEN_CENTER_X - 90;
		SETTINGS_BUTTON_X = SCREEN_CENTER_X - SETTINGS_BUTTON_WIDTH / 2;
		DEATH_SPECTATE_BUTTON_X = SCREEN_CENTER_X - DEATH_SPECTATE_BUTTON_WIDTH / 2;
		DEATH_SPECTATE_BUTTON_Y = SCREEN_CENTER_Y + 92;
	}

	public static void loadDisplayPreferences() {
		File file = new File(DISPLAY_CONFIG_PATH);
		if (!file.exists()) {
			return;
		}
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				applyDisplayConfigLine(line);
			}
		} catch (IOException ignored) {
		}
	}

	private static void applyDisplayConfigLine(String line) {
		int separator = line.indexOf(DISPLAY_CONFIG_SEPARATOR);
		if (separator < 0) {
			return;
		}
		String key = line.substring(0, separator).trim();
		String value = line.substring(separator + 1).trim();
		if (DISPLAY_CONFIG_FULLSCREEN.equals(key)) {
			fullscreenPreferred = Boolean.parseBoolean(value);
		} else if (DISPLAY_CONFIG_RESOLUTION.equals(key)) {
			applyResolutionValue(value);
		} else if (DISPLAY_CONFIG_MUSIC_VOLUME.equals(key)) {
			musicVolume = parseVolume(value, musicVolume);
		} else if (DISPLAY_CONFIG_SFX_VOLUME.equals(key)) {
			sfxVolume = parseVolume(value, sfxVolume);
		}
	}

	private static double parseVolume(String value, double fallback) {
		try {
			return clamp01(Double.parseDouble(value));
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static void applyResolutionValue(String value) {
		for (int i = 0; i < WINDOW_RESOLUTIONS.length; i++) {
			Dimension resolution = WINDOW_RESOLUTIONS[i];
			if ((resolution.width + "x" + resolution.height).equals(value)) {
				windowResolutionIndex = i;
				return;
			}
		}
	}

	public static void saveDisplayPreferences() {
		File file = new File(DISPLAY_CONFIG_PATH);
		ensureParentDirExists(file);
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
			writer.write(DISPLAY_CONFIG_FULLSCREEN + DISPLAY_CONFIG_SEPARATOR + fullscreenPreferred);
			writer.newLine();
			writer.write(DISPLAY_CONFIG_RESOLUTION + DISPLAY_CONFIG_SEPARATOR + getWindowResolutionLabel());
			writer.newLine();
			writer.write(DISPLAY_CONFIG_MUSIC_VOLUME + DISPLAY_CONFIG_SEPARATOR + musicVolume);
			writer.newLine();
			writer.write(DISPLAY_CONFIG_SFX_VOLUME + DISPLAY_CONFIG_SEPARATOR + sfxVolume);
			writer.newLine();
		} catch (IOException ignored) {
		}
	}

	private static void ensureParentDirExists(File file) {
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
	}

	public static int getWindowResolutionIndex() {
		return windowResolutionIndex;
	}

	public static Dimension getWindowResolution() {
		Dimension resolution = WINDOW_RESOLUTIONS[windowResolutionIndex];
		return new Dimension(resolution.width, resolution.height);
	}

	public static int getWindowResolutionCount() {
		return WINDOW_RESOLUTIONS.length;
	}

	public static String getWindowResolutionLabel() {
		Dimension resolution = WINDOW_RESOLUTIONS[windowResolutionIndex];
		return resolution.width + "x" + resolution.height;
	}

	public static String getWindowResolutionLabel(int index) {
		int clampedIndex = Math.max(0, Math.min(WINDOW_RESOLUTIONS.length - 1, index));
		Dimension resolution = WINDOW_RESOLUTIONS[clampedIndex];
		return resolution.width + "x" + resolution.height;
	}

	public static void setWindowResolutionIndex(int index) {
		windowResolutionIndex = Math.max(0, Math.min(WINDOW_RESOLUTIONS.length - 1, index));
	}

	public static boolean isFullscreenPreferred() {
		return fullscreenPreferred;
	}

	public static void setFullscreenPreferred(boolean fullscreenPreferred) {
		GameConfig.fullscreenPreferred = fullscreenPreferred;
	}

	public static double getMusicVolume() {
		return musicVolume;
	}

	public static void setMusicVolume(double musicVolume) {
		GameConfig.musicVolume = clamp01(musicVolume);
	}

	public static double getSfxVolume() {
		return sfxVolume;
	}

	public static void setSfxVolume(double sfxVolume) {
		GameConfig.sfxVolume = clamp01(sfxVolume);
	}

	private static double clamp01(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

}
