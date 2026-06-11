package SheriffsssPackage;

enum State {
	MENU, MENU_SETTINGS, PLAYING, SETTINGS, DEAD, TRAINING;
}

enum Facing {
	DOWN(0),
	LEFT(1),
	UP(2),
	RIGHT(3),
	DOWN_LEFT(4),
	UP_RIGHT(5),
	UP_LEFT(6),
	DOWN_RIGHT(7);

	private final int spriteIndex;

	Facing(int spriteIndex) {
		this.spriteIndex = spriteIndex;
	}

	public int getSpriteIndex() {
		return this.spriteIndex;
	}
}

enum CursorType {
	IDLE("sprites/crosshair.png", "Idle", 15, 15),
	SELECT("sprites/newCursorSelect.png", "Select"),
	GRABBING("sprites/newCursorGrabbing.png", "Grabbing"),
	HIT_MARKER("sprites/hit_marker.png", "Hit Marker", 15, 15);

	private final String spritePath;
	private final String cursorName;
	private final int hotspotX;
	private final int hotspotY;

	CursorType(String spritePath, String cursorName) {
		this(spritePath, cursorName, -1, -1);
	}

	CursorType(String spritePath, String cursorName, int hotspotX, int hotspotY) {
		this.spritePath = spritePath;
		this.cursorName = cursorName;
		this.hotspotX = hotspotX;
		this.hotspotY = hotspotY;
	}

	public String getSpritePath() {
		return this.spritePath;
	}

	public String getCursorName() {
		return this.cursorName;
	}

	public int getHotspotX(int imageWidth) {
		int x = this.hotspotX >= 0 ? this.hotspotX : imageWidth / 2;
		return Math.min(x, imageWidth - 1);
	}

	public int getHotspotY(int imageHeight) {
		int y = this.hotspotY >= 0 ? this.hotspotY : imageHeight / 2;
		return Math.min(y, imageHeight - 1);
	}
}
