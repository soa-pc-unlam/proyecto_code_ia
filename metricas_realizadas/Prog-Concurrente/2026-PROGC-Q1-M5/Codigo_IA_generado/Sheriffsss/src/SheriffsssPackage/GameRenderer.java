package SheriffsssPackage;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

public class GameRenderer {
	private static final TextRenderer.Style MENU_TEXT_STYLE = TextRenderer.Style.OUTLINED;

	private final AssetManager assets;
	private final MenuRenderer menuRenderer;
	private final Camera camera = new Camera();
	private final WorldLighting lighting = new WorldLighting();
	private final Color[] darknessColors = new Color[WorldLighting.MAX_DARKNESS_ALPHA + 1];
	private final Color[] sunsetTintColors = new Color[69];
	private final Color[] flameBurstOuterColors = new Color[256];
	private final Color[] flameBurstCoreColors = new Color[256];
	private final Color fireYellow = new Color(255, 218, 72, 210);
	private final Color fireOrange = new Color(255, 96, 18, 210);
	private final Color deathWhite = new Color(245, 245, 245, 95);
	private final Color deathBlack = new Color(0, 0, 0, 155);
	private final Composite[] pickupTextComposites = new Composite[256];
	private final Font settingsTitleFont = new Font("Arial", Font.BOLD, 38);
	private final Font settingsFont = new Font("Arial", Font.BOLD, 18);
	private final Font settingsButtonFont = new Font("Arial", Font.BOLD, 20);
	private final Font deathFont = new Font("Arial", Font.BOLD, 128);
	private final Font playerNameFont = new Font("Arial", Font.BOLD, 14);
	private final Font pickupTextFont = new Font("Arial", Font.BOLD, 16);
	private final Font infoMessageFont = new Font("Arial", Font.BOLD, 15);
	private final Font debugTitleFont = new Font("Arial", Font.BOLD, 16);
	private final Font debugFont = new Font("Arial", Font.BOLD, 13);
	private final Color debugPanelBg = new Color(0, 0, 0, 175);
	private final Color debugPanelBorder = new Color(255, 230, 120, 220);
	private final Color debugHitboxColor = new Color(255, 80, 80, 210);
	private final Color debugSpritePerimeterColor = new Color(190, 120, 255, 230);
	private final Color debugLineColor = new Color(90, 220, 255, 210);
	private final Color debugOriginColor = new Color(255, 230, 80, 230);
	private final Color debugWeaponOriginColor = new Color(120, 255, 190, 235);
	private final Color debugWeaponGripAnchorColor = new Color(255, 140, 230, 235);
	private final Color debugWeaponBarrelAnchorColor = new Color(140, 190, 255, 235);
	private final Color debugBulletTrajectoryColor = new Color(255, 255, 255, 170);
	private final Color debugTrainingFailurePerimeterColor = new Color(255, 70, 70, 220);
	private final Color debugFullConeColor = new Color(255, 140, 60, 115);
	private final Color debugFullConeOutlineColor = new Color(255, 140, 60, 230);
	private final Color debugWeaponConeColor = new Color(80, 255, 140, 140);
	private final Color debugWeaponConeOutlineColor = new Color(80, 255, 140, 230);
	private final Color debugSwitchOnColor = new Color(70, 210, 110, 230);
	private final Color debugSwitchOffColor = new Color(80, 80, 80, 210);
	private final java.awt.Stroke debugStroke = new BasicStroke(2f);
	private final Path2D.Double debugConePath = new Path2D.Double();
	private final Line2D.Double debugLine = new Line2D.Double();

	public GameRenderer(AssetManager assets, MenuRenderer menuRenderer) {
		this.assets = assets;
		this.menuRenderer = menuRenderer;
	}

	public void render(Graphics2D g2, Game game) {
		if (game.getState() == State.MENU || game.getState() == State.MENU_SETTINGS) {
			this.menuRenderer.draw(g2, game);
			return;
		}
		renderWorld(g2, game);
		if (game.isDeathOverlayActive()) {
			drawDeathOverlay(g2, game);
		}
		drawInfoMessages(g2, game);
		if (game.isTrainingActive() && game.getTrainingMode() != null) {
			game.getTrainingMode().renderHud(g2);
		}
		drawEquipment(g2, game);
		if (game.getState() == State.SETTINGS) {
			drawSettingsOverlay(g2, game);
		}
		drawDebugMenu(g2, game);
	}

	private void renderWorld(Graphics2D g2, Game game) {
		GameMap map = game.getMap();
		Player player = game.getPlayer();
		this.camera.update(game.getCameraCenterWorldX(), game.getCameraCenterWorldY());
		AffineTransform previousTransform = g2.getTransform();
		double cameraZoom = game.getCameraZoom();
		if (cameraZoom != GameConfig.CAMERA_MIN_ZOOM) {
			g2.translate(GameConfig.SCREEN_CENTER_X, GameConfig.SCREEN_CENTER_Y);
			g2.scale(cameraZoom, cameraZoom);
			g2.translate(-GameConfig.SCREEN_CENTER_X, -GameConfig.SCREEN_CENTER_Y);
		}

		for (int tileX = this.camera.getStartTileX(); tileX <= this.camera.getEndTileX(); tileX++) {
			for (int tileY = this.camera.getStartTileY(); tileY <= this.camera.getEndTileY(); tileY++) {
				int screenX = this.camera.tileToScreenX(tileX);
				int screenY = this.camera.tileToScreenY(tileY);
				TileType tileType = map.getTile(tileX, tileY);
				BufferedImage tileSprite;
				if (tileType == null) {
					tileSprite = game.isTrainingActive()
						? TileType.SAND.getSprite(this.assets, game.getFrameCount())
						: this.assets.getImage("sprites/Pasto.png");
				} else {
					tileSprite = tileType.getSprite(this.assets, game.getFrameCount());
				}
				g2.drawImage(tileSprite, screenX, screenY, GameConfig.TILE_SIZE, GameConfig.TILE_SIZE, null);
			}
		}

		drawGroundObjects(g2, map);
		drawEnemies(g2, game);
		drawProjectiles(g2, game);
		drawFlameBurstEffects(g2, game);
		
		Facing facing = player.getFacing();

		boolean heldBehindPlayer = !player.isDead() && isHeldItemRendered(game, player)
			&& heldItemDrawConfig(player).isDrawnBehind(facing);
		if (heldBehindPlayer) {
			drawHeldItems(g2, game, player);
			drawPlayers(g2, game, player);
		} else {
			drawPlayers(g2, game, player);
			drawHeldItems(g2, game, player);
		}
		if (!game.isSpectating() && !player.isDead()) {
			drawMapObjectOverlay(g2, map, player);
		}
		drawSunsetTint(g2, game);
		drawLightingOverlay(g2, game);
		drawCombatFloatingTexts(g2, game);
		drawToolTargetBar(g2, game);
		drawDebugWorld(g2, game);
		g2.setTransform(previousTransform);
		drawDebugAndUi(g2, game);
	}

