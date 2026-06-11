package SheriffsssPackage;

import java.awt.Color;
import java.awt.image.BufferedImage;

public enum TileType {
	SAND(0, false, Color.YELLOW, "sprites/Arena.png");

	private static final TileType[] BY_ID = values();

	private final int id;
	private final boolean solid;
	private final Color minimapColor;
	private final String spritePath;

	TileType(int id, boolean solid, Color minimapColor, String spritePath) {
		this.id = id;
		this.solid = solid;
		this.minimapColor = minimapColor;
		this.spritePath = spritePath;
	}

	public int getId() { return this.id; }
	public boolean isSolid() { return this.solid; }
	public boolean isHazardous() { return false; }
	public Color getMinimapColor() { return this.minimapColor; }
	public String[] getSpritePaths() { return new String[] { this.spritePath }; }
	public int getLightRadiusTiles() { return 0; }
	public double getLightIntensity() { return 0.0; }
	public BufferedImage getSprite(AssetManager assets, long frameCount) { return assets.getImage(this.spritePath); }

	public static TileType fromId(int id) {
		if (id < 0 || id >= BY_ID.length) {
			return SAND;
		}
		return BY_ID[id];
	}
}
