package SheriffsssPackage;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public class MenuRenderer {
	private static final TextRenderer.Style MENU_TEXT_STYLE = TextRenderer.Style.OUTLINED;
	private static final int ROOT_MENU_BTN_W = 320;
	private static final int ROOT_MENU_BTN_H = 56;
	private static final int ROOT_MENU_BTN_GAP = 20;
	private static final int ROOT_MENU_FIRST_Y = 436;
	private static final int ROOT_TITLE_Y = 104;
	private static final int ROOT_TITLE_WIDTH = 250;
	private static final int ROOT_TITLE_HEIGHT = 250;
	private static final int ROOT_TITLE_FRAME_TICKS = 13;
	private static final int MENU_BUTTON_ARC = 14;
	private static final String[] ROOT_TITLE_FRAME_PATHS = {
		"sprites/sheriffsss-title/frame_000.png",
		"sprites/sheriffsss-title/frame_001.png",
		"sprites/sheriffsss-title/frame_002.png",
		"sprites/sheriffsss-title/frame_003.png"
	};
	private static final int ROOT_BUTTON_X = (GameConfig.BASE_SCREEN_WIDTH - ROOT_MENU_BTN_W) / 2;
	private static final int TRAINING_BUTTON_X = ROOT_BUTTON_X;
	private static final int TRAINING_BUTTON_Y = ROOT_MENU_FIRST_Y;
	private static final int TRAINING_BUTTON_WIDTH = ROOT_MENU_BTN_W;
	private static final int TRAINING_BUTTON_HEIGHT = ROOT_MENU_BTN_H;
	private static final int TRAINING_SETTINGS_BUTTON_X = ROOT_BUTTON_X;
	private static final int TRAINING_SETTINGS_BUTTON_Y = ROOT_MENU_FIRST_Y + ROOT_MENU_BTN_H + ROOT_MENU_BTN_GAP;
	private static final int TRAINING_SETTINGS_BUTTON_WIDTH = ROOT_MENU_BTN_W;
	private static final int TRAINING_SETTINGS_BUTTON_HEIGHT = ROOT_MENU_BTN_H;
	private static final int TRAINING_EXIT_BUTTON_X = ROOT_BUTTON_X;
	private static final int TRAINING_EXIT_BUTTON_Y = TRAINING_SETTINGS_BUTTON_Y + ROOT_MENU_BTN_H + ROOT_MENU_BTN_GAP;
	private static final int TRAINING_EXIT_BUTTON_WIDTH = ROOT_MENU_BTN_W;
	private static final int TRAINING_EXIT_BUTTON_HEIGHT = ROOT_MENU_BTN_H;
	private static final int MENU_BACK_BUTTON_X = 24;
	private static final int MENU_BACK_BUTTON_Y = 24;
	private static final int MENU_BACK_BUTTON_WIDTH = 110;
	private static final int MENU_BACK_BUTTON_HEIGHT = 40;
	private static final int MENU_SETTINGS_PANEL_HEIGHT = 360;
	private static final int MENU_SETTINGS_BUTTON_X = GameConfig.BASE_SCREEN_WIDTH / 2 - 160;
	private static final int MENU_SETTINGS_FULLSCREEN_BUTTON_Y = 360;
	private static final int MENU_SETTINGS_BUTTON_WIDTH = 320;
	private static final int MENU_SETTINGS_BUTTON_HEIGHT = 42;

	private final BufferedImage backgroundImage;
	private final BufferedImage[] rootTitleFrames = new BufferedImage[ROOT_TITLE_FRAME_PATHS.length];
	private final Font saveTitleFont = new Font("Arial", Font.BOLD, 18);
	private final Font saveRowFont = new Font("Arial", Font.BOLD, 12);
	private final Font settingsTitleFont = new Font("Arial", Font.BOLD, 38);
	private final Font settingsFont = new Font("Arial", Font.BOLD, 18);

	public MenuRenderer(AssetManager assets) {
		this.backgroundImage = assets.getImage(TileType.SAND.getSpritePaths()[0]);
		for (int i = 0; i < ROOT_TITLE_FRAME_PATHS.length; i++) {
			this.rootTitleFrames[i] = assets.getImage(ROOT_TITLE_FRAME_PATHS[i]);
		}
	}

	public void draw(Graphics2D g2, Game game) {
		g2.drawImage(this.backgroundImage, 0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT, null);
		AffineTransform previousTransform = g2.getTransform();
		g2.translate(menuOffsetX(), menuOffsetY());
		if (game.getState() == State.MENU_SETTINGS) {
			drawMenuSettings(g2, game);
		} else {
			drawRootMenu(g2, game);
		}
		g2.setTransform(previousTransform);
	}

	public boolean isTrainingButtonHovered(int mouseX, int mouseY) {
		return contains(mouseX, mouseY, TRAINING_BUTTON_X, TRAINING_BUTTON_Y, TRAINING_BUTTON_WIDTH, TRAINING_BUTTON_HEIGHT);
	}

	public boolean isTrainingSettingsButtonHovered(int mouseX, int mouseY) {
		return contains(mouseX, mouseY, TRAINING_SETTINGS_BUTTON_X, TRAINING_SETTINGS_BUTTON_Y, TRAINING_SETTINGS_BUTTON_WIDTH, TRAINING_SETTINGS_BUTTON_HEIGHT);
	}

	public boolean isExitButtonHovered(int mouseX, int mouseY) {
		return contains(mouseX, mouseY, TRAINING_EXIT_BUTTON_X, TRAINING_EXIT_BUTTON_Y, TRAINING_EXIT_BUTTON_WIDTH, TRAINING_EXIT_BUTTON_HEIGHT);
	}

	public boolean isMenuBackButtonHovered(int mouseX, int mouseY) {
		return containsScreen(mouseX, mouseY, MENU_BACK_BUTTON_X, MENU_BACK_BUTTON_Y, MENU_BACK_BUTTON_WIDTH, MENU_BACK_BUTTON_HEIGHT);
	}

	public boolean isMenuFullscreenButtonHovered(int mouseX, int mouseY) {
		return contains(mouseX, mouseY, MENU_SETTINGS_BUTTON_X, MENU_SETTINGS_FULLSCREEN_BUTTON_Y, MENU_SETTINGS_BUTTON_WIDTH, MENU_SETTINGS_BUTTON_HEIGHT);
	}

	private void drawRootMenu(Graphics2D g2, Game game) {
		drawRootTitle(g2, game);
		drawTrainingOnlyRootMenu(g2, game);
	}

	private void drawTrainingOnlyRootMenu(Graphics2D g2, Game game) {
		drawMenuButton(g2, "TRAINING", TRAINING_BUTTON_X, TRAINING_BUTTON_Y, TRAINING_BUTTON_WIDTH, TRAINING_BUTTON_HEIGHT, isTrainingButtonHovered(game.getInput().getMouseX(), game.getInput().getMouseY()), true);
		drawMenuButton(g2, "SETTINGS", TRAINING_SETTINGS_BUTTON_X, TRAINING_SETTINGS_BUTTON_Y, TRAINING_SETTINGS_BUTTON_WIDTH, TRAINING_SETTINGS_BUTTON_HEIGHT, isTrainingSettingsButtonHovered(game.getInput().getMouseX(), game.getInput().getMouseY()), true);
		drawMenuButton(g2, "EXIT", TRAINING_EXIT_BUTTON_X, TRAINING_EXIT_BUTTON_Y, TRAINING_EXIT_BUTTON_WIDTH, TRAINING_EXIT_BUTTON_HEIGHT, isExitButtonHovered(game.getInput().getMouseX(), game.getInput().getMouseY()), true);
	}

	private void drawRootTitle(Graphics2D g2, Game game) {
		int frameIndex = (int) ((game.getFrameCount() / ROOT_TITLE_FRAME_TICKS) % this.rootTitleFrames.length);
		int titleX = baseCenterX() - ROOT_TITLE_WIDTH / 2;
		g2.drawImage(this.rootTitleFrames[frameIndex], titleX, ROOT_TITLE_Y, ROOT_TITLE_WIDTH, ROOT_TITLE_HEIGHT, null);
	}

	public int getMenuOffsetX() {
		return menuOffsetX();
	}

	public int getMenuOffsetY() {
		return menuOffsetY();
	}

	private boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
		mouseX = baseMouseX(mouseX);
		mouseY = baseMouseY(mouseY);
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	private boolean containsScreen(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	private int baseMouseX(int mouseX) {
		return mouseX - menuOffsetX();
	}

	private int baseMouseY(int mouseY) {
		return mouseY - menuOffsetY();
	}

	private int menuOffsetX() {
		return (GameConfig.SCREEN_WIDTH - GameConfig.BASE_SCREEN_WIDTH) / 2;
	}

	private int menuOffsetY() {
		return (GameConfig.SCREEN_HEIGHT - GameConfig.BASE_SCREEN_HEIGHT) / 2;
	}

	private int baseCenterX() {
		return GameConfig.BASE_SCREEN_WIDTH / 2;
	}

	private int settingsPanelX() {
		return baseCenterX() - GameConfig.SETTINGS_PANEL_WIDTH / 2;
	}

	private int settingsSliderX() {
		return baseCenterX() - 90;
	}

	private int screenButtonX(int x) {
		return x - menuOffsetX();
	}

	private int screenButtonY(int y) {
		return y - menuOffsetY();
	}

	private void drawMenuSettings(Graphics2D g2, Game game) {
		drawMenuButton(g2, "BACK", screenButtonX(MENU_BACK_BUTTON_X), screenButtonY(MENU_BACK_BUTTON_Y), MENU_BACK_BUTTON_WIDTH, MENU_BACK_BUTTON_HEIGHT, isMenuBackButtonHovered(game.getInput().getMouseX(), game.getInput().getMouseY()), true);
		g2.setColor(GameTheme.DIRT);
		g2.fillRect(settingsPanelX(), GameConfig.SETTINGS_PANEL_Y, GameConfig.SETTINGS_PANEL_WIDTH, MENU_SETTINGS_PANEL_HEIGHT);
		g2.setColor(Color.BLACK);
		g2.setStroke(GameTheme.HEAVY_STROKE);
		g2.drawRect(settingsPanelX(), GameConfig.SETTINGS_PANEL_Y, GameConfig.SETTINGS_PANEL_WIDTH, MENU_SETTINGS_PANEL_HEIGHT);

		int titleX = TextRenderer.centeredX(g2, this.settingsTitleFont, "SETTINGS", baseCenterX());
		TextRenderer.draw(g2, this.settingsTitleFont, "SETTINGS", Color.WHITE, titleX, GameConfig.SETTINGS_PANEL_Y + 58, MENU_TEXT_STYLE);
		drawSettingsSlider(g2, "Music", GameConfig.SETTINGS_MUSIC_SLIDER_Y, game.getAudio().getMusicVolume());
		drawSettingsSlider(g2, "SFX", GameConfig.SETTINGS_SFX_SLIDER_Y, game.getAudio().getSfxVolume());
		drawResolutionSlider(g2, game);
		drawMenuButton(g2, "FULLSCREEN: " + (game.isPendingFullscreen() ? "ON" : "OFF"), MENU_SETTINGS_BUTTON_X, MENU_SETTINGS_FULLSCREEN_BUTTON_Y,
			MENU_SETTINGS_BUTTON_WIDTH, MENU_SETTINGS_BUTTON_HEIGHT, isMenuFullscreenButtonHovered(game.getInput().getMouseX(), game.getInput().getMouseY()), true);
	}

	private void drawResolutionSlider(Graphics2D g2, Game game) {
		drawSettingsSlider(g2, "Res", GameConfig.SETTINGS_RESOLUTION_SLIDER_Y, game.getWindowResolutionSliderValue());
		String label = game.getWindowResolutionLabel();
		int labelX = TextRenderer.centeredX(g2, this.saveRowFont, label, settingsSliderX() + GameConfig.SETTINGS_SLIDER_WIDTH / 2);
		TextRenderer.draw(g2, this.saveRowFont, label, Color.WHITE, labelX, GameConfig.SETTINGS_RESOLUTION_SLIDER_Y + 34, MENU_TEXT_STYLE);
	}

	private void drawSettingsSlider(Graphics2D g2, String label, int y, double value) {
		TextRenderer.draw(g2, this.settingsFont, label, Color.WHITE, settingsPanelX() + 55, y + 6, MENU_TEXT_STYLE);
		g2.setColor(Color.BLACK);
		g2.fillRect(settingsSliderX() - 2, y - 2, GameConfig.SETTINGS_SLIDER_WIDTH + 4, 12);
		g2.setColor(Color.GRAY);
		g2.fillRect(settingsSliderX(), y, GameConfig.SETTINGS_SLIDER_WIDTH, 8);
		g2.setColor(Color.GREEN);
		g2.fillRect(settingsSliderX(), y, (int) (GameConfig.SETTINGS_SLIDER_WIDTH * value), 8);
		int knobX = settingsSliderX() + (int) (GameConfig.SETTINGS_SLIDER_WIDTH * value);
		g2.setColor(Color.WHITE);
		g2.fillRect(knobX - 5, y - 6, 10, 20);
	}

	private void drawMenuButton(Graphics2D g2, String text, int x, int y, int width, int height, boolean hovered, boolean enabled) {
		g2.setColor(GameTheme.DIRT);
		fillRoundedRect(g2, x, y, width, height);
		g2.setColor(hovered && enabled ? Color.YELLOW : Color.BLACK);
		g2.setStroke(GameTheme.LIGHT_STROKE);
		strokeRoundedRect(g2, x - 2, y - 2, width + 4, height + 4);
		Color textColor = !enabled ? Color.GRAY : hovered ? Color.YELLOW : Color.WHITE;
		int textX = TextRenderer.centeredX(g2, this.saveTitleFont, text, x + width / 2);
		TextRenderer.draw(g2, this.saveTitleFont, text, textColor, textX, y + height / 2 + 7, MENU_TEXT_STYLE);
	}

	private void fillRoundedRect(Graphics2D g2, int x, int y, int w, int h) {
		g2.fill(new RoundRectangle2D.Float(x, y, w, h, MENU_BUTTON_ARC, MENU_BUTTON_ARC));
	}

	private void strokeRoundedRect(Graphics2D g2, int x, int y, int w, int h) {
		g2.draw(new RoundRectangle2D.Float(x, y, w, h, MENU_BUTTON_ARC + 2, MENU_BUTTON_ARC + 2));
	}

}
