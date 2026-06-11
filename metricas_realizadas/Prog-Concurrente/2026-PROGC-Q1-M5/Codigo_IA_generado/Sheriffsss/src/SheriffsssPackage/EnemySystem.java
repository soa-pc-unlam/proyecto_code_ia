package SheriffsssPackage;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EnemySystem {
	private static final int MAX_ENEMIES = 2000;
	private static final int MIN_SPAWN_DISTANCE_TILES = 13;
	private static final int MAX_SPAWN_DISTANCE_TILES = 22;
	private static final int DESPAWN_DISTANCE_TILES = 64;
	private static final int DESPAWN_DISTANCE_PIXELS = DESPAWN_DISTANCE_TILES * GameConfig.TILE_SIZE;
	private static final int DESPAWN_DISTANCE_PIXELS_SQUARED = DESPAWN_DISTANCE_PIXELS * DESPAWN_DISTANCE_PIXELS;
	private static final double ENEMY_COLLISION_EPSILON = 0.0001;
	private static final double ENEMY_COLLISION_MAX_PUSH_PIXELS = 4.0;
	private static final Color CRIT_TEXT_COLOR = new Color(255, 45, 45);

	private final ArrayList<Enemy> enemies = new ArrayList<Enemy>(MAX_ENEMIES);
	private final ArrayList<FlameBurstEffect> flameBurstEffects = new ArrayList<FlameBurstEffect>();
	private final ArrayList<CombatFloatingText> combatFloatingTexts = new ArrayList<CombatFloatingText>();
	private final ArrayList<EnemyHitSound> hitSounds = new ArrayList<EnemyHitSound>();
	private Random spawnRandom = new Random(0L);
	private Random combatRandom = new Random(0L);
	private int spawnCooldownTicks;
	private boolean autoSpawnEnabled = true;

	public void reset(int seedHash) {
		this.enemies.clear();
		this.flameBurstEffects.clear();
		this.combatFloatingTexts.clear();
		this.hitSounds.clear();
		this.spawnRandom = new Random(seedHash ^ 0x4F1BBCDC);
		this.combatRandom = new Random(seedHash ^ 0xC2B2AE35);
		this.spawnCooldownTicks = 90;
	}

	public void clear() {
		this.enemies.clear();
		this.flameBurstEffects.clear();
		this.combatFloatingTexts.clear();
		this.hitSounds.clear();
		this.spawnCooldownTicks = 0;
	}

	public void update(GameMap map, Player player, DayNightCycle cycle) {
		int dayCount = cycle.getDayCount();
		for (int i = this.enemies.size() - 1; i >= 0; i--) {
			Enemy enemy = this.enemies.get(i);
			if (enemy.isDead()) {
				this.enemies.remove(i);
				continue;
			}
			if (shouldDespawn(enemy, player)) {
				this.enemies.remove(i);
				continue;
			}
			enemy.update(map, player);
			if (enemy.isDead()) {
				this.enemies.remove(i);
			}
		}
		resolveEnemyCollisions(map);
		updateSpawns(map, player, cycle, dayCount);
		updateFlameBurstEffects();
		updateCombatFloatingTexts();
	}

	public void update(GameMap map, List<Player> players, DayNightCycle cycle) {
		if (players == null || players.isEmpty()) {
			updateFlameBurstEffects();
			updateCombatFloatingTexts();
			return;
		}
		int dayCount = cycle.getDayCount();
		for (int i = this.enemies.size() - 1; i >= 0; i--) {
			Enemy enemy = this.enemies.get(i);
			if (enemy.isDead()) {
				this.enemies.remove(i);
				continue;
			}
			if (shouldDespawn(enemy, players)) {
				this.enemies.remove(i);
				continue;
			}
			enemy.update(map, players);
			if (enemy.isDead()) {
				this.enemies.remove(i);
			}
		}
		resolveEnemyCollisions(map);
		Player spawnAnchor = chooseSpawnAnchor(players);
		if (spawnAnchor != null) {
			updateSpawns(map, spawnAnchor, cycle, dayCount);
		}
		updateFlameBurstEffects();
		updateCombatFloatingTexts();
	}

	private Player chooseSpawnAnchor(List<Player> players) {
		int livingCount = 0;
		for (int i = 0; i < players.size(); i++) {
			Player player = players.get(i);
			if (player != null && player.getCurrentHP() > 0.0) {
				livingCount++;
			}
		}
		if (livingCount <= 0) {
			return null;
		}
		int selected = this.spawnRandom.nextInt(livingCount);
		for (int i = 0; i < players.size(); i++) {
			Player player = players.get(i);
			if (player == null || player.getCurrentHP() <= 0.0) {
				continue;
			}
			if (selected == 0) {
				return player;
			}
			selected--;
		}
		return null;
	}

	private boolean shouldDespawn(Enemy enemy, Player player) {
		if (enemy == null || player == null || player.getCurrentHP() <= 0.0) {
			return false;
		}
		return distanceSquared(player.getX(), player.getFeetWorldY(), enemy.getWorldX(), enemy.getWorldY()) > DESPAWN_DISTANCE_PIXELS_SQUARED;
	}

	private boolean shouldDespawn(Enemy enemy, List<Player> players) {
		if (enemy == null || players == null || players.isEmpty()) {
			return false;
		}
		boolean hasLivingPlayer = false;
		for (int i = 0; i < players.size(); i++) {
			Player player = players.get(i);
			if (player == null || player.getCurrentHP() <= 0.0) {
				continue;
			}
			hasLivingPlayer = true;
			if (distanceSquared(player.getX(), player.getFeetWorldY(), enemy.getWorldX(), enemy.getWorldY()) <= DESPAWN_DISTANCE_PIXELS_SQUARED) {
				return false;
			}
		}
		return hasLivingPlayer;
	}

	private void resolveEnemyCollisions(GameMap map) {
		int count = this.enemies.size();
		for (int i = 0; i < count - 1; i++) {
			Enemy first = this.enemies.get(i);
			for (int j = i + 1; j < count; j++) {
				resolveEnemyCollision(map, first, this.enemies.get(j), i, j);
			}
		}
	}

	private void resolveEnemyCollision(GameMap map, Enemy first, Enemy second, int firstIndex, int secondIndex) {
		int minimumDistance = first.getType().getCollisionRadius() + second.getType().getCollisionRadius();
		double deltaX = second.getCollisionX() - first.getCollisionX();
		double deltaY = second.getCollisionY() - first.getCollisionY();
		double distanceSquared = deltaX * deltaX + deltaY * deltaY;
		double minimumDistanceSquared = minimumDistance * minimumDistance;
		if (distanceSquared >= minimumDistanceSquared) {
			return;
		}
		double distance;
		if (distanceSquared <= ENEMY_COLLISION_EPSILON) {
			deltaX = ((firstIndex + secondIndex) & 1) == 0 ? 1.0 : -1.0;
			deltaY = (firstIndex & 1) == 0 ? 0.35 : -0.35;
			distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
		} else {
			distance = Math.sqrt(distanceSquared);
		}
		double pushDistance = Math.min((minimumDistance - distance) * 0.5, ENEMY_COLLISION_MAX_PUSH_PIXELS);
		if (pushDistance <= 0.0) {
			return;
		}
		double normalX = deltaX / distance;
		double normalY = deltaY / distance;
		first.pushBy(map, -normalX * pushDistance, -normalY * pushDistance);
		second.pushBy(map, normalX * pushDistance, normalY * pushDistance);
	}

	public void damageEnemy(Enemy enemy, double amount, Player sourcePlayer, ItemDefinition weapon) {
		if (enemy == null || amount <= 0.0) {
			return;
		}
		double finalDamage = amount;
		if (isCriticalHit(sourcePlayer, weapon)) {
			finalDamage *= 2.0;
			this.combatFloatingTexts.add(new CombatFloatingText(enemy.getWorldX(), enemy.getWorldY() - enemy.getType().getDrawHeight() / 2, "CRIT", CRIT_TEXT_COLOR));
		}
		recordHitSound(enemy);
		enemy.damage(finalDamage, sourcePlayer);
	}

	private void recordHitSound(Enemy enemy) {
		String hitSoundPath = enemy.getType().getHitSoundPath();
		if (hitSoundPath == null || hitSoundPath.isEmpty()) {
			return;
		}
		this.hitSounds.add(new EnemyHitSound(hitSoundPath, enemy.getWorldX(), enemy.getWorldY()));
	}

	private boolean isCriticalHit(Player sourcePlayer, ItemDefinition weapon) {
		if (sourcePlayer == null || weapon == null || !weapon.canCrit()) {
			return false;
		}
		int critChancePercent = Math.max(0, Math.min(100, weapon.getBaseCritChancePercent()));
		return critChancePercent > 0 && this.combatRandom.nextInt(100) < critChancePercent;
	}

	public void collectDeadEnemies() {
		for (int i = this.enemies.size() - 1; i >= 0; i--) {
			Enemy enemy = this.enemies.get(i);
			if (!enemy.isDead()) {
				continue;
			}
			this.enemies.remove(i);
		}
	}

	private void updateSpawns(GameMap map, Player player, DayNightCycle cycle, int dayCount) {
		if (!this.autoSpawnEnabled) {
			return;
		}
		if (this.enemies.size() >= MAX_ENEMIES) {
			return;
		}
		if (this.spawnCooldownTicks > 0) {
			this.spawnCooldownTicks--;
			return;
		}
		spawnNearPlayer(map, player, dayCount);
		this.spawnCooldownTicks = nextSpawnCooldown(cycle, dayCount);
	}

	public void setAutoSpawnEnabled(boolean enabled) {
		this.autoSpawnEnabled = enabled;
	}

	private int nextSpawnCooldown(DayNightCycle cycle, int dayCount) {
		double dayScale = 1.0 + Math.max(0, dayCount - 1) * 0.22;
		int baseTicks = cycle.getPhase() == DayPhase.NIGHT ? 78 : 330;
		int jitter = cycle.getPhase() == DayPhase.NIGHT ? this.spawnRandom.nextInt(35) : this.spawnRandom.nextInt(120);
		return Math.max(24, (int) ((baseTicks + jitter) / dayScale));
	}

	private void spawnNearPlayer(GameMap map, Player player, int dayCount) {
		EnemyType type = chooseEnemyType(dayCount);
		if (type == null) {
			return;
		}
		int playerTileX = map.worldToTileX(player.getX());
		int playerTileY = map.worldToTileY(player.getFeetWorldY());
		for (int attempt = 0; attempt < 18; attempt++) {
			int distanceTiles = this.spawnRandom.nextInt(MIN_SPAWN_DISTANCE_TILES, MAX_SPAWN_DISTANCE_TILES + 1);
			int offsetX = this.spawnRandom.nextInt(-distanceTiles, distanceTiles + 1);
			int offsetY = this.spawnRandom.nextBoolean() ? distanceTiles : -distanceTiles;
			if (Math.abs(offsetX) < MIN_SPAWN_DISTANCE_TILES / 2) {
				offsetX += offsetX < 0 ? -MIN_SPAWN_DISTANCE_TILES / 2 : MIN_SPAWN_DISTANCE_TILES / 2;
			}
			if (this.spawnRandom.nextBoolean()) {
				int swap = offsetX;
				offsetX = offsetY;
				offsetY = swap;
			}
			int tileX = playerTileX + offsetX;
			int tileY = playerTileY + offsetY;
			int worldX = tileX * GameConfig.TILE_SIZE + GameConfig.TILE_SIZE / 2;
			int worldY = tileY * GameConfig.TILE_SIZE + GameConfig.TILE_SIZE / 2;
			if (map.isWalkableAtWorld(worldX, worldY)) {
				this.enemies.add(new Enemy(type, worldX, worldY, dayCount));
				return;
			}
		}
	}

	private EnemyType chooseEnemyType(int dayCount) {
		int totalWeight = 0;
		EnemyType[] enemyTypes = EnemyType.values();
		for (int i = 0; i < enemyTypes.length; i++) {
			EnemyType type = enemyTypes[i];
			if (dayCount >= type.getMinimumSpawnDay()) {
				totalWeight += type.getSpawnWeight();
			}
		}
		if (totalWeight <= 0) {
			return null;
		}
		int roll = this.spawnRandom.nextInt(totalWeight);
		for (int i = 0; i < enemyTypes.length; i++) {
			EnemyType type = enemyTypes[i];
			if (dayCount < type.getMinimumSpawnDay()) {
				continue;
			}
			roll -= type.getSpawnWeight();
			if (roll < 0) {
				return type;
			}
		}
		return null;
	}

	private void updateFlameBurstEffects() {
		for (int i = this.flameBurstEffects.size() - 1; i >= 0; i--) {
			FlameBurstEffect effect = this.flameBurstEffects.get(i);
			effect.update();
			if (effect.isExpired()) {
				this.flameBurstEffects.remove(i);
			}
		}
	}

	private void updateCombatFloatingTexts() {
		for (int i = this.combatFloatingTexts.size() - 1; i >= 0; i--) {
			CombatFloatingText text = this.combatFloatingTexts.get(i);
			text.update();
			if (text.isExpired()) {
				this.combatFloatingTexts.remove(i);
			}
		}
	}

	private int distanceSquared(int startX, int startY, int endX, int endY) {
		int deltaX = endX - startX;
		int deltaY = endY - startY;
		return deltaX * deltaX + deltaY * deltaY;
	}

	public List<Enemy> getEnemies() {
		return this.enemies;
	}

	public List<FlameBurstEffect> getFlameBurstEffects() {
		return this.flameBurstEffects;
	}

	public List<CombatFloatingText> getCombatFloatingTexts() {
		return this.combatFloatingTexts;
	}

	public List<EnemyHitSound> getHitSounds() {
		return this.hitSounds;
	}

	public void clearHitSounds() {
		this.hitSounds.clear();
	}
}
