package SheriffsssPackage;

public final class EnemyHitSound {
	private final String resourcePath;
	private final int worldX;
	private final int worldY;

	public EnemyHitSound(String resourcePath, int worldX, int worldY) {
		this.resourcePath = resourcePath == null ? "" : resourcePath;
		this.worldX = worldX;
		this.worldY = worldY;
	}

	public String getResourcePath() {
		return this.resourcePath;
	}

	public int getWorldX() {
		return this.worldX;
	}

	public int getWorldY() {
		return this.worldY;
	}
}
