package SheriffsssPackage;

public final class FlameBurstEffect {
	private static final int DEFAULT_LIFE_TICKS = 34;
	private static final int PARTICLE_COUNT = 26;

	private final int originWorldX;
	private final int originWorldY;
	private final int radiusPixels;
	private int ageTicks;

	public FlameBurstEffect(int originWorldX, int originWorldY, int radiusPixels) {
		this(originWorldX, originWorldY, radiusPixels, 0);
	}

	public FlameBurstEffect(int originWorldX, int originWorldY, int radiusPixels, int ageTicks) {
		this.originWorldX = originWorldX;
		this.originWorldY = originWorldY;
		this.radiusPixels = Math.max(1, radiusPixels);
		this.ageTicks = Math.max(0, Math.min(ageTicks, DEFAULT_LIFE_TICKS));
	}

	public void update() {
		this.ageTicks++;
	}

	public boolean isExpired() {
		return this.ageTicks >= DEFAULT_LIFE_TICKS;
	}

	public int getOriginWorldX() {
		return this.originWorldX;
	}

	public int getOriginWorldY() {
		return this.originWorldY;
	}

	public int getRadiusPixels() {
		return this.radiusPixels;
	}

	public int getAgeTicks() {
		return this.ageTicks;
	}

	public int getLifeTicks() {
		return DEFAULT_LIFE_TICKS;
	}

	public int getParticleCount() {
		return PARTICLE_COUNT;
	}

	public double getParticleAngleRadians(int index) {
		return index * (Math.PI * 2.0 / PARTICLE_COUNT) + (index % 5) * 0.17;
	}

	public double getParticleDistanceScale(int index) {
		return 0.68 + (index % 7) * 0.055;
	}
}
