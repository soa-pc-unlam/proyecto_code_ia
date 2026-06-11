package SheriffsssPackage;

public class MapObject {
	private final MapObjectType type;
	private final int rootTileX;
	private final int rootTileY;
	private final int drawIndex;
	private final boolean solid;
	private final boolean abovePlayer;
	private double durabilityDamage;

	public MapObject(MapObjectType type, int rootTileX, int rootTileY, int drawIndex, boolean solid, boolean abovePlayer) {
		this.type = type;
		this.rootTileX = rootTileX;
		this.rootTileY = rootTileY;
		this.drawIndex = drawIndex;
		this.solid = solid;
		this.abovePlayer = abovePlayer;
	}

	public MapObjectType getType() {
		return this.type;
	}

	public int getRootTileX() {
		return this.rootTileX;
	}

	public int getRootTileY() {
		return this.rootTileY;
	}

	public int getDrawIndex() {
		return this.drawIndex;
	}

	public boolean isSolid() {
		return this.solid;
	}

	public boolean isAbovePlayer() {
		return this.abovePlayer;
	}

	public double getDurabilityDamage() {
		return this.durabilityDamage;
	}

	public void resetDurabilityDamage() {
		this.durabilityDamage = 0.0;
	}

	public boolean isRootCell(int tileX, int tileY) {
		return this.rootTileX == tileX && this.rootTileY == tileY;
	}
}
