package SheriffsssPackage;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public final class TextRenderer {
	public enum Style {
		PLAIN(0),
		OUTLINED(0),
		SOFT_SHADOW_MENU(1),
		SOFT_SHADOW_TRAINING(2);

		private final int shadowOffset;

		Style(int shadowOffset) {
			this.shadowOffset = shadowOffset;
		}
	}

	private static final Color SHADOW_SOFT = new Color(0xFF, 0xEB, 0xCE, 62);
	private static final Color SHADOW_MID = new Color(0xFF, 0xEB, 0xCE, 92);
	private static final Color SHADOW_CORE = new Color(0xFF, 0xEB, 0xCE, 132);
	private static final Color OUTLINE_COLOR = Color.BLACK;

	private TextRenderer() {
	}

	public static void draw(Graphics2D g2, Font font, String text, Color color, int x, int y, boolean shadow) {
		draw(g2, font, text, color, x, y, shadow ? Style.OUTLINED : Style.PLAIN);
	}

	public static void draw(Graphics2D g2, Font font, String text, Color color, int x, int y, Style style) {
		g2.setFont(font);
		if (style == Style.OUTLINED) {
			drawOutline(g2, text, x, y);
		} else if (style != Style.PLAIN) {
			drawSoftShadow(g2, text, x, y, style.shadowOffset);
		}
		g2.setColor(color);
		g2.drawString(text, x, y);
	}

	public static void drawCentered(Graphics2D g2, Font font, String text, Color color, int centerX, int baselineY, Style style) {
		draw(g2, font, text, color, centeredX(g2, font, text, centerX), baselineY, style);
	}

	public static int centeredX(Graphics2D g2, Font font, String text, int centerX) {
		g2.setFont(font);
		return centerX - g2.getFontMetrics().stringWidth(text) / 2;
	}

	private static void drawSoftShadow(Graphics2D g2, String text, int x, int y, int offset) {
		int shadowX = x + offset;
		int shadowY = y + offset;
		g2.setColor(SHADOW_SOFT);
		g2.drawString(text, shadowX - 2, shadowY);
		g2.drawString(text, shadowX + 2, shadowY);
		g2.drawString(text, shadowX, shadowY - 2);
		g2.drawString(text, shadowX, shadowY + 2);
		g2.setColor(SHADOW_MID);
		g2.drawString(text, shadowX - 1, shadowY);
		g2.drawString(text, shadowX + 1, shadowY);
		g2.drawString(text, shadowX, shadowY - 1);
		g2.drawString(text, shadowX, shadowY + 1);
		g2.setColor(SHADOW_CORE);
		g2.drawString(text, shadowX, shadowY);
	}

	private static void drawOutline(Graphics2D g2, String text, int x, int y) {
		g2.setColor(OUTLINE_COLOR);
		g2.drawString(text, x - 1, y);
		g2.drawString(text, x + 1, y);
		g2.drawString(text, x, y - 1);
		g2.drawString(text, x, y + 1);
	}
}
