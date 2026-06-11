package SheriffsssPackage;

import java.awt.Color;

public enum MapObjectType {
	DRY_BUSH(0, 1, 1, "sprites/ArbustoMuerto.png", GameTheme.DIRT),
	WOODEN_FENCE(1, 1, 1, "sprites/CercoMadera1.png", GameTheme.DIRT, Player.HITBOX_WIDTH, Player.HITBOX_HEIGHT),
	TRAINING_BORDER_TREE_1(2, 2, 3, "sprites/Arbol1.png", GameTheme.DARK_DIRT),
	TRAINING_BORDER_TREE_2(3, 2, 3, "sprites/Arbol3.png", GameTheme.DARK_DIRT),
	TRAINING_BORDER_TREE_3(4, 2, 3, "sprites/Arbol2.png", GameTheme.DARK_DIRT),
	TRAINING_BORDER_TREE_4(5, 2, 3, "sprites/Arbol4.png", GameTheme.DARK_DIRT);

	private final int id;
	private final int footprintWidth;
	private final int footprintHeight;
	private final String spritePath;
	private final Color minimapColor;
	private final int collisionWidth;
	private final int collisionHeight;
	private static final MapObjectType[] VALUES = values();

	MapObjectType(int id, int footprintWidth, int footprintHeight, String spritePath, Color minimapColor) {
		this(id, footprintWidth, footprintHeight, spritePath, minimapColor, GameConfig.TILE_SIZE, GameConfig.TILE_SIZE);
	}

	MapObjectType(int id, int footprintWidth, int footprintHeight, String spritePath, Color minimapColor, int collisionWidth, int collisionHeight) {
		this.id = id;
		this.footprintWidth = footprintWidth;
		this.footprintHeight = footprintHeight;
		this.spritePath = spritePath;
		this.minimapColor = minimapColor;
		this.collisionWidth = Math.max(1, collisionWidth);
		this.collisionHeight = Math.max(1, collisionHeight);
	}

	public int getId() { return this.id; }
	public int getFootprintWidth() { return this.footprintWidth; }
	public int getFootprintHeight() { return this.footprintHeight; }
	public String getSpritePath() { return this.spritePath; }
	public int getCollisionWidth() { return this.collisionWidth; }
	public int getCollisionHeight() { return this.collisionHeight; }
	public boolean isBreakable() { return false; }
	public double getDurability() { return 0.0; }
	public int getLightRadiusTiles() { return 0; }
	public double getLightIntensity() { return 0.0; }
	public Color resolveMinimapColor(int drawIndex) { return this.minimapColor; }

	public static int getMaxFootprintWidth() {
		int maxWidth = 1;
		for (MapObjectType type : VALUES) {
			maxWidth = Math.max(maxWidth, type.footprintWidth);
		}
		return maxWidth;
	}

	public static int getMaxFootprintHeight() {
		int maxHeight = 1;
		for (MapObjectType type : VALUES) {
			maxHeight = Math.max(maxHeight, type.footprintHeight);
		}
		return maxHeight;
	}
}
