package SheriffsssPackage;

import java.awt.Color;

public class CombatFloatingText {
	public static final int LIFETIME_TICKS = GameConfig.TARGET_FPS * 3 / 4;
	private static final int RISE_PIXELS = 22;

	private final int worldX;
	private final int worldY;
	private final String message;
	private final Color color;
	private int ageTicks;

	public CombatFloatingText(int worldX, int worldY, String message, Color color) {
		this.worldX = worldX;
		this.worldY = worldY;
		this.message = message == null ? "" : message;
		this.color = color == null ? Color.WHITE : color;
	}

	public void update() {
		this.ageTicks++;
	}

	public boolean isExpired() {
		return this.ageTicks >= LIFETIME_TICKS;
	}

	public int getWorldX() {
		return this.worldX;
	}

	public int getWorldY() {
		return this.worldY;
	}

	public String getMessage() {
		return this.message;
	}

	public Color getColor() {
		return this.color;
	}

	public int getOffsetPixels() {
		return this.ageTicks * RISE_PIXELS / LIFETIME_TICKS;
	}

	public int getAlpha() {
		return Math.max(0, 255 - this.ageTicks * 255 / LIFETIME_TICKS);
	}
}
