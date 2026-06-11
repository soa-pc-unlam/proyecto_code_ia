package SheriffsssPackage;

public enum ProjectileType {
	BULLET("sprites/commonBullet.png", 14, 6, false, true),
	NAIL("sprites/Nail.png", 14, 5, true, false),
	FIRE_BULLET("sprites/FireBullet.png", 14, 22, false, true, 2, 0.72, 2.7, Math.PI / 2.0);

	private final String spritePath;
	private final int drawWidth;
	private final int drawHeight;
	private final boolean piercesLowDensity;
	private final boolean triggersMuzzleFlash;
	private final int lightRadiusTiles;
	private final double lightIntensity;
	private final double lightFalloffExponent;
	private final double drawAngleOffsetRadians;

	ProjectileType(String spritePath, int drawWidth, int drawHeight, boolean piercesLowDensity, boolean triggersMuzzleFlash) {
		this(spritePath, drawWidth, drawHeight, piercesLowDensity, triggersMuzzleFlash, 0, 0.0);
	}

	ProjectileType(String spritePath, int drawWidth, int drawHeight, boolean piercesLowDensity, boolean triggersMuzzleFlash,
		int lightRadiusTiles, double lightIntensity) {
		this(spritePath, drawWidth, drawHeight, piercesLowDensity, triggersMuzzleFlash, lightRadiusTiles, lightIntensity, 1.0, 0.0);
	}

	ProjectileType(String spritePath, int drawWidth, int drawHeight, boolean piercesLowDensity, boolean triggersMuzzleFlash,
		int lightRadiusTiles, double lightIntensity, double drawAngleOffsetRadians) {
		this(spritePath, drawWidth, drawHeight, piercesLowDensity, triggersMuzzleFlash, lightRadiusTiles, lightIntensity, 1.0, drawAngleOffsetRadians);
	}

	ProjectileType(String spritePath, int drawWidth, int drawHeight, boolean piercesLowDensity, boolean triggersMuzzleFlash,
		int lightRadiusTiles, double lightIntensity, double lightFalloffExponent, double drawAngleOffsetRadians) {
		this.spritePath = spritePath;
		this.drawWidth = drawWidth;
		this.drawHeight = drawHeight;
		this.piercesLowDensity = piercesLowDensity;
		this.triggersMuzzleFlash = triggersMuzzleFlash;
		this.lightRadiusTiles = lightRadiusTiles;
		this.lightIntensity = lightIntensity;
		this.lightFalloffExponent = Math.max(1.0, lightFalloffExponent);
		this.drawAngleOffsetRadians = drawAngleOffsetRadians;
	}

	public String getSpritePath() {
		return this.spritePath;
	}

	public int getDrawWidth() {
		return this.drawWidth;
	}

	public int getDrawHeight() {
		return this.drawHeight;
	}

	public boolean piercesLowDensity() {
		return this.piercesLowDensity;
	}

	public boolean triggersMuzzleFlash() {
		return this.triggersMuzzleFlash;
	}

	public int getLightRadiusTiles() {
		return this.lightRadiusTiles;
	}

	public double getLightIntensity() {
		return this.lightIntensity;
	}

	public double getLightFalloffExponent() {
		return this.lightFalloffExponent;
	}

	public double getDrawAngleOffsetRadians() {
		return this.drawAngleOffsetRadians;
	}
}
