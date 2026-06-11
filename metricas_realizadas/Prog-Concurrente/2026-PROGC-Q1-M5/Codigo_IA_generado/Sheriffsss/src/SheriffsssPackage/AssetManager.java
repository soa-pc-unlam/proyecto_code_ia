package SheriffsssPackage;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

public class AssetManager {
	private final Map<String, BufferedImage> imageCache = new HashMap<String, BufferedImage>();
	private final EnumMap<CursorType, Cursor> cursorCache = new EnumMap<CursorType, Cursor>(CursorType.class);

	public AssetManager() {
		preload();
	}

	public BufferedImage getImage(String path) {
		BufferedImage image = this.imageCache.get(path);
		if (image != null) {
			return image;
		}
		BufferedImage loadedImage = loadImage(path);
		this.imageCache.put(path, loadedImage);
		return loadedImage;
	}

	public Cursor getCursor(CursorType cursorType) {
		Cursor cursor = this.cursorCache.get(cursorType);
		if (cursor != null) {
			return cursor;
		}
		BufferedImage cursorImage = getImage(cursorType.getSpritePath());
		Cursor createdCursor = Toolkit.getDefaultToolkit().createCustomCursor(cursorImage,
			new Point(cursorType.getHotspotX(cursorImage.getWidth()), cursorType.getHotspotY(cursorImage.getHeight())),
			cursorType.getCursorName());
		this.cursorCache.put(cursorType, createdCursor);
		return createdCursor;
	}

	private void preload() {
		for (TileType tileType : TileType.values()) {
			for (String spritePath : tileType.getSpritePaths()) {
				getImage(spritePath);
			}
		}
		getImage("sprites/sheriffsss_icono.png");
		for (MapObjectType objectType : MapObjectType.values()) {
			getImage(objectType.getSpritePath());
		}
		for (ItemDefinition itemDefinition : ItemDefinition.values()) {
			getImage(itemDefinition.getSpritePath());
		}
		for (EnemyType enemyType : EnemyType.values()) {
			getImage(enemyType.getSpritePath());
		}
		for (CursorType cursorType : CursorType.values()) {
			getImage(cursorType.getSpritePath());
		}
		getImage("sprites/sheriffsss-player-sprites/1/sheriff-south.png");
		getImage("sprites/sheriffsss-player-sprites/1/sheriff-west.png");
		getImage("sprites/sheriffsss-player-sprites/1/sheriff-north.png");
		getImage("sprites/sheriffsss-player-sprites/1/sheriff-east.png");
		getImage("sprites/sheriffsss-player-sprites/1/sheriff-south-west.png");
		getImage("sprites/sheriffsss-player-sprites/1/sheriff-north-east.png");
		getImage("sprites/sheriffsss-player-sprites/1/sheriff-north-west.png");
		getImage("sprites/sheriffsss-player-sprites/1/sheriff-south-east.png");
	}

	private BufferedImage loadImage(String path) {
		URL resource = getClass().getClassLoader().getResource(path);
		if (resource == null) {
			throw new IllegalStateException("Missing image resource: " + path);
		}
		try {
			return ImageIO.read(resource);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load image resource: " + path, e);
		}
	}
}
