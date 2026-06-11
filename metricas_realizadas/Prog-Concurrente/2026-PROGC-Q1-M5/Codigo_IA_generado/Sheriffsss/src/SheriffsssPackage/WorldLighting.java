package SheriffsssPackage;

import java.util.List;

public class WorldLighting {
	public static final int MAX_DARKNESS_ALPHA = 225;
	private static final Debuff[] DEBUFFS = Debuff.values();

	public int resolveDarknessAlpha(GameMap map, int tileX, int tileY, DayNightCycle cycle) {
		double light = Math.max(cycle.getAmbientLight(), resolveLocalLight(map, tileX, tileY));
		light = Math.max(0.0, Math.min(1.0, light));
		return (int) ((1.0 - light) * MAX_DARKNESS_ALPHA);
	}

	public int resolveDarknessAlpha(GameMap map, Player player, int tileX, int tileY, DayNightCycle cycle) {
		double light = Math.max(cycle.getAmbientLight(), resolveLocalLight(map, tileX, tileY));
		light = Math.max(light, resolveHeldLight(map, player, tileX, tileY));
		light = Math.max(0.0, Math.min(1.0, light));
		return (int) ((1.0 - light) * MAX_DARKNESS_ALPHA);
	}

	public double resolveDynamicLight(GameMap map, int sourceWorldX, int sourceWorldY, int tileX, int tileY, int radiusTiles, double intensity) {
		if (map == null) {
			return 0.0;
		}
		int sourceTileX = map.worldToTileX(sourceWorldX);
		int sourceTileY = map.worldToTileY(sourceWorldY);
		return contribution(radiusTiles, intensity, tileX - sourceTileX, tileY - sourceTileY);
	}

	public boolean hasEnemyDebuffLights(List<Enemy> enemies) {
		if (enemies == null) {
			return false;
		}
		for (int i = 0; i < enemies.size(); i++) {
			Enemy enemy = enemies.get(i);
			if (enemy != null && !enemy.isDead() && enemy.hasLightEmittingDebuff()) {
				return true;
			}
		}
		return false;
	}

	public double resolveEnemyDebuffLight(GameMap map, List<Enemy> enemies, int tileX, int tileY) {
		if (map == null || enemies == null) {
			return 0.0;
		}
		double bestLight = 0.0;
		int tileCenterWorldX = tileX * GameConfig.TILE_SIZE + GameConfig.TILE_SIZE / 2;
		int tileCenterWorldY = tileY * GameConfig.TILE_SIZE + GameConfig.TILE_SIZE / 2;
		for (int i = 0; i < enemies.size(); i++) {
			Enemy enemy = enemies.get(i);
			if (enemy == null || enemy.isDead() || !enemy.hasLightEmittingDebuff()) {
				continue;
			}
			int deltaX = tileCenterWorldX - enemy.getWorldX();
			int deltaY = tileCenterWorldY - enemy.getWorldY();
			for (int debuffIndex = 0; debuffIndex < DEBUFFS.length; debuffIndex++) {
				Debuff debuff = DEBUFFS[debuffIndex];
				if (!enemy.hasDebuff(debuff)) {
					continue;
				}
				bestLight = Math.max(bestLight, contributionPixels(debuff.getLightRadiusTiles() * GameConfig.TILE_SIZE, debuff.getLightIntensity(), deltaX, deltaY));
				if (bestLight >= 1.0) {
					return 1.0;
				}
			}
		}
		return bestLight;
	}

	private double resolveHeldLight(GameMap map, Player player, int tileX, int tileY) {
		if (player == null) {
			return 0.0;
		}
		ItemDefinition definition = player.getEquipment().getEquippedWeapon();
		if (definition == null) {
			return 0.0;
		}
		int radius = definition.getLightRadiusTiles();
		if (radius <= 0) {
			return 0.0;
		}
		double intensity = definition.getLightIntensity();
		int sourceX = map.worldToTileX(player.getX());
		int sourceY = map.worldToTileY(player.getFeetWorldY());
		return contribution(radius, intensity, tileX - sourceX, tileY - sourceY);
	}

	private double resolveLocalLight(GameMap map, int tileX, int tileY) {
		double bestLight = 0.0;
		int radius = GameConfig.MAX_LIGHT_RADIUS_TILES;
		for (int sourceX = tileX - radius; sourceX <= tileX + radius; sourceX++) {
			for (int sourceY = tileY - radius; sourceY <= tileY + radius; sourceY++) {
				if (!map.isInBounds(sourceX, sourceY)) {
					continue;
				}
				TileType tileType = map.getTile(sourceX, sourceY);
				bestLight = Math.max(bestLight, contribution(tileType.getLightRadiusTiles(), tileType.getLightIntensity(), tileX - sourceX, tileY - sourceY));

				MapObject mapObject = map.getObject(sourceX, sourceY);
				if (mapObject != null) {
					MapObjectType objectType = mapObject.getType();
					bestLight = Math.max(bestLight, contribution(objectType.getLightRadiusTiles(), objectType.getLightIntensity(), tileX - sourceX, tileY - sourceY));
				}
				if (bestLight >= 1.0) {
					return 1.0;
				}
			}
		}
		return bestLight;
	}

	private double contribution(int radiusTiles, double intensity, int deltaX, int deltaY) {
		if (radiusTiles <= 0 || intensity <= 0.0) {
			return 0.0;
		}
		int distanceSquared = deltaX * deltaX + deltaY * deltaY;
		int radiusSquared = radiusTiles * radiusTiles;
		if (distanceSquared > radiusSquared) {
			return 0.0;
		}
		return intensity * (1.0 - distanceSquared / (double) radiusSquared);
	}

	private double contributionPixels(int radiusPixels, double intensity, int deltaX, int deltaY) {
		if (radiusPixels <= 0 || intensity <= 0.0) {
			return 0.0;
		}
		int distanceSquared = deltaX * deltaX + deltaY * deltaY;
		int radiusSquared = radiusPixels * radiusPixels;
		if (distanceSquared > radiusSquared) {
			return 0.0;
		}
		return intensity * (1.0 - distanceSquared / (double) radiusSquared);
	}
}
