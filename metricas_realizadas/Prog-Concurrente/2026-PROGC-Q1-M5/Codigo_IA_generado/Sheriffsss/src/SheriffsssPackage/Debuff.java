package SheriffsssPackage;

public enum Debuff {
	BURN(GameConfig.TARGET_FPS * 5, 3, 0.34) {
		@Override
		public void update(Enemy enemy) {
			enemy.damageFromDebuff(this, enemy.resolveDebuffDamage(this, 1.2 / GameConfig.TARGET_FPS));
		}
	};

	private final int durationTicks;
	private final int lightRadiusTiles;
	private final double lightIntensity;

	Debuff(int durationTicks) {
		this(durationTicks, 0, 0.0);
	}

	Debuff(int durationTicks, int lightRadiusTiles, double lightIntensity) {
		this.durationTicks = durationTicks;
		this.lightRadiusTiles = Math.max(0, lightRadiusTiles);
		this.lightIntensity = Math.max(0.0, lightIntensity);
	}

	public int getDurationTicks() {
		return this.durationTicks;
	}

	public int getLightRadiusTiles() {
		return this.lightRadiusTiles;
	}

	public double getLightIntensity() {
		return this.lightIntensity;
	}

	public void update(Enemy enemy) {
	}
}
