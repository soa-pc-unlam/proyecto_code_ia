package SheriffsssPackage;

final class ProjectileStatModifiers {
	static final ProjectileStatModifiers NONE = new ProjectileStatModifiers(0.0, 0.0, 0.0, 0, 0);

	private final double damage;
	private final double speedPixels;
	private final double knockbackStrengthPixels;
	private final int cooldownTicks;
	private final int lifeTicks;

	ProjectileStatModifiers(double damage, double speedPixels, double knockbackStrengthPixels, int cooldownTicks, int lifeTicks) {
		this.damage = damage;
		this.speedPixels = speedPixels;
		this.knockbackStrengthPixels = knockbackStrengthPixels;
		this.cooldownTicks = cooldownTicks;
		this.lifeTicks = lifeTicks;
	}

	double getDamage() {
		return this.damage;
	}

	double getSpeedPixels() {
		return this.speedPixels;
	}

	double getKnockbackStrengthPixels() {
		return this.knockbackStrengthPixels;
	}

	int getCooldownTicks() {
		return this.cooldownTicks;
	}

	int getLifeTicks() {
		return this.lifeTicks;
	}
}
