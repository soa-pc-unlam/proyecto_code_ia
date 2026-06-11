
package SheriffsssPackage;

public class Enemy {
	private static final int JUMP_TICKS = 16;
	private static final int JUMP_REST_TICKS = 34;
	private static final double KNOCKBACK_FRICTION = 0.82;
	private static final double MIN_KNOCKBACK_SPEED = 0.08;
	private static final Debuff[] DEBUFFS = Debuff.values();

	private final EnemyType type;
	private final double maxHP;
	private final double speed;
	private final double damage;
	private final int[] debuffTicks = new int[DEBUFFS.length];
	private final double[] debuffDamageMultipliers = new double[DEBUFFS.length];
	private final Player[] debuffSourcePlayers = new Player[DEBUFFS.length];
	private double currentHP;
	private double x;
	private double y;
	private double knockbackX;
	private double knockbackY;
	private double jumpVelocityX;
	private double jumpVelocityY;
	private int jumpTicks;
	private int jumpRestTicks;
	private int attackCooldownTicks;
	private int animationTicks;
	private Facing facing = Facing.DOWN;
	private Player lastDamageSourcePlayer;
	private EnemyBehavior behaviorOverride;

	public Enemy(EnemyType type, int worldX, int worldY, int dayCount) {
		this(type, worldX, worldY, dayCount, type.getScaledMaxHP(dayCount));
	}

	public Enemy(EnemyType type, int worldX, int worldY, int dayCount, double currentHP) {
		this.type = type;
		this.x = worldX;
		this.y = worldY;
		this.maxHP = type.getScaledMaxHP(dayCount);
		this.speed = type.getScaledSpeed(dayCount);
		this.damage = type.getScaledDamage(dayCount);
		this.currentHP = Math.max(0.0, Math.min(currentHP, this.maxHP));
		this.jumpRestTicks = JUMP_REST_TICKS;
		resetDebuffDamageMultipliers();
	}

	public void restoreTransientState(int animationTicks, Facing facing) {
		this.animationTicks = Math.max(0, animationTicks);
		if (facing != null) {
			this.facing = facing;
		}
	}

	public void update(GameMap map, Player player) {
		this.animationTicks++;
		updateDebuffs();
		if (this.attackCooldownTicks > 0) {
			this.attackCooldownTicks--;
		}
		double deltaX = player.getX() - this.x;
		double deltaY = player.getFeetWorldY() - this.y;
		updateFacing(deltaX, deltaY);
		EnemyBehavior behavior = effectiveBehavior();
		if (behavior == EnemyBehavior.STATIC) {
			// STATIC: no se mueve, pero sigue dañable y puede atacar si el jugador entra al rango.
		} else if (behavior == EnemyBehavior.JUMPING) {
			updateJumpingMovement(map, deltaX, deltaY);
		} else {
			moveToward(map, deltaX, deltaY, this.speed);
		}
		applyKnockback(map);
		attackPlayerIfInRange(player);
	}

	public void setBehaviorOverride(EnemyBehavior behavior) {
		this.behaviorOverride = behavior;
	}

	public EnemyBehavior effectiveBehavior() {
		return this.behaviorOverride != null ? this.behaviorOverride : this.type.getBehavior();
	}

	public void update(GameMap map, java.util.List<Player> players) {
		Player target = nearestLivingPlayer(players);
		if (target == null) {
			this.animationTicks++;
			updateDebuffs();
			if (this.attackCooldownTicks > 0) {
				this.attackCooldownTicks--;
			}
			applyKnockback(map);
			return;
		}
		update(map, target);
	}

	private Player nearestLivingPlayer(java.util.List<Player> players) {
		Player nearest = null;
		double nearestDistanceSquared = Double.MAX_VALUE;
		for (int i = 0; i < players.size(); i++) {
			Player player = players.get(i);
			if (player == null || player.getCurrentHP() <= 0.0) {
				continue;
			}
			double deltaX = player.getX() - this.x;
			double deltaY = player.getFeetWorldY() - this.y;
			double distanceSquared = deltaX * deltaX + deltaY * deltaY;
			if (distanceSquared < nearestDistanceSquared) {
				nearest = player;
				nearestDistanceSquared = distanceSquared;
			}
		}
		return nearest;
	}