	private void drawSunsetTint(Graphics2D g2, Game game) {
		int alpha = game.getDayNightCycle().getSunsetTintAlpha();
		if (alpha <= 0) {
			return;
		}
		g2.setColor(getSunsetTintColor(alpha));
		g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);
	}

	private void drawLightingOverlay(Graphics2D g2, Game game) {
		DayNightCycle cycle = game.getDayNightCycle();
		if (cycle.isNaturallyBright()) {
			return;
		}
		GameMap map = game.getMap();
		java.util.List<Enemy> enemies = game.getEnemies();
		boolean hasEnemyDebuffLights = this.lighting.hasEnemyDebuffLights(enemies);
		java.util.List<Projectile> projectiles = game.getProjectiles();
		for (int tileX = this.camera.getStartTileX(); tileX <= this.camera.getEndTileX(); tileX++) {
			for (int tileY = this.camera.getStartTileY(); tileY <= this.camera.getEndTileY(); tileY++) {
				int alpha = game.isSpectating() || game.getPlayer().isDead()
					? this.lighting.resolveDarknessAlpha(map, tileX, tileY, cycle)
					: this.lighting.resolveDarknessAlpha(map, game.getPlayer(), tileX, tileY, cycle);
				alpha = applyRevolverFlash(game, map, tileX, tileY, alpha);
				alpha = applyProjectileLights(projectiles, map, tileX, tileY, alpha);
				alpha = applyEnemyDebuffLights(enemies, map, tileX, tileY, alpha, hasEnemyDebuffLights);
				if (alpha <= 0) {
					continue;
				}
				g2.setColor(getDarknessColor(alpha));
				g2.fillRect(this.camera.tileToScreenX(tileX), this.camera.tileToScreenY(tileY), GameConfig.TILE_SIZE, GameConfig.TILE_SIZE);
			}
		}
	}

	private int applyProjectileLights(java.util.List<Projectile> projectiles, GameMap map, int tileX, int tileY, int alpha) {
		if (alpha <= 0 || projectiles == null || projectiles.isEmpty()) {
			return alpha;
		}
		double bestLight = 0.0;
		for (int i = 0; i < projectiles.size(); i++) {
			Projectile projectile = projectiles.get(i);
			ProjectileType type = projectile.getType();
			if (type.getLightRadiusTiles() <= 0 || type.getLightIntensity() <= 0.0) {
				continue;
			}
			double light = this.lighting.resolveDynamicLight(map, projectile.getWorldX(), projectile.getWorldY(),
				tileX, tileY, type.getLightRadiusTiles(), type.getLightIntensity());
			if (type.getLightFalloffExponent() > 1.0) {
				light = Math.pow(light, type.getLightFalloffExponent());
			}
			bestLight = Math.max(bestLight, light);
			if (bestLight >= 1.0) {
				return 0;
			}
		}
		return bestLight <= 0.0 ? alpha : Math.max(0, alpha - (int) (bestLight * WorldLighting.MAX_DARKNESS_ALPHA));
	}

	private int applyEnemyDebuffLights(java.util.List<Enemy> enemies, GameMap map, int tileX, int tileY, int alpha, boolean hasEnemyDebuffLights) {
		if (!hasEnemyDebuffLights || alpha <= 0) {
			return alpha;
		}
		double light = this.lighting.resolveEnemyDebuffLight(map, enemies, tileX, tileY);
		if (light <= 0.0) {
			return alpha;
		}
		return Math.max(0, alpha - (int) (light * WorldLighting.MAX_DARKNESS_ALPHA));
	}

	private int applyRevolverFlash(Game game, GameMap map, int tileX, int tileY, int alpha) {
		if (game.getRevolverFlashTicks() <= 0 || alpha <= 0) {
			return alpha;
		}
		double light = this.lighting.resolveDynamicLight(map, game.getRevolverFlashWorldX(), game.getRevolverFlashWorldY(),
			tileX, tileY, game.getRevolverFlashRadiusTiles(), game.getRevolverFlashIntensity());
		if (light <= 0.0) {
			return alpha;
		}
		return Math.max(0, alpha - (int) (light * WorldLighting.MAX_DARKNESS_ALPHA));
	}

	private Color getDarknessColor(int alpha) {
		Color color = this.darknessColors[alpha];
		if (color == null) {
			color = new Color(0, 0, 0, alpha);
			this.darknessColors[alpha] = color;
		}
		return color;
	}

	private Color getSunsetTintColor(int alpha) {
		Color color = this.sunsetTintColors[alpha];
		if (color == null) {
			color = new Color(255, 122, 28, alpha);
			this.sunsetTintColors[alpha] = color;
		}
		return color;
	}

	private void drawGroundObjects(Graphics2D g2, GameMap map) {
		int scanStartX = this.camera.getStartTileX() - MapObjectType.getMaxFootprintWidth() + 1;
		int scanEndX = this.camera.getEndTileX();
		int scanStartY = this.camera.getStartTileY() - MapObjectType.getMaxFootprintHeight() + 1;
		int scanEndY = this.camera.getEndTileY();
		for (int tileX = scanStartX; tileX <= scanEndX; tileX++) {
			for (int tileY = scanStartY; tileY <= scanEndY; tileY++) {
				MapObject mapObject = map.getObject(tileX, tileY);
				if (mapObject != null && mapObject.isRootCell(tileX, tileY) && intersectsVisibleArea(mapObject)) {
					drawMapObject(g2, mapObject);
				}
			}
		}
	}

	private void drawPlayer(Graphics2D g2, Player player) {
		drawPlayerAt(g2, player, GameConfig.SCREEN_CENTER_X - Player.PLAYER_WIDTH / 2, GameConfig.SCREEN_CENTER_Y - Player.PLAYER_HEIGHT / 2);
	}

	private void drawPlayers(Graphics2D g2, Game game, Player localPlayer) {
		if (!localPlayer.isDead()) {
			drawPlayer(g2, localPlayer);
		}
	}

	private void drawPlayerAt(Graphics2D g2, Player player, int drawX, int drawY) {
		BufferedImage sprite = player.getCurrentImage();
		int[] drawSize = fitPlayerSpriteSize(sprite);
		int spriteX = drawX + (Player.PLAYER_WIDTH - drawSize[0]) / 2;
		int spriteY = drawY + (Player.PLAYER_HEIGHT - drawSize[1]) / 2;
		g2.drawImage(sprite, spriteX, spriteY, drawSize[0], drawSize[1], null);
	}

	private int[] fitPlayerSpriteSize(BufferedImage sprite) {
		double scale = Math.min(Player.PLAYER_WIDTH / (double) sprite.getWidth(), Player.PLAYER_HEIGHT / (double) sprite.getHeight());
		return new int[] {
			Math.max(1, (int) Math.round(sprite.getWidth() * scale)),
			Math.max(1, (int) Math.round(sprite.getHeight() * scale))
		};
	}

	private void drawCombatFloatingTexts(Graphics2D g2, Game game) {
		java.util.List<CombatFloatingText> combatFloatingTexts = game.getCombatFloatingTexts();
		Composite previousComposite = g2.getComposite();
		for (int i = 0; i < combatFloatingTexts.size(); i++) {
			CombatFloatingText text = combatFloatingTexts.get(i);
			int alpha = text.getAlpha();
			if (alpha <= 0) {
				continue;
			}
			int centerX = this.camera.worldToScreenX(text.getWorldX());
			int centerY = this.camera.worldToScreenY(text.getWorldY());
			if (centerX < -GameConfig.TILE_SIZE || centerX > GameConfig.SCREEN_WIDTH + GameConfig.TILE_SIZE
				|| centerY < -GameConfig.TILE_SIZE || centerY > GameConfig.SCREEN_HEIGHT + GameConfig.TILE_SIZE) {
				continue;
			}
			String message = text.getMessage();
			int x = TextRenderer.centeredX(g2, this.pickupTextFont, message, centerX);
			int y = centerY - text.getOffsetPixels();
			g2.setComposite(getPickupTextComposite(alpha));
			TextRenderer.draw(g2, this.pickupTextFont, message, text.getColor(), x, y, true);
		}
		g2.setComposite(previousComposite);
	}

	private Composite getPickupTextComposite(int alpha) {
		int clampedAlpha = Math.max(0, Math.min(255, alpha));
		Composite composite = this.pickupTextComposites[clampedAlpha];
		if (composite == null) {
			composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampedAlpha / 255f);
			this.pickupTextComposites[clampedAlpha] = composite;
		}
		return composite;
	}

	private void drawEnemies(Graphics2D g2, Game game) {
		for (int i = 0; i < game.getEnemies().size(); i++) {
			Enemy enemy = game.getEnemies().get(i);
			EnemyType type = enemy.getType();
			int screenX = this.camera.worldToScreenX(enemy.getWorldX());
			int screenY = this.camera.worldToScreenY(enemy.getWorldY());
			int drawWidth = type.getDrawWidth();
			int drawHeight = type.getDrawHeight();
			if (screenX < -drawWidth || screenX > GameConfig.SCREEN_WIDTH + drawWidth
				|| screenY < -drawHeight || screenY > GameConfig.SCREEN_HEIGHT + drawHeight) {
				continue;
			}
			BufferedImage sheet = this.assets.getImage(type.getSpritePath());
			int frame = (enemy.getAnimationTicks() / 8) % type.getFramesPerRow();
			int row = enemyAnimationRow(enemy, type);
			int sourceX = frame * type.getFrameWidth();
			int sourceY = row * type.getFrameHeight();
			int drawX = screenX - drawWidth / 2;
			int drawY = screenY - drawHeight / 2;
			drawEnemySprite(g2, sheet, enemy, drawX, drawY, drawWidth, drawHeight, sourceX, sourceY);
			drawEnemyHealthBar(g2, enemy, screenX, drawY - 8);
			if (enemy.hasDebuff(Debuff.BURN)) {
				drawBurningParticles(g2, enemy, screenX, drawY, drawWidth, drawHeight);
			}
		}
	}

	private void drawProjectiles(Graphics2D g2, Game game) {
		for (Projectile projectile : game.getProjectiles()) {
			ProjectileType type = projectile.getType();
			int screenX = this.camera.worldToScreenX(projectile.getWorldX());
			int screenY = this.camera.worldToScreenY(projectile.getWorldY());
			int drawWidth = type.getDrawWidth();
			int drawHeight = type.getDrawHeight();
			if (screenX < -drawWidth || screenX > GameConfig.SCREEN_WIDTH + drawWidth
				|| screenY < -drawHeight || screenY > GameConfig.SCREEN_HEIGHT + drawHeight) {
				continue;
			}
			BufferedImage sprite = this.assets.getImage(type.getSpritePath());
			AffineTransform previousTransform = g2.getTransform();
			g2.rotate(projectile.getAngleRadians() + type.getDrawAngleOffsetRadians(), screenX, screenY);
			g2.drawImage(sprite, screenX - drawWidth / 2, screenY - drawHeight / 2, drawWidth, drawHeight, null);
			g2.setTransform(previousTransform);
		}
	}

	private void drawFlameBurstEffects(Graphics2D g2, Game game) {
		Composite previousComposite = g2.getComposite();
		for (int i = 0; i < game.getFlameBurstEffects().size(); i++) {
			FlameBurstEffect effect = game.getFlameBurstEffects().get(i);
			double progress = effect.getAgeTicks() / (double) effect.getLifeTicks();
			double easeOut = 1.0 - (1.0 - progress) * (1.0 - progress);
			int centerX = this.camera.worldToScreenX(effect.getOriginWorldX());
			int centerY = this.camera.worldToScreenY(effect.getOriginWorldY());
			int radius = (int) Math.max(8.0, effect.getRadiusPixels() * easeOut);
			int alpha = Math.max(0, Math.min(220, (int) (220.0 * (1.0 - progress))));

			g2.setComposite(getPickupTextComposite(Math.max(0, Math.min(255, alpha))));
			g2.setColor(getFlameBurstOuterColor(alpha));
			g2.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
			g2.setColor(getFlameBurstCoreColor(alpha));
			int coreRadius = Math.max(8, radius / 4);
			g2.fillOval(centerX - coreRadius, centerY - coreRadius, coreRadius * 2, coreRadius * 2);

			for (int particle = 0; particle < effect.getParticleCount(); particle++) {
				double angle = effect.getParticleAngleRadians(particle) + progress * 0.9;
				int distance = (int) (effect.getRadiusPixels() * easeOut * effect.getParticleDistanceScale(particle));
				int particleX = centerX + (int) Math.round(Math.cos(angle) * distance);
				int particleY = centerY + (int) Math.round(Math.sin(angle) * distance);
				int emberSize = 3 + (particle % 5);
				g2.setColor((particle & 1) == 0 ? this.fireYellow : this.fireOrange);
				g2.drawLine(centerX, centerY, particleX, particleY);
				g2.fillOval(particleX - emberSize / 2, particleY - emberSize / 2, emberSize, emberSize + 2);
			}
		}
		g2.setComposite(previousComposite);
	}

	private Color getFlameBurstOuterColor(int alpha) {
		int colorAlpha = Math.max(35, Math.max(0, Math.min(255, alpha)) / 3);
		Color color = this.flameBurstOuterColors[colorAlpha];
		if (color == null) {
			color = new Color(255, 68, 12, colorAlpha);
			this.flameBurstOuterColors[colorAlpha] = color;
		}
		return color;
	}

	private Color getFlameBurstCoreColor(int alpha) {
		int colorAlpha = Math.max(70, Math.max(0, Math.min(255, alpha)) / 2);
		Color color = this.flameBurstCoreColors[colorAlpha];
		if (color == null) {
			color = new Color(255, 214, 72, colorAlpha);
			this.flameBurstCoreColors[colorAlpha] = color;
		}
		return color;
	}

	private void drawBurningParticles(Graphics2D g2, Enemy enemy, int screenX, int drawY, int drawWidth, int drawHeight) {
		int ticks = enemy.getAnimationTicks();
		for (int i = 0; i < 5; i++) {
			int offsetSeed = ticks * (i + 3) + i * 17;
			int particleX = screenX - drawWidth / 3 + Math.floorMod(offsetSeed * 7, Math.max(1, drawWidth * 2 / 3));
			int particleY = drawY + drawHeight - 10 - Math.floorMod(offsetSeed * 5, Math.max(1, drawHeight - 8));
			int size = 3 + Math.floorMod(offsetSeed, 4);
			g2.setColor((i & 1) == 0 ? this.fireOrange : this.fireYellow);
			g2.fillOval(particleX, particleY, size, size + 2);
		}
	}

	private int enemyAnimationRow(Enemy enemy, EnemyType type) {
		return type.getAnimationRow(enemy.getFacing());
	}

	private void drawEnemySprite(Graphics2D g2, BufferedImage sheet, Enemy enemy, int drawX, int drawY, int drawWidth, int drawHeight, int sourceX, int sourceY) {
		EnemyType type = enemy.getType();
		g2.drawImage(sheet, drawX, drawY, drawX + drawWidth, drawY + drawHeight,
			sourceX, sourceY, sourceX + type.getFrameWidth(), sourceY + type.getFrameHeight(), null);
	}

	private void drawEnemyHealthBar(Graphics2D g2, Enemy enemy, int centerX, int y) {
		if (enemy.getCurrentHP() >= enemy.getMaxHP()) {
			return;
		}
		int barWidth = 34;
		int barHeight = 5;
		int x = centerX - barWidth / 2;
		double hpRatio = Math.max(0.0, Math.min(1.0, enemy.getCurrentHP() / enemy.getMaxHP()));
		g2.setColor(Color.BLACK);
		g2.fillRect(x - 1, y - 1, barWidth + 2, barHeight + 2);
		g2.setColor(Color.RED);
		g2.fillRect(x, y, barWidth, barHeight);
		g2.setColor(Color.GREEN);
		g2.fillRect(x, y, (int) (barWidth * hpRatio), barHeight);
	}

	private boolean isHeldItemRendered(Game game, Player player) {
		ItemDefinition equippedWeapon = player.getEquipment().getEquippedWeapon();
		if (equippedWeapon == null || !equippedWeapon.isHandEquipable()) {
			return false;
		}
		return game.isUsingTool() || player.shouldRenderHeldItem();
	}

	private void drawHeldItem(Graphics2D g2, Game game, Player player) {
		drawHeldItemAt(g2, player, GameConfig.SCREEN_CENTER_X, GameConfig.SCREEN_CENTER_Y, game.isUsingTool(), game.getToolUseTicks(), game.getToolUseDurationTicks());
	}

	private void drawHeldItems(Graphics2D g2, Game game, Player localPlayer) {
		if (!localPlayer.isDead()) {
			drawHeldItem(g2, game, localPlayer);
		}
	}

	private void drawHeldItemAt(Graphics2D g2, Player player, int centerX, int centerY, boolean usingTool, int toolUseTicks, int toolUseDurationTicks) {
		ItemDefinition definition = player.getEquipment().getEquippedWeapon();
		if (definition == null || !definition.isHandEquipable() || (!usingTool && !player.shouldRenderHeldItem())) {
			return;
		}
		ItemDefinitionDrawConfig drawConfig = definition.getDrawConfig();
		BufferedImage itemSprite = this.assets.getImage(definition.getSpritePath());
		int itemWidth = definition.getHeldDrawWidth();
		int itemHeight = definition.getHeldDrawHeight();
		Facing facing = player.getFacing();
		int drawX = centerX + drawConfig.getBaseOffsetX(facing);
		int drawY = centerY + drawConfig.getBaseOffsetY(facing);
		double baseAngle = drawConfig.getBaseAngle(facing);
		double recoilAngle = 0.0;
		if (usingTool) {
			double swing = toolSwing(toolUseTicks, toolUseDurationTicks);
			drawX += drawConfig.getSwingOffsetX(facing, swing);
			drawY += drawConfig.getSwingOffsetY(facing, swing);
			recoilAngle = drawConfig.getSwingAngle(facing, swing);
		}
		AffineTransform previousTransform = g2.getTransform();
		applyHeldItemRotation(g2, drawConfig, facing, drawX, drawY, itemWidth, itemHeight, baseAngle, recoilAngle);
		if (drawConfig.isMirrored(facing)) {
			g2.drawImage(itemSprite, drawX + itemWidth, drawY, -itemWidth, itemHeight, null);
		} else {
			g2.drawImage(itemSprite, drawX, drawY, itemWidth, itemHeight, null);
		}
		g2.setTransform(previousTransform);
	}

	private ItemDefinitionDrawConfig heldItemDrawConfig(Player player) {
		ItemDefinition equippedWeapon = player.getEquipment().getEquippedWeapon();
		return equippedWeapon == null ? ItemDefinitionDrawConfig.DEFAULT : equippedWeapon.getDrawConfig();
	}

	private void drawToolTargetBar(Graphics2D g2, Game game) {
		MapObject targetObject = game.getToolTargetObject();
		if (targetObject == null || !targetObject.getType().isBreakable()) {
			return;
		}
		double durability = targetObject.getType().getDurability();
		if (durability <= 0.0) {
			return;
		}
		double remainingRatio = 1.0 - Math.min(1.0, targetObject.getDurabilityDamage() / durability);
		int barWidth = 52;
		int barHeight = 8;
		int objectWidth = targetObject.getType().getFootprintWidth() * GameConfig.TILE_SIZE;
		int screenX = this.camera.tileToScreenX(targetObject.getRootTileX()) + objectWidth / 2 - barWidth / 2;
		int screenY = this.camera.tileToScreenY(targetObject.getRootTileY()) - 12;
		g2.setColor(Color.BLACK);
		g2.fillRect(screenX - 2, screenY - 2, barWidth + 4, barHeight + 4);
		g2.setColor(Color.RED);
		g2.fillRect(screenX, screenY, barWidth, barHeight);
		g2.setColor(Color.GREEN);
		g2.fillRect(screenX, screenY, (int) (barWidth * remainingRatio), barHeight);
		g2.setColor(Color.BLACK);
		g2.setStroke(GameTheme.LIGHT_STROKE);
		g2.drawRect(screenX, screenY, barWidth, barHeight);
	}

	private void drawMapObjectOverlay(Graphics2D g2, GameMap map, Player player) {
		MapObject objectAtFeet = map.getObjectAtWorld(player.getX(), player.getFeetWorldY());
		if (objectAtFeet == null || !objectAtFeet.isAbovePlayer()) {
			return;
		}
		MapObject rootObject = map.getObject(objectAtFeet.getRootTileX(), objectAtFeet.getRootTileY());
		if (rootObject != null && intersectsVisibleArea(rootObject)) {
			drawMapObject(g2, rootObject);
		}
	}

	private void drawMapObject(Graphics2D g2, MapObject mapObject) {
		BufferedImage sprite = this.assets.getImage(mapObject.getType().getSpritePath());
		int screenX = this.camera.tileToScreenX(mapObject.getRootTileX());
		int screenY = this.camera.tileToScreenY(mapObject.getRootTileY());
		int drawWidth = mapObject.getType().getFootprintWidth() * GameConfig.TILE_SIZE;
		int drawHeight = mapObject.getType().getFootprintHeight() * GameConfig.TILE_SIZE;
		g2.drawImage(sprite, screenX, screenY, drawWidth, drawHeight, null);
	}

	private boolean intersectsVisibleArea(MapObject mapObject) {
		int objectStartX = mapObject.getRootTileX();
		int objectStartY = mapObject.getRootTileY();
		int objectEndX = objectStartX + mapObject.getType().getFootprintWidth() - 1;
		int objectEndY = objectStartY + mapObject.getType().getFootprintHeight() - 1;
		return objectEndX >= this.camera.getStartTileX()
			&& objectStartX <= this.camera.getEndTileX()
			&& objectEndY >= this.camera.getStartTileY()
			&& objectStartY <= this.camera.getEndTileY();
	}

	private void drawDebugWorld(Graphics2D g2, Game game) {
		DebugOptions debug = game.getDebugOptions();
		Player player = game.getPlayer();
		if (!game.isTrainingActive() || debug == null || player == null || player.isDead()) {
			return;
		}
		Composite previousComposite = g2.getComposite();
		java.awt.Stroke previousStroke = g2.getStroke();
		g2.setStroke(this.debugStroke);
		if (debug.shouldDrawFullAccuracyCone()) {
			drawAccuracyCone(g2, game, 0.0, this.debugFullConeColor, this.debugFullConeOutlineColor);
		}
		if (debug.shouldDrawWeaponAccuracyCone()) {
			drawAccuracyCone(g2, game, game.getEquippedPlayerAccuracy(), this.debugWeaponConeColor, this.debugWeaponConeOutlineColor);
		}
		if (debug.shouldDrawBulletTrajectories()) {
			drawDebugBulletTrajectories(g2, debug);
		}
		if (debug.shouldDrawTrainingFailurePerimeter()) {
			drawDebugTrainingFailurePerimeter(g2, game);
		}
		if (debug.shouldDrawPlayerMouseLine()) {
			g2.setColor(this.debugLineColor);
			this.debugLine.setLine(GameConfig.SCREEN_CENTER_X, GameConfig.SCREEN_CENTER_Y, mouseCanvasX(game), mouseCanvasY(game));
			g2.draw(this.debugLine);
		}
		if (debug.shouldDrawHitboxes()) {
			drawDebugHitboxes(g2, game);
		}
		if (debug.shouldDrawSpritePerimeters()) {
			drawDebugSpritePerimeters(g2, game);
		}
		if (debug.shouldDrawPlayerOrigin()) {
			drawDebugOrigin(g2);
		}
		if (debug.shouldDrawWeaponOrigin()) {
			drawDebugWeaponOrigin(g2, game);
		}
		if (debug.shouldDrawWeaponGripAnchor()) {
			drawDebugWeaponGripAnchor(g2, game);
		}
		if (debug.shouldDrawWeaponBarrelAnchor()) {
			drawDebugWeaponBarrelAnchor(g2, game);
		}
		g2.setStroke(previousStroke);
		g2.setComposite(previousComposite);
	}

	private void drawDebugHitboxes(Graphics2D g2, Game game) {
		g2.setColor(this.debugHitboxColor);
		Player player = game.getPlayer();
		g2.drawRect(GameConfig.SCREEN_CENTER_X - player.getHitboxWidth() / 2, GameConfig.SCREEN_CENTER_Y - player.getHitboxHeight() / 2,
			player.getHitboxWidth(), player.getHitboxHeight());
		for (int i = 0; i < game.getEnemies().size(); i++) {
			Enemy enemy = game.getEnemies().get(i);
			int radius = enemy.getType().getCollisionRadius();
			int screenX = this.camera.worldToScreenX(enemy.getWorldX());
			int screenY = this.camera.worldToScreenY(enemy.getWorldY());
			g2.drawOval(screenX - radius, screenY - radius, radius * 2, radius * 2);
		}
		drawDebugObjectHitboxes(g2, game.getMap());
	}

	private void drawDebugTrainingFailurePerimeter(Graphics2D g2, Game game) {
		TrainingMode trainingMode = game.getTrainingMode();
		if (trainingMode == null) {
			return;
		}
		g2.setColor(this.debugTrainingFailurePerimeterColor);
		g2.drawRect(
			this.camera.worldToScreenX(trainingMode.getFailurePerimeterLeftWorldX()),
			this.camera.worldToScreenY(trainingMode.getFailurePerimeterTopWorldY()),
			trainingMode.getFailurePerimeterWidthWorld(),
			trainingMode.getFailurePerimeterHeightWorld()
		);
	}

	private void drawDebugObjectHitboxes(Graphics2D g2, GameMap map) {
		if (map == null) {
			return;
		}
		for (int tileX = this.camera.getStartTileX(); tileX <= this.camera.getEndTileX(); tileX++) {
			for (int tileY = this.camera.getStartTileY(); tileY <= this.camera.getEndTileY(); tileY++) {
				MapObject mapObject = map.getObject(tileX, tileY);
				if (mapObject == null || !mapObject.isSolid()) {
					continue;
				}
				int left = map.objectCollisionLeftWorldX(tileX, mapObject);
				int top = map.objectCollisionTopWorldY(tileY, mapObject);
				g2.drawRect(this.camera.worldToScreenX(left), this.camera.worldToScreenY(top),
					mapObject.getType().getCollisionWidth(), mapObject.getType().getCollisionHeight());
			}
		}
	}

	private void drawDebugSpritePerimeters(Graphics2D g2, Game game) {
		drawDebugPlayerSpritePerimeter(g2, game.getPlayer());
		drawDebugHeldItemSpritePerimeter(g2, game);
	}

	private void drawDebugPlayerSpritePerimeter(Graphics2D g2, Player player) {
		BufferedImage sprite = player.getCurrentImage();
		int[] drawSize = fitPlayerSpriteSize(sprite);
		int drawX = GameConfig.SCREEN_CENTER_X - Player.PLAYER_WIDTH / 2;
		int drawY = GameConfig.SCREEN_CENTER_Y - Player.PLAYER_HEIGHT / 2;
		int spriteX = drawX + (Player.PLAYER_WIDTH - drawSize[0]) / 2;
		int spriteY = drawY + (Player.PLAYER_HEIGHT - drawSize[1]) / 2;
		g2.setColor(this.debugSpritePerimeterColor);
		g2.drawRect(spriteX, spriteY, drawSize[0], drawSize[1]);
	}

	private void drawDebugHeldItemSpritePerimeter(Graphics2D g2, Game game) {
		Player player = game.getPlayer();
		ItemDefinition definition = equippedHandDefinition(player);
		if (definition == null) {
			return;
		}
		ItemDefinitionDrawConfig drawConfig = definition.getDrawConfig();
		Facing facing = player.getFacing();
		int itemWidth = definition.getHeldDrawWidth();
		int itemHeight = definition.getHeldDrawHeight();
		int drawX = GameConfig.SCREEN_CENTER_X + drawConfig.getBaseOffsetX(facing);
		int drawY = GameConfig.SCREEN_CENTER_Y + drawConfig.getBaseOffsetY(facing);
		double baseAngle = drawConfig.getBaseAngle(facing);
		double recoilAngle = 0.0;
		if (game.isUsingTool()) {
			double swing = toolSwing(game.getToolUseTicks(), game.getToolUseDurationTicks());
			drawX += drawConfig.getSwingOffsetX(facing, swing);
			drawY += drawConfig.getSwingOffsetY(facing, swing);
			recoilAngle = drawConfig.getSwingAngle(facing, swing);
		}
		AffineTransform previousTransform = g2.getTransform();
		applyHeldItemRotation(g2, drawConfig, facing, drawX, drawY, itemWidth, itemHeight, baseAngle, recoilAngle);
		g2.setColor(this.debugSpritePerimeterColor);
		g2.drawRect(drawX, drawY, itemWidth, itemHeight);
		g2.setTransform(previousTransform);
	}

	private void applyHeldItemRotation(Graphics2D g2, ItemDefinitionDrawConfig drawConfig, Facing facing, int drawX, int drawY,
		int itemWidth, int itemHeight, double baseAngle, double recoilAngle) {
		double centerX = drawX + itemWidth / 2.0;
		double centerY = drawY + itemHeight / 2.0;
		if (recoilAngle != 0.0) {
			double anchorX = heldItemGripAnchorX(drawConfig, facing, drawX, itemWidth);
			double anchorY = heldItemGripAnchorY(drawConfig, drawY, itemHeight);
			double rotatedAnchorX = rotateX(anchorX, anchorY, centerX, centerY, baseAngle);
			double rotatedAnchorY = rotateY(anchorX, anchorY, centerX, centerY, baseAngle);
			g2.rotate(recoilAngle, rotatedAnchorX, rotatedAnchorY);
		}
		g2.rotate(baseAngle, centerX, centerY);
	}

	private double heldItemGripAnchorX(ItemDefinitionDrawConfig drawConfig, Facing facing, int drawX, int itemWidth) {
		int offsetX = drawConfig.isMirrored(facing) ? -drawConfig.getGripAnchorOffsetX() : drawConfig.getGripAnchorOffsetX();
		return (drawConfig.isMirrored(facing) ? drawX + itemWidth : drawX) + offsetX;
	}

	private double heldItemBarrelAnchorX(ItemDefinitionDrawConfig drawConfig, Facing facing, int drawX, int itemWidth) {
		return drawConfig.isMirrored(facing) ? drawX : drawX + itemWidth;
	}

	private double heldItemGripAnchorY(ItemDefinitionDrawConfig drawConfig, int drawY, int itemHeight) {
		return drawY + itemHeight + drawConfig.getGripAnchorOffsetY();
	}

	private double heldItemBarrelAnchorY(int drawY) {
		return drawY;
	}

	private double rotateX(double x, double y, double centerX, double centerY, double angle) {
		double deltaX = x - centerX;
		double deltaY = y - centerY;
		return centerX + deltaX * Math.cos(angle) - deltaY * Math.sin(angle);
	}

	private double rotateY(double x, double y, double centerX, double centerY, double angle) {
		double deltaX = x - centerX;
		double deltaY = y - centerY;
		return centerY + deltaX * Math.sin(angle) + deltaY * Math.cos(angle);
	}

	private double heldItemGripAnchorCanvasX(Player player, ItemDefinition definition) {
		ItemDefinitionDrawConfig drawConfig = definition.getDrawConfig();
		Facing facing = player.getFacing();
		int itemWidth = definition.getHeldDrawWidth();
		int itemHeight = definition.getHeldDrawHeight();
		int drawX = GameConfig.SCREEN_CENTER_X + drawConfig.getBaseOffsetX(facing);
		int drawY = GameConfig.SCREEN_CENTER_Y + drawConfig.getBaseOffsetY(facing);
		double centerX = drawX + itemWidth / 2.0;
		double centerY = drawY + itemHeight / 2.0;
		double anchorX = heldItemGripAnchorX(drawConfig, facing, drawX, itemWidth);
		double anchorY = heldItemGripAnchorY(drawConfig, drawY, itemHeight);
		return rotateX(anchorX, anchorY, centerX, centerY, drawConfig.getBaseAngle(facing));
	}

	private double heldItemGripAnchorCanvasY(Player player, ItemDefinition definition) {
		ItemDefinitionDrawConfig drawConfig = definition.getDrawConfig();
		Facing facing = player.getFacing();
		int itemWidth = definition.getHeldDrawWidth();
		int itemHeight = definition.getHeldDrawHeight();
		int drawX = GameConfig.SCREEN_CENTER_X + drawConfig.getBaseOffsetX(facing);
		int drawY = GameConfig.SCREEN_CENTER_Y + drawConfig.getBaseOffsetY(facing);
		double centerX = drawX + itemWidth / 2.0;
		double centerY = drawY + itemHeight / 2.0;
		double anchorX = heldItemGripAnchorX(drawConfig, facing, drawX, itemWidth);
		double anchorY = heldItemGripAnchorY(drawConfig, drawY, itemHeight);
		return rotateY(anchorX, anchorY, centerX, centerY, drawConfig.getBaseAngle(facing));
	}

	private double heldItemBarrelAnchorCanvasX(Player player, ItemDefinition definition) {
		ItemDefinitionDrawConfig drawConfig = definition.getDrawConfig();
		Facing facing = player.getFacing();
		int itemWidth = definition.getHeldDrawWidth();
		int itemHeight = definition.getHeldDrawHeight();
		int drawX = GameConfig.SCREEN_CENTER_X + drawConfig.getBaseOffsetX(facing);
		int drawY = GameConfig.SCREEN_CENTER_Y + drawConfig.getBaseOffsetY(facing);
		double centerX = drawX + itemWidth / 2.0;
		double centerY = drawY + itemHeight / 2.0;
		double anchorX = heldItemBarrelAnchorX(drawConfig, facing, drawX, itemWidth);
		double anchorY = heldItemBarrelAnchorY(drawY);
		return rotateX(anchorX, anchorY, centerX, centerY, drawConfig.getBaseAngle(facing));
	}

	private double heldItemBarrelAnchorCanvasY(Player player, ItemDefinition definition) {
		ItemDefinitionDrawConfig drawConfig = definition.getDrawConfig();
		Facing facing = player.getFacing();
		int itemWidth = definition.getHeldDrawWidth();
		int itemHeight = definition.getHeldDrawHeight();
		int drawX = GameConfig.SCREEN_CENTER_X + drawConfig.getBaseOffsetX(facing);
		int drawY = GameConfig.SCREEN_CENTER_Y + drawConfig.getBaseOffsetY(facing);
		double centerX = drawX + itemWidth / 2.0;
		double centerY = drawY + itemHeight / 2.0;
		double anchorX = heldItemBarrelAnchorX(drawConfig, facing, drawX, itemWidth);
		double anchorY = heldItemBarrelAnchorY(drawY);
		return rotateY(anchorX, anchorY, centerX, centerY, drawConfig.getBaseAngle(facing));
	}

	private double toolSwing(int toolUseTicks, int toolUseDurationTicks) {
		int duration = Math.max(1, toolUseDurationTicks);
		if (duration <= 1) {
			return 0.0;
		}
		double progress = Math.max(0.0, Math.min(1.0, toolUseTicks / (double) (duration - 1)));
		return Math.sin(progress * Math.PI);
	}

	private void drawDebugOrigin(Graphics2D g2) {
		int radius = 2;
		g2.setColor(this.debugOriginColor);
		g2.fillOval(GameConfig.SCREEN_CENTER_X - radius, GameConfig.SCREEN_CENTER_Y - radius, radius * 2, radius * 2);
		g2.setColor(Color.BLACK);
		g2.drawOval(GameConfig.SCREEN_CENTER_X - radius, GameConfig.SCREEN_CENTER_Y - radius, radius * 2, radius * 2);
	}

	private void drawDebugWeaponOrigin(Graphics2D g2, Game game) {
		if (equippedHandDefinition(game.getPlayer()) == null) {
			return;
		}
		int radius = 2;
		int originX = weaponOriginCanvasX(game);
		int originY = weaponOriginCanvasY(game);
		g2.setColor(this.debugWeaponOriginColor);
		g2.fillOval(originX - radius, originY - radius, radius * 2, radius * 2);
		g2.setColor(Color.BLACK);
		g2.drawOval(originX - radius, originY - radius, radius * 2, radius * 2);
	}

	private void drawDebugWeaponGripAnchor(Graphics2D g2, Game game) {
		Player player = game.getPlayer();
		ItemDefinition definition = equippedHandDefinition(player);
		if (definition == null) {
			return;
		}
		int radius = 3;
		int anchorX = (int) Math.round(heldItemGripAnchorCanvasX(player, definition));
		int anchorY = (int) Math.round(heldItemGripAnchorCanvasY(player, definition));
		g2.setColor(this.debugWeaponGripAnchorColor);
		g2.fillOval(anchorX - radius, anchorY - radius, radius * 2, radius * 2);
		g2.setColor(Color.BLACK);
		g2.drawOval(anchorX - radius, anchorY - radius, radius * 2, radius * 2);
	}

	private void drawDebugWeaponBarrelAnchor(Graphics2D g2, Game game) {
		Player player = game.getPlayer();
		ItemDefinition definition = equippedHandDefinition(player);
		if (definition == null) {
			return;
		}
		int radius = 3;
		int anchorX = (int) Math.round(heldItemBarrelAnchorCanvasX(player, definition));
		int anchorY = (int) Math.round(heldItemBarrelAnchorCanvasY(player, definition));
		g2.setColor(this.debugWeaponBarrelAnchorColor);
		g2.fillOval(anchorX - radius, anchorY - radius, radius * 2, radius * 2);
		g2.setColor(Color.BLACK);
		g2.drawOval(anchorX - radius, anchorY - radius, radius * 2, radius * 2);
	}

	private void drawDebugBulletTrajectories(Graphics2D g2, DebugOptions debug) {
		java.util.List<DebugBulletTrajectory> trajectories = debug.getBulletTrajectories();
		g2.setColor(this.debugBulletTrajectoryColor);
		for (int i = 0; i < trajectories.size(); i++) {
			DebugBulletTrajectory trajectory = trajectories.get(i);
			g2.drawLine(this.camera.worldToScreenX(trajectory.getStartWorldX()), this.camera.worldToScreenY(trajectory.getStartWorldY()),
				this.camera.worldToScreenX(trajectory.getEndWorldX()), this.camera.worldToScreenY(trajectory.getEndWorldY()));
		}
	}

	private void drawAccuracyCone(Graphics2D g2, Game game, double accuracy, Color fillColor, Color outlineColor) {
		double targetWorldX = mouseWorldX(game);
		double targetWorldY = mouseWorldY(game);
		double originWorldX = weaponOriginWorldX(game);
		double originWorldY = weaponOriginWorldY(game);
		double deltaX = targetWorldX - originWorldX;
		double deltaY = targetWorldY - originWorldY;
		double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
		if (length <= 0.001) {
			return;
		}
		double clampedAccuracy = Math.max(0.0, Math.min(1.0, accuracy));
		double halfBase = length * 0.5 * (1.0 - clampedAccuracy);
		double baseUnitX = -deltaY / length;
		double baseUnitY = deltaX / length;
		double tipX = worldToCanvasX(game, originWorldX);
		double tipY = worldToCanvasY(game, originWorldY);
		double leftX = worldToCanvasX(game, targetWorldX + baseUnitX * halfBase);
		double leftY = worldToCanvasY(game, targetWorldY + baseUnitY * halfBase);
		double rightX = worldToCanvasX(game, targetWorldX - baseUnitX * halfBase);
		double rightY = worldToCanvasY(game, targetWorldY - baseUnitY * halfBase);
		this.debugConePath.reset();
		this.debugConePath.moveTo(tipX, tipY);
		this.debugConePath.lineTo(leftX, leftY);
		this.debugConePath.lineTo(rightX, rightY);
		this.debugConePath.closePath();
		g2.setColor(fillColor);
		g2.fill(this.debugConePath);
		g2.setColor(outlineColor);
		g2.draw(this.debugConePath);
	}

	private double mouseWorldX(Game game) {
		return game.getCameraCenterWorldX() + (game.getInput().getMouseX() - GameConfig.SCREEN_CENTER_X) / game.getCameraZoom();
	}

	private double mouseWorldY(Game game) {
		return game.getCameraCenterWorldY() + (game.getInput().getMouseY() - GameConfig.SCREEN_CENTER_Y) / game.getCameraZoom();
	}

	private double mouseCanvasX(Game game) {
		return GameConfig.SCREEN_CENTER_X + (game.getInput().getMouseX() - GameConfig.SCREEN_CENTER_X) / game.getCameraZoom();
	}

	private double mouseCanvasY(Game game) {
		return GameConfig.SCREEN_CENTER_Y + (game.getInput().getMouseY() - GameConfig.SCREEN_CENTER_Y) / game.getCameraZoom();
	}

	private int weaponOriginCanvasX(Game game) {
		return (int) Math.round(worldToCanvasX(game, weaponOriginWorldX(game)));
	}

	private int weaponOriginCanvasY(Game game) {
		return (int) Math.round(worldToCanvasY(game, weaponOriginWorldY(game)));
	}

	private int weaponOriginWorldX(Game game) {
		Player player = game.getPlayer();
		ItemDefinition definition = equippedHandDefinition(player);
		if (player == null || definition == null) {
			return game.getCameraCenterWorldX();
		}
		return game.heldItemOriginWorldX(player, definition, aimFacing(game, player));
	}

	private int weaponOriginWorldY(Game game) {
		Player player = game.getPlayer();
		ItemDefinition definition = equippedHandDefinition(player);
		if (player == null || definition == null) {
			return game.getCameraCenterWorldY();
		}
		return game.heldItemOriginWorldY(player, definition, aimFacing(game, player));
	}

	private ItemDefinition equippedHandDefinition(Player player) {
		if (player == null) {
			return null;
		}
		ItemDefinition equippedWeapon = player.getEquipment().getEquippedWeapon();
		if (equippedWeapon == null || !equippedWeapon.isHandEquipable()) {
			return null;
		}
		return equippedWeapon;
	}

	private Facing aimFacing(Game game, Player player) {
		double deltaX = mouseWorldX(game) - player.getX();
		double deltaY = mouseWorldY(game) - player.getY();
		if (Math.abs(deltaX) <= 0.001 && Math.abs(deltaY) <= 0.001) {
			return player.getFacing();
		}
		return facingFromDelta(deltaX, deltaY);
	}

	private double worldToCanvasX(Game game, double worldX) {
		return worldX - game.getCameraCenterWorldX() + GameConfig.SCREEN_CENTER_X;
	}

	private double worldToCanvasY(Game game, double worldY) {
		return worldY - game.getCameraCenterWorldY() + GameConfig.SCREEN_CENTER_Y;
	}

	private Facing facingFromDelta(double deltaX, double deltaY) {
		if (Math.abs(deltaX) > Math.abs(deltaY) * 1.35) {
			return deltaX < 0 ? Facing.LEFT : Facing.RIGHT;
		}
		if (Math.abs(deltaY) > Math.abs(deltaX) * 1.35) {
			return deltaY < 0 ? Facing.UP : Facing.DOWN;
		}
		if (deltaX < 0 && deltaY < 0) {
			return Facing.UP_LEFT;
		}
		if (deltaX > 0 && deltaY < 0) {
			return Facing.UP_RIGHT;
		}
		if (deltaX < 0 && deltaY > 0) {
			return Facing.DOWN_LEFT;
		}
		return Facing.DOWN_RIGHT;
	}

	private void drawDebugMenu(Graphics2D g2, Game game) {
		DebugOptions debug = game.getDebugOptions();
		if (!game.isTrainingActive() || debug == null || !debug.isMenuOpen()) {
			return;
		}
		java.awt.Stroke previousStroke = g2.getStroke();
		g2.setColor(this.debugPanelBg);
		g2.fillRect(DebugOptions.PANEL_X, DebugOptions.PANEL_Y, DebugOptions.PANEL_WIDTH, DebugOptions.PANEL_HEIGHT);
		g2.setColor(this.debugPanelBorder);
		g2.setStroke(GameTheme.LIGHT_STROKE);
		g2.drawRect(DebugOptions.PANEL_X, DebugOptions.PANEL_Y, DebugOptions.PANEL_WIDTH, DebugOptions.PANEL_HEIGHT);
		TextRenderer.draw(g2, this.debugTitleFont, "DEBUG", Color.WHITE, DebugOptions.PANEL_X + DebugOptions.PANEL_PADDING,
			DebugOptions.TITLE_BASELINE_Y, false);
		drawDebugSwitch(g2, debug, 0, "Hitboxes jugador/objetos/enemigos");
		drawDebugSwitch(g2, debug, 1, "Perimetros sprites");
		drawDebugSwitch(g2, debug, 2, "Linea jugador -> mouse");
		drawDebugSwitch(g2, debug, 3, "Origen jugador");
		drawDebugSwitch(g2, debug, 4, "Origen arma");
		drawDebugSwitch(g2, debug, 5, "Grip anchor");
		drawDebugSwitch(g2, debug, 6, "Barrel anchor");
		drawDebugSwitch(g2, debug, 7, "Cono accuracy completo");
		drawDebugSwitch(g2, debug, 8, "Cono accuracy arma");
		drawDebugSwitch(g2, debug, 9, "Trayectoria de bala");
		drawDebugSwitch(g2, debug, 10, "Forzar precision 100%");
		drawDebugSwitch(g2, debug, 11, "Perimetro fallo training");
		drawDebugSwitch(g2, debug, DebugOptions.UNLOCK_ALL_WEAPONS_ROW, "Desbloquear todas las armas");
		drawDebugTrajectorySlider(g2, debug);
		g2.setStroke(previousStroke);
	}

	private void drawDebugSwitch(Graphics2D g2, DebugOptions debug, int row, String label) {
		boolean enabled = debug.isRowEnabled(row);
		int y = DebugOptions.FIRST_ROW_Y + row * DebugOptions.ROW_HEIGHT;
		g2.setColor(enabled ? this.debugSwitchOnColor : this.debugSwitchOffColor);
		g2.fillRect(DebugOptions.SWITCH_X, y, DebugOptions.SWITCH_SIZE, DebugOptions.SWITCH_SIZE);
		g2.setColor(enabled ? Color.WHITE : Color.GRAY);
		g2.drawRect(DebugOptions.SWITCH_X, y, DebugOptions.SWITCH_SIZE, DebugOptions.SWITCH_SIZE);
		if (enabled) {
			g2.drawLine(DebugOptions.SWITCH_X + 4, y + 9, DebugOptions.SWITCH_X + 8, y + 13);
			g2.drawLine(DebugOptions.SWITCH_X + 8, y + 13, DebugOptions.SWITCH_X + 15, y + 4);
		}
		TextRenderer.draw(g2, this.debugFont, label + (enabled ? " ON" : " OFF"), enabled ? Color.WHITE : Color.LIGHT_GRAY,
			DebugOptions.TEXT_X, y + 14, false);
	}

	private void drawEquipment(Graphics2D g2, Game game) {
		if (!game.isTrainingActive() || game.getPlayer() == null || game.getPlayer().isDead() || game.getState() == State.SETTINGS) {
			return;
		}
		Player player = game.getPlayer();
		Equipment equipment = player.getEquipment();
		if (!equipment.isMenuOpen()) {
			return;
		}
		drawEquipmentPanel(g2, game, equipment);
		if (equipment.isWeaponSelectorOpen()) {
			drawEquipmentWeaponList(g2, game, equipment);
		}
	}

	private void drawEquipmentPanel(Graphics2D g2, Game game, Equipment equipment) {
		int x = game.getEquipmentPanelX();
		int y = game.getEquipmentPanelY();
		int width = game.getEquipmentPanelWidth();
		int height = game.getEquipmentPanelHeight();
		g2.setColor(GameTheme.TRANSPARENT_BLACK);
		g2.fillRect(x, y, width, height);
		g2.setColor(new Color(255, 230, 120, 220));
		g2.setStroke(GameTheme.LIGHT_STROKE);
		g2.drawRect(x, y, width, height);
		BufferedImage sheriffIcon = this.assets.getImage("sprites/sheriffsss_icono.png");
		int iconSize = 128;
		g2.drawImage(sheriffIcon, x + (width - iconSize) / 2, y + 42, iconSize, iconSize, null);
		drawEquipmentSelector(g2, game, equipment.getEquippedWeapon());
	}

	private void drawEquipmentSelector(Graphics2D g2, Game game, ItemDefinition weapon) {
		int x = game.getEquipmentSelectorX();
		int y = game.getEquipmentSelectorY();
		int width = game.getEquipmentSelectorWidth();
		int height = game.getEquipmentSelectorHeight();
		g2.setColor(new Color(0, 0, 0, 190));
		g2.fillRect(x, y, width, height);
		g2.setColor(new Color(255, 230, 120, 220));
		g2.drawRect(x, y, width, height);
		if (weapon != null) {
			drawEquipmentWeaponSprite(g2, weapon, x + 8, y + 8, width - 16, 38);
			String damageText = "Daño: " + formatDamage(weapon.getProjectileDamage());
			TextRenderer.draw(g2, this.debugFont, damageText, Color.LIGHT_GRAY,
				TextRenderer.centeredX(g2, this.debugFont, damageText, x + width / 2), y + height - 12, false);
		}
	}

	private void drawEquipmentWeaponList(Graphics2D g2, Game game, Equipment equipment) {
		java.util.List<ItemDefinition> weapons = equipment.getWeaponSelectionOrder();
		int x = game.getEquipmentListX();
		int y = game.getEquipmentListY();
		int width = game.getEquipmentListWidth();
		int rowHeight = game.getEquipmentListRowHeight();
		g2.setColor(new Color(0, 0, 0, 190));
		g2.fillRect(x, y, width, rowHeight * weapons.size());
		g2.setColor(new Color(255, 230, 120, 220));
		g2.drawRect(x, y, width, rowHeight * weapons.size());
		for (int i = 0; i < weapons.size(); i++) {
			ItemDefinition weapon = weapons.get(i);
			int rowY = y + i * rowHeight;
			boolean selected = weapon == equipment.getEquippedWeapon();
			g2.setColor(selected ? new Color(70, 130, 90, 180) : (i % 2 == 0 ? new Color(255, 255, 255, 24) : new Color(255, 255, 255, 12)));
			g2.fillRect(x + 1, rowY + 1, width - 2, rowHeight - 2);
			drawEquipmentWeaponSprite(g2, weapon, x + 12, rowY + 8, 90, 32);
			TextRenderer.draw(g2, this.debugFont, weapon.getDisplayName(), Color.WHITE, x + 120, rowY + 21, false);
			TextRenderer.draw(g2, this.debugFont, "Daño: " + formatDamage(weapon.getProjectileDamage()), Color.LIGHT_GRAY, x + 120, rowY + 41, false);
		}
	}

	private void drawEquipmentWeaponSprite(Graphics2D g2, ItemDefinition weapon, int x, int y, int maxWidth, int maxHeight) {
		BufferedImage sprite = this.assets.getImage(weapon.getSpritePath());
		int drawWidth = Math.max(1, weapon.getHeldDrawWidth());
		int drawHeight = Math.max(1, weapon.getHeldDrawHeight());
		double scale = Math.min(maxWidth / (double) drawWidth, maxHeight / (double) drawHeight);
		int fittedWidth = Math.max(1, (int) Math.round(drawWidth * scale));
		int fittedHeight = Math.max(1, (int) Math.round(drawHeight * scale));
		int drawX = x + (maxWidth - fittedWidth) / 2;
		int drawY = y + (maxHeight - fittedHeight) / 2;
		g2.drawImage(sprite, drawX, drawY, fittedWidth, fittedHeight, null);
	}

	private String formatDamage(double damage) {
		return Math.abs(damage - Math.round(damage)) < 0.001 ? Integer.toString((int) Math.round(damage)) : String.format(java.util.Locale.US, "%.1f", damage);
	}

	private void drawDebugTrajectorySlider(Graphics2D g2, DebugOptions debug) {
		int y = DebugOptions.TRAJECTORY_SLIDER_Y;
		TextRenderer.draw(g2, this.debugFont, "Trayectorias: " + debug.getBulletTrajectoryLimit(), Color.WHITE,
			DebugOptions.SWITCH_X, y + 5, false);
		g2.setColor(Color.BLACK);
		g2.fillRect(DebugOptions.TRAJECTORY_SLIDER_X - 2, y + 20, DebugOptions.TRAJECTORY_SLIDER_WIDTH + 4,
			DebugOptions.TRAJECTORY_SLIDER_HEIGHT + 4);
		g2.setColor(Color.GRAY);
		g2.fillRect(DebugOptions.TRAJECTORY_SLIDER_X, y + 22, DebugOptions.TRAJECTORY_SLIDER_WIDTH,
			DebugOptions.TRAJECTORY_SLIDER_HEIGHT);
		g2.setColor(Color.GREEN);
		g2.fillRect(DebugOptions.TRAJECTORY_SLIDER_X, y + 22,
			(int) Math.round(DebugOptions.TRAJECTORY_SLIDER_WIDTH * debug.getBulletTrajectorySliderValue()),
			DebugOptions.TRAJECTORY_SLIDER_HEIGHT);
		int knobX = DebugOptions.TRAJECTORY_SLIDER_X
			+ (int) Math.round(DebugOptions.TRAJECTORY_SLIDER_WIDTH * debug.getBulletTrajectorySliderValue());
		g2.setColor(Color.WHITE);
		g2.fillRect(knobX - 5, y + 14, 10, 22);
	}

	private void drawDebugAndUi(Graphics2D g2, Game game) {
		Player player = game.getPlayer();
		if (game.isSpectating() || game.isDeathOverlayActive()) {
			return;
		}
		if (player.getTakingDamage()) {
			g2.setColor(GameTheme.DAMAGE_RED);
			g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);
		}
	}

	private void drawDeathOverlay(Graphics2D g2, Game game) {
		g2.setColor(this.deathWhite);
		g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);
		g2.setColor(this.deathBlack);
		g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);
		int x = TextRenderer.centeredX(g2, this.deathFont, "YOU DIED", GameConfig.SCREEN_CENTER_X);
		TextRenderer.draw(g2, this.deathFont, "YOU DIED", Color.RED, x, GameConfig.SCREEN_CENTER_Y + 32, true);
	}

	private void drawInfoMessages(Graphics2D g2, Game game) {
		int drawY = GameConfig.SCREEN_HEIGHT - 22;
		for (int i = 0; i < game.getInfoMessageSlotCount(); i++) {
			String message = game.getInfoMessage(i);
			if (message == null || game.getInfoMessageTicks(i) <= 0) {
				continue;
			}
			int width = g2.getFontMetrics(this.infoMessageFont).stringWidth(message);
			g2.setColor(GameTheme.TRANSPARENT_BLACK);
			g2.fillRect(10, drawY - 17, width + 14, 22);
			TextRenderer.draw(g2, this.infoMessageFont, message, Color.WHITE, 17, drawY, false);
			drawY -= 26;
		}
	}

	private void drawSettingsOverlay(Graphics2D g2, Game game) {
		int offsetY = game.getSettingsOverlayOffsetY();
		g2.setColor(GameTheme.TRANSPARENT_BLACK);
		g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);
		g2.setColor(GameTheme.DIRT);
		g2.fillRect(GameConfig.SETTINGS_PANEL_X, GameConfig.SETTINGS_PANEL_Y + offsetY, GameConfig.SETTINGS_PANEL_WIDTH, GameConfig.SETTINGS_PANEL_HEIGHT);
		g2.setColor(Color.BLACK);
		g2.setStroke(GameTheme.HEAVY_STROKE);
		g2.drawRect(GameConfig.SETTINGS_PANEL_X, GameConfig.SETTINGS_PANEL_Y + offsetY, GameConfig.SETTINGS_PANEL_WIDTH, GameConfig.SETTINGS_PANEL_HEIGHT);

		int titleX = TextRenderer.centeredX(g2, this.settingsTitleFont, "SETTINGS", GameConfig.SCREEN_CENTER_X);
		TextRenderer.draw(g2, this.settingsTitleFont, "SETTINGS", Color.WHITE, titleX, GameConfig.SETTINGS_PANEL_Y + 58 + offsetY, MENU_TEXT_STYLE);
		drawSlider(g2, "Music", GameConfig.SETTINGS_MUSIC_SLIDER_Y + offsetY, game.getAudio().getMusicVolume());
		drawSlider(g2, "SFX", GameConfig.SETTINGS_SFX_SLIDER_Y + offsetY, game.getAudio().getSfxVolume());
		drawResolutionSlider(g2, game, offsetY);
		drawSettingsButton(g2, game, "FULLSCREEN: " + (game.isPendingFullscreen() ? "ON" : "OFF"), GameConfig.SETTINGS_FULLSCREEN_BUTTON_Y + offsetY);
		drawSettingsButton(g2, game, "RESUME", GameConfig.SETTINGS_RESUME_BUTTON_Y + offsetY);
		if (game.isTrainingActive()) {
			drawSettingsDebugButton(g2, game);
		}
		drawSettingsButton(g2, game, "MAIN MENU", GameConfig.SETTINGS_MENU_BUTTON_Y + offsetY);
		drawSettingsButton(g2, game, "QUIT", GameConfig.SETTINGS_QUIT_BUTTON_Y + offsetY);
		if (!game.getSettingsMessage().isEmpty()) {
			int messageX = TextRenderer.centeredX(g2, this.settingsFont, game.getSettingsMessage(), GameConfig.SCREEN_CENTER_X);
			TextRenderer.draw(g2, this.settingsFont, game.getSettingsMessage(), Color.YELLOW, messageX, GameConfig.SETTINGS_PANEL_Y + GameConfig.SETTINGS_PANEL_HEIGHT - 22 + offsetY, MENU_TEXT_STYLE);
		}
	}

	private void drawSlider(Graphics2D g2, String label, int y, double value) {
		TextRenderer.draw(g2, this.settingsFont, label, Color.WHITE, GameConfig.SETTINGS_PANEL_X + 55, y + 6, MENU_TEXT_STYLE);
		g2.setColor(Color.BLACK);
		g2.fillRect(GameConfig.SETTINGS_SLIDER_X - 2, y - 2, GameConfig.SETTINGS_SLIDER_WIDTH + 4, 12);
		g2.setColor(Color.GRAY);
		g2.fillRect(GameConfig.SETTINGS_SLIDER_X, y, GameConfig.SETTINGS_SLIDER_WIDTH, 8);
		g2.setColor(Color.GREEN);
		g2.fillRect(GameConfig.SETTINGS_SLIDER_X, y, (int) (GameConfig.SETTINGS_SLIDER_WIDTH * value), 8);
		int knobX = GameConfig.SETTINGS_SLIDER_X + (int) (GameConfig.SETTINGS_SLIDER_WIDTH * value);
		g2.setColor(Color.WHITE);
		g2.fillRect(knobX - 5, y - 6, 10, 20);
	}

	private void drawResolutionSlider(Graphics2D g2, Game game, int offsetY) {
		drawSlider(g2, "Res", GameConfig.SETTINGS_RESOLUTION_SLIDER_Y + offsetY, game.getWindowResolutionSliderValue());
		String label = game.getWindowResolutionLabel();
		int labelX = TextRenderer.centeredX(g2, this.settingsFont, label, GameConfig.SETTINGS_SLIDER_X + GameConfig.SETTINGS_SLIDER_WIDTH / 2);
		TextRenderer.draw(g2, this.settingsFont, label, Color.WHITE, labelX, GameConfig.SETTINGS_RESOLUTION_SLIDER_Y + 36 + offsetY, MENU_TEXT_STYLE);
	}

	private void drawSettingsDebugButton(Graphics2D g2, Game game) {
		boolean hovered = game.isSettingsDebugButtonHovered();
		int x = game.getSettingsDebugButtonX();
		int y = game.getSettingsDebugButtonY();
		int width = game.getSettingsDebugButtonWidth();
		int height = game.getSettingsDebugButtonHeight();
		g2.setColor(game.isDebugMenuOpen() ? GameTheme.TRANSPARENT_BLACK : GameTheme.DARK_DIRT);
		g2.fillRect(x, y, width, height);
		g2.setColor(hovered ? Color.YELLOW : Color.BLACK);
		g2.setStroke(GameTheme.LIGHT_STROKE);
		g2.drawRect(x, y, width, height);
		String text = "DEBUG";
		int textX = TextRenderer.centeredX(g2, this.settingsButtonFont, text, x + width / 2);
		TextRenderer.draw(g2, this.settingsButtonFont, text, hovered ? Color.YELLOW : Color.WHITE, textX, y + 28, MENU_TEXT_STYLE);
	}

	private void drawSettingsButton(Graphics2D g2, Game game, String text, int y) {
		drawSettingsButton(g2, game, text, y, true);
	}

	private void drawSettingsButton(Graphics2D g2, Game game, String text, int y, boolean enabled) {
		boolean hovered = enabled && game.getInput().getMouseX() >= GameConfig.SETTINGS_BUTTON_X
			&& game.getInput().getMouseX() <= GameConfig.SETTINGS_BUTTON_X + GameConfig.SETTINGS_BUTTON_WIDTH
			&& game.getInput().getMouseY() >= y
			&& game.getInput().getMouseY() <= y + GameConfig.SETTINGS_BUTTON_HEIGHT;
		g2.setColor(enabled ? GameTheme.DARK_DIRT : GameTheme.TRANSPARENT_BLACK);
		g2.fillRect(GameConfig.SETTINGS_BUTTON_X, y, GameConfig.SETTINGS_BUTTON_WIDTH, GameConfig.SETTINGS_BUTTON_HEIGHT);
		g2.setColor(hovered ? Color.YELLOW : enabled ? Color.BLACK : Color.GRAY);
		g2.setStroke(GameTheme.LIGHT_STROKE);
		g2.drawRect(GameConfig.SETTINGS_BUTTON_X, y, GameConfig.SETTINGS_BUTTON_WIDTH, GameConfig.SETTINGS_BUTTON_HEIGHT);
		int textX = TextRenderer.centeredX(g2, this.settingsButtonFont, text, GameConfig.SCREEN_CENTER_X);
		TextRenderer.draw(g2, this.settingsButtonFont, text, hovered ? Color.YELLOW : enabled ? Color.WHITE : Color.GRAY, textX, y + 28, MENU_TEXT_STYLE);
	}
}
