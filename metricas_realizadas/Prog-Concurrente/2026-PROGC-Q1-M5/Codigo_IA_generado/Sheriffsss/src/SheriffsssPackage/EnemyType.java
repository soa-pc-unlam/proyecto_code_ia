package SheriffsssPackage;

public enum EnemyType {
	DIANA(
		"sprites/Diana.png",
		EnemyBehavior.STATIC,
		64,
		64,
		1,
		1,
		36,
		36,
		12,
		1.0,
		0.0,
		0.0,
		0.0,
		0,
		1,
		0,
		1,
		EnemyDensity.LOW,
		"sounds/diana_clang.wav"
	);

	private final String spritePath;
	private final EnemyBehavior behavior;
	private final int frameWidth;
	private final int frameHeight;
	private final int framesPerRow;
	private final int rowCount;
	private final int drawWidth;
	private final int drawHeight;
	private final int collisionRadius;
	private final double baseMaxHP;
	private final double baseSpeed;
	private final double baseDamage;
	private final double attackKnockbackStrengthPixels;
	private final int attackRangePixels;
	private final int attackCooldownTicks;
	private final int spawnWeight;
	private final int minimumSpawnDay;
	private final EnemyDensity density;
	private final String hitSoundPath;

	EnemyType(String spritePath, EnemyBehavior behavior, int frameWidth, int frameHeight, int framesPerRow, int rowCount,
		int drawWidth, int drawHeight, int collisionRadius, double baseMaxHP, double baseSpeed, double baseDamage,
		double attackKnockbackStrengthPixels, int attackRangePixels,
		int attackCooldownTicks, int spawnWeight, int minimumSpawnDay, EnemyDensity density, String hitSoundPath) {
		this.spritePath = spritePath;
		this.behavior = behavior;
		this.frameWidth = frameWidth;
		this.frameHeight = frameHeight;
		this.framesPerRow = framesPerRow;
		this.rowCount = rowCount;
		this.drawWidth = drawWidth;
		this.drawHeight = drawHeight;
		this.collisionRadius = collisionRadius;
		this.baseMaxHP = baseMaxHP;
		this.baseSpeed = baseSpeed;
		this.baseDamage = baseDamage;
		this.attackKnockbackStrengthPixels = attackKnockbackStrengthPixels;
		this.attackRangePixels = attackRangePixels;
		this.attackCooldownTicks = attackCooldownTicks;
		this.spawnWeight = spawnWeight;
		this.minimumSpawnDay = minimumSpawnDay;
		this.density = density;
		this.hitSoundPath = hitSoundPath == null ? "" : hitSoundPath;
	}

	public String getSpritePath() {
		return this.spritePath;
	}

	public EnemyBehavior getBehavior() {
		return this.behavior;
	}

	public int getFrameWidth() {
		return this.frameWidth;
	}

	public int getFrameHeight() {
		return this.frameHeight;
	}

	public int getFramesPerRow() {
		return this.framesPerRow;
	}

	public int getDrawWidth() {
		return this.drawWidth;
	}

	public int getDrawHeight() {
		return this.drawHeight;
	}

	public int getCollisionRadius() {
		return this.collisionRadius;
	}

	public double getScaledMaxHP(int dayCount) {
		return this.baseMaxHP * (1.0 + Math.max(0, dayCount - 1) * 0.16);
	}

	public double getScaledSpeed(int dayCount) {
		return this.baseSpeed * (1.0 + Math.max(0, dayCount - 1) * 0.055);
	}

	public double getScaledDamage(int dayCount) {
		return this.baseDamage * (1.0 + Math.max(0, dayCount - 1) * 0.13);
	}

	public double getAttackKnockbackStrengthPixels() {
		return this.attackKnockbackStrengthPixels;
	}

	public int getAttackRangePixels() {
		return this.attackRangePixels;
	}

	public int getAttackCooldownTicks() {
		return this.attackCooldownTicks;
	}

	public int getSpawnWeight() {
		return this.spawnWeight;
	}

	public int getMinimumSpawnDay() {
		return this.minimumSpawnDay;
	}

	public EnemyDensity getDensity() {
		return this.density;
	}

	public String getHitSoundPath() {
		return this.hitSoundPath;
	}

	public int getAnimationRow(Facing facing) {
		if (this.rowCount <= 1) {
			return 0;
		}
		if (facing == Facing.DOWN) {
			return 0;
		}
		if (facing == Facing.LEFT) {
			return 1;
		}
		if (facing == Facing.RIGHT) {
			return 2;
		}
		return 3;
	}
}
