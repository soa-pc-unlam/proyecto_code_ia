package SheriffsssPackage;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class GameMap {
	private final int widthTiles;
	private final int heightTiles;
	private final int[][] tiles;
	private final MapObject[][] objectGrid;
	private final BufferedImage minimapImage;

	public GameMap(int widthTiles, int heightTiles) {
		this.widthTiles = widthTiles;
		this.heightTiles = heightTiles;
		this.tiles = new int[widthTiles][heightTiles];
		this.objectGrid = new MapObject[widthTiles][heightTiles];
		this.minimapImage = new BufferedImage(widthTiles, heightTiles, BufferedImage.TYPE_INT_RGB);
	}

	public void clear(TileType defaultTile) {
		for (int x = 0; x < this.widthTiles; x++) {
			Arrays.fill(this.tiles[x], defaultTile.getId());
			Arrays.fill(this.objectGrid[x], null);
		}
	}

	public boolean isInBounds(int tileX, int tileY) {
		return tileX >= 0 && tileX < this.widthTiles && tileY >= 0 && tileY < this.heightTiles;
	}

	public TileType getTile(int tileX, int tileY) {
		if (!isInBounds(tileX, tileY)) {
			return null;
		}
		return TileType.fromId(this.tiles[tileX][tileY]);
	}

	public MapObject getObject(int tileX, int tileY) {
		if (!isInBounds(tileX, tileY)) {
			return null;
		}
		return this.objectGrid[tileX][tileY];
	}

	public int worldToTileX(int worldX) {
		return Math.floorDiv(worldX, GameConfig.TILE_SIZE);
	}

	public int worldToTileY(int worldY) {
		return Math.floorDiv(worldY, GameConfig.TILE_SIZE);
	}

	public TileType getTileAtWorld(int worldX, int worldY) {
		return getTile(worldToTileX(worldX), worldToTileY(worldY));
	}

	public MapObject getObjectAtWorld(int worldX, int worldY) {
		return getObject(worldToTileX(worldX), worldToTileY(worldY));
	}

	public boolean isAreaBlockedAtWorld(int leftWorldX, int topWorldY, int rightWorldX, int bottomWorldY) {
		int startTileX = worldToTileX(leftWorldX);
		int endTileX = worldToTileX(rightWorldX);
		int startTileY = worldToTileY(topWorldY);
		int endTileY = worldToTileY(bottomWorldY);
		for (int tileX = startTileX; tileX <= endTileX; tileX++) {
			for (int tileY = startTileY; tileY <= endTileY; tileY++) {
				TileType tile = getTile(tileX, tileY);
				if (tile == null || tile.isSolid()) {
					return true;
				}
				MapObject mapObject = getObject(tileX, tileY);
				if (mapObject != null && mapObject.isSolid() && intersectsObjectCollision(leftWorldX, topWorldY, rightWorldX, bottomWorldY, tileX, tileY, mapObject)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean intersectsObjectCollision(int leftWorldX, int topWorldY, int rightWorldX, int bottomWorldY,
		int objectTileX, int objectTileY, MapObject mapObject) {
		int objectLeft = objectCollisionLeftWorldX(objectTileX, mapObject);
		int objectTop = objectCollisionTopWorldY(objectTileY, mapObject);
		int objectRight = objectLeft + mapObject.getType().getCollisionWidth() - 1;
		int objectBottom = objectTop + mapObject.getType().getCollisionHeight() - 1;
		return leftWorldX <= objectRight && rightWorldX >= objectLeft && topWorldY <= objectBottom && bottomWorldY >= objectTop;
	}

	public int objectCollisionLeftWorldX(int objectTileX, MapObject mapObject) {
		int width = mapObject.getType().getCollisionWidth();
		return objectTileX * GameConfig.TILE_SIZE + (GameConfig.TILE_SIZE - width) / 2;
	}

	public int objectCollisionTopWorldY(int objectTileY, MapObject mapObject) {
		int height = mapObject.getType().getCollisionHeight();
		return objectTileY * GameConfig.TILE_SIZE + (GameConfig.TILE_SIZE - height) / 2;
	}

	public boolean isProjectileBlockedAtWorld(int worldX, int worldY) {
		TileType tile = getTileAtWorld(worldX, worldY);
		if (tile == null || tile.isSolid()) {
			return true;
		}
		MapObject mapObject = getObjectAtWorld(worldX, worldY);
		return mapObject != null && mapObject.isSolid() && mapObject.getType() != MapObjectType.WOODEN_FENCE;
	}

	public boolean isWalkableAtWorld(int worldX, int worldY) {
		TileType tile = getTileAtWorld(worldX, worldY);
		if (tile == null || tile.isSolid() || tile.isHazardous()) {
			return false;
		}
		MapObject mapObject = getObjectAtWorld(worldX, worldY);
		return mapObject == null || !mapObject.isSolid();
	}

	public boolean canPlaceObject(int tileX, int tileY) {
		TileType tile = getTile(tileX, tileY);
		return tile != null && !tile.isSolid() && !tile.isHazardous() && getObject(tileX, tileY) == null;
	}

	public void placeSingleObject(MapObjectType type, int tileX, int tileY, boolean solid, boolean abovePlayer) {
		if (!isInBounds(tileX, tileY)) {
			return;
		}
		this.objectGrid[tileX][tileY] = new MapObject(type, tileX, tileY, 0, solid, abovePlayer);
		rebuildMinimap();
	}

	public void rebuildMinimap() {
		for (int tileX = 0; tileX < this.widthTiles; tileX++) {
			for (int tileY = 0; tileY < this.heightTiles; tileY++) {
				TileType tileType = TileType.fromId(this.tiles[tileX][tileY]);
				this.minimapImage.setRGB(tileX, tileY, tileType.getMinimapColor().getRGB());
				MapObject mapObject = this.objectGrid[tileX][tileY];
				if (mapObject != null) {
					Color minimapColor = mapObject.getType().resolveMinimapColor(mapObject.getDrawIndex());
					this.minimapImage.setRGB(tileX, tileY, minimapColor.getRGB());
				}
			}
		}
	}

	public void placeObjectRect(MapObjectType type, int rootTileX, int rootTileY, boolean[] solidByIndex, boolean[] aboveByIndex) {
		int drawIndex = 0;
		for (int offsetY = 0; offsetY < type.getFootprintHeight(); offsetY++) {
			for (int offsetX = 0; offsetX < type.getFootprintWidth(); offsetX++) {
				if (isInBounds(rootTileX + offsetX, rootTileY + offsetY)) {
					this.objectGrid[rootTileX + offsetX][rootTileY + offsetY] = new MapObject(type, rootTileX, rootTileY, drawIndex, solidByIndex[drawIndex], aboveByIndex[drawIndex]);
				}
				drawIndex++;
			}
		}
		rebuildMinimap();
	}

}
