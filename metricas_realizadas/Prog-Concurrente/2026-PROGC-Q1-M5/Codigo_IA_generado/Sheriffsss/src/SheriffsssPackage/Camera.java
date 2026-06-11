package SheriffsssPackage;

public class Camera {
	private int centerWorldX;
	private int centerWorldY;
	private int startTileX;
	private int endTileX;
	private int startTileY;
	private int endTileY;

	public void update(int centerWorldX, int centerWorldY) {
		this.centerWorldX = centerWorldX;
		this.centerWorldY = centerWorldY;
		this.startTileX = Math.floorDiv(centerWorldX - GameConfig.SCREEN_CENTER_X, GameConfig.TILE_SIZE) - GameConfig.RENDER_PADDING_TILES;
		this.endTileX = Math.floorDiv(centerWorldX + GameConfig.SCREEN_CENTER_X, GameConfig.TILE_SIZE) + GameConfig.RENDER_PADDING_TILES;
		this.startTileY = Math.floorDiv(centerWorldY - GameConfig.SCREEN_CENTER_Y, GameConfig.TILE_SIZE) - GameConfig.RENDER_PADDING_TILES;
		this.endTileY = Math.floorDiv(centerWorldY + GameConfig.SCREEN_CENTER_Y, GameConfig.TILE_SIZE) + GameConfig.RENDER_PADDING_TILES;
	}

	public int tileToScreenX(int tileX) {
		return tileX * GameConfig.TILE_SIZE - this.centerWorldX + GameConfig.SCREEN_CENTER_X;
	}

	public int tileToScreenY(int tileY) {
		return tileY * GameConfig.TILE_SIZE - this.centerWorldY + GameConfig.SCREEN_CENTER_Y;
	}

	public int worldToScreenX(int worldX) {
		return worldX - this.centerWorldX + GameConfig.SCREEN_CENTER_X;
	}

	public int worldToScreenY(int worldY) {
		return worldY - this.centerWorldY + GameConfig.SCREEN_CENTER_Y;
	}

	public int getStartTileX() {
		return this.startTileX;
	}

	public int getEndTileX() {
		return this.endTileX;
	}

	public int getStartTileY() {
		return this.startTileY;
	}

	public int getEndTileY() {
		return this.endTileY;
	}
}