	private void updateDebuffs() {
		for (int i = 0; i < DEBUFFS.length; i++) {
			if (this.debuffTicks[i] <= 0) {
				continue;
			}
			DEBUFFS[i].update(this);
			this.debuffTicks[i]--;
			if (this.debuffTicks[i] <= 0) {
				this.debuffDamageMultipliers[i] = 1.0;
			}
		}
	}

	private void resetDebuffDamageMultipliers() {
		for (int i = 0; i < this.debuffDamageMultipliers.length; i++) {
			this.debuffDamageMultipliers[i] = 1.0;
		}
	}

	private void updateJumpingMovement(GameMap map, double deltaX, double deltaY) {
		if (this.jumpTicks > 0) {
			moveWithCollision(map, this.jumpVelocityX, this.jumpVelocityY);
			this.jumpTicks--;
			return;
		}
		if (this.jumpRestTicks > 0) {
			this.jumpRestTicks--;
			return;
		}
		double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
		if (length <= 0.001) {
			this.jumpVelocityX = 0.0;
			this.jumpVelocityY = 0.0;
		} else {
			double jumpSpeed = this.speed * 2.9;
			this.jumpVelocityX = deltaX / length * jumpSpeed;
			this.jumpVelocityY = deltaY / length * jumpSpeed;
		}
		this.jumpTicks = JUMP_TICKS;
		this.jumpRestTicks = JUMP_REST_TICKS;
	}

	private void moveToward(GameMap map, double deltaX, double deltaY, double movementSpeed) {
		double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
		if (length <= 0.001) {
			return;
		}
		moveWithCollision(map, deltaX / length * movementSpeed, deltaY / length * movementSpeed);
	}

	private void applyKnockback(GameMap map) {
		if (Math.abs(this.knockbackX) < MIN_KNOCKBACK_SPEED && Math.abs(this.knockbackY) < MIN_KNOCKBACK_SPEED) {
			this.knockbackX = 0.0;
			this.knockbackY = 0.0;
			return;
		}
		moveWithCollision(map, this.knockbackX, this.knockbackY);
		this.knockbackX *= KNOCKBACK_FRICTION;
		this.knockbackY *= KNOCKBACK_FRICTION;
	}

	private void moveWithCollision(GameMap map, double deltaX, double deltaY) {
		double nextX = this.x + deltaX;
		if (canStandAt(map, nextX, this.y)) {
			this.x = nextX;
		} else {
			this.knockbackX = 0.0;
		}
		double nextY = this.y + deltaY;
		if (canStandAt(map, this.x, nextY)) {
			this.y = nextY;
		} else {
			this.knockbackY = 0.0;
		}
	}

	private boolean canStandAt(GameMap map, double worldX, double worldY) {
		int checkX = (int) Math.round(worldX);
		int checkY = (int) Math.round(worldY);
		return map.isWalkableAtWorld(checkX, checkY)
			&& map.isWalkableAtWorld(checkX - this.type.getCollisionRadius(), checkY)
			&& map.isWalkableAtWorld(checkX + this.type.getCollisionRadius(), checkY);
	}

	void pushBy(GameMap map, double deltaX, double deltaY) {
		moveWithCollision(map, deltaX, deltaY);
	}

	private void attackPlayerIfInRange(Player player) {
		if (this.damage <= 0.0) {
			return;
		}
		double deltaX = player.getX() - this.x;
		double deltaY = player.getFeetWorldY() - this.y;
		int range = this.type.getAttackRangePixels();
		if (deltaX * deltaX + deltaY * deltaY > range * range || this.attackCooldownTicks > 0) {
			return;
		}
		player.damageEnemyAttack(this.damage);
		player.setTakingDamage(true);
		player.applyKnockbackFrom(getWorldX(), getWorldY(), this.type.getAttackKnockbackStrengthPixels());
		this.attackCooldownTicks = this.type.getAttackCooldownTicks();
	}

