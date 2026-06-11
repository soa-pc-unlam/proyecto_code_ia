package SheriffsssPackage;

import java.util.ArrayList;
import java.util.List;

public class ProjectileSystem {
	private static final int MAX_PROJECTILES = 180;

	private final ArrayList<Projectile> projectiles = new ArrayList<Projectile>(MAX_PROJECTILES);
	private boolean hitTargetThisUpdate;

	public boolean spawn(ProjectileType type, int startWorldX, int startWorldY, int targetWorldX, int targetWorldY,
		double speedPixels, double damage, double knockbackStrengthPixels, int lifeTicks) {
		return spawn(type, null, startWorldX, startWorldY, targetWorldX, targetWorldY, speedPixels, damage, knockbackStrengthPixels, lifeTicks);
	}

	public boolean spawn(ProjectileType type, Player owner, int startWorldX, int startWorldY, int targetWorldX, int targetWorldY,
		double speedPixels, double damage, double knockbackStrengthPixels, int lifeTicks) {
		return spawn(type, owner, null, startWorldX, startWorldY, targetWorldX, targetWorldY, speedPixels, damage, knockbackStrengthPixels, lifeTicks);
	}

	public boolean spawn(ProjectileType type, Player owner, ItemDefinition weapon, int startWorldX, int startWorldY, int targetWorldX, int targetWorldY,
		double speedPixels, double damage, double knockbackStrengthPixels, int lifeTicks) {
		if (type == null || this.projectiles.size() >= MAX_PROJECTILES) {
			return false;
		}
		double deltaX = targetWorldX - startWorldX;
		double deltaY = targetWorldY - startWorldY;
		double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
		if (length <= 0.001) {
			deltaX = 1.0;
			deltaY = 0.0;
			length = 1.0;
		}
		double velocityX = deltaX / length * speedPixels;
		double velocityY = deltaY / length * speedPixels;
		this.projectiles.add(new Projectile(type, owner, weapon, startWorldX, startWorldY, velocityX, velocityY, damage, knockbackStrengthPixels, lifeTicks));
		return true;
	}

	public void add(Projectile projectile) {
		if (projectile != null && this.projectiles.size() < MAX_PROJECTILES) {
			this.projectiles.add(projectile);
		}
	}

	public void update(GameMap map, EnemySystem enemySystem) {
		this.hitTargetThisUpdate = false;
		for (int i = this.projectiles.size() - 1; i >= 0; i--) {
			Projectile projectile = this.projectiles.get(i);
			projectile.update(map, enemySystem);
			if (projectile.consumeHitTarget()) {
				this.hitTargetThisUpdate = true;
			}
			if (!projectile.isActive()) {
				this.projectiles.remove(i);
			}
		}
	}

	public void clear() {
		this.projectiles.clear();
	}

	public List<Projectile> getProjectiles() {
		return this.projectiles;
	}

	public boolean didHitTargetThisUpdate() {
		return this.hitTargetThisUpdate;
	}
}