	private void updateFacing(double deltaX, double deltaY) {
		if (Math.abs(deltaX) > Math.abs(deltaY)) {
			this.facing = deltaX < 0.0 ? Facing.LEFT : Facing.RIGHT;
		} else if (deltaY < 0.0) {
			this.facing = Facing.UP;
		} else {
			this.facing = Facing.DOWN;
		}
	}

	public boolean containsWorldPoint(int worldX, int worldY) {
		return containsWorldPoint(worldX, worldY, 0);
	}

	public boolean containsWorldPoint(int worldX, int worldY, int paddingPixels) {
		int halfWidth = this.type.getDrawWidth() / 2;
		int halfHeight = this.type.getDrawHeight() / 2;
		int padding = Math.max(0, paddingPixels);
		return worldX >= getWorldX() - halfWidth - padding && worldX <= getWorldX() + halfWidth + padding
			&& worldY >= getWorldY() - halfHeight - padding && worldY <= getWorldY() + halfHeight + padding;
	}

	public void damage(double amount) {
		this.currentHP -= amount;
	}

	public void damage(double amount, Player sourcePlayer) {
		if (sourcePlayer != null) {
			this.lastDamageSourcePlayer = sourcePlayer;
		}
		damage(amount);
	}

	public void damageFromDebuff(Debuff debuff, double amount) {
		Player sourcePlayer = debuff == null ? null : this.debuffSourcePlayers[debuff.ordinal()];
		damage(amount, sourcePlayer);
	}

	public void applyDebuff(Debuff debuff) {
		applyDebuff(debuff, null);
	}

	public void applyDebuff(Debuff debuff, Player sourcePlayer) {
		if (debuff == null) {
			return;
		}
		int index = debuff.ordinal();
		this.debuffTicks[index] = debuff.getDurationTicks();
		this.debuffDamageMultipliers[index] = 1.0;
		this.debuffSourcePlayers[index] = sourcePlayer;
	}

	public double resolveDebuffDamage(Debuff debuff, double baseDamage) {
		if (debuff == null) {
			return baseDamage;
		}
		return baseDamage * this.debuffDamageMultipliers[debuff.ordinal()];
	}

	public void applyKnockbackFrom(int sourceX, int sourceY, double strength) {
		double deltaX = this.x - sourceX;
		double deltaY = this.y - sourceY;
		double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
		if (length <= 0.001) {
			this.knockbackX = 0.0;
			this.knockbackY = strength;
			return;
		}
		this.knockbackX = deltaX / length * strength;
		this.knockbackY = deltaY / length * strength;
	}

	public boolean isDead() {
		return this.currentHP <= 0.0;
	}

	public EnemyType getType() {
		return this.type;
	}

	public int getWorldX() {
		return (int) Math.round(this.x);
	}

	public int getWorldY() {
		return (int) Math.round(this.y);
	}

	double getCollisionX() {
		return this.x;
	}

	double getCollisionY() {
		return this.y;
	}

	public double getCurrentHP() {
		return this.currentHP;
	}

	public double getMaxHP() {
		return this.maxHP;
	}

	public int getAnimationTicks() {
		return this.animationTicks;
	}

	public boolean hasDebuff(Debuff debuff) {
		return debuff != null && this.debuffTicks[debuff.ordinal()] > 0;
	}

	public boolean hasLightEmittingDebuff() {
		for (int i = 0; i < DEBUFFS.length; i++) {
			Debuff debuff = DEBUFFS[i];
			if (this.debuffTicks[i] > 0 && debuff.getLightRadiusTiles() > 0 && debuff.getLightIntensity() > 0.0) {
				return true;
			}
		}
		return false;
	}

	public Facing getFacing() {
		return this.facing;
	}
}
