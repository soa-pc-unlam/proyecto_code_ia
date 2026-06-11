package SheriffsssPackage;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Modo entrenamiento: arena fija, enemigos spawneados manualmente con cantidad
 * y comportamiento controlados por panel HUD, tutorial corriendo en hilo aparte.
 *
 * No es subclase de Game ni de JPanel: es un sistema-coordinador que vive
 * dentro del estado de Game. La logica de simulacion sigue siendo del game loop
 * existente; este sistema solo agrega:
 *   - construccion de arena fija
 *   - control de panel
 *   - sincronizacion de enemigos con el panel
 *   - publicacion de eventos al TutorialThread
 *   - render del HUD del panel y del tutorial
 *
 * Threading:
 *   - Vive en el game loop thread (SheriffsssGameLoop). No se accede desde otros threads.
 *   - Se comunica con TutorialThread via su API thread-safe (publishEvent, skip, getCurrentMessage).
 */
public final class TrainingMode {
	// --- Arena ---
	private static final int ARENA_TILES_WIDE = GameConfig.TRAINING_ARENA_TILES_WIDE;
	private static final int ARENA_TILES_HIGH = GameConfig.TRAINING_ARENA_TILES_HIGH;
	private static final int INTERIOR_TILES = GameConfig.TRAINING_INTERIOR_FENCE_TILES;
	private static final int INTERIOR_ORIGIN_X = (ARENA_TILES_WIDE - INTERIOR_TILES) / 2;
	private static final int INTERIOR_ORIGIN_Y = (ARENA_TILES_HIGH - INTERIOR_TILES) / 2;
	private static final int INTERIOR_CENTER_TILE_X = INTERIOR_ORIGIN_X + INTERIOR_TILES / 2;
	private static final int INTERIOR_CENTER_TILE_Y = INTERIOR_ORIGIN_Y + INTERIOR_TILES / 2;
	private static final int ENEMY_INITIAL_DAY_COUNT = 1;
	private static final int CENTER_FENCE_HALF_SIZE_TILES = 2;
	private static final int BORDER_TREE_WIDTH_TILES = 2;
	private static final int BORDER_TREE_HEIGHT_TILES = 3;
	private static final int DECORATION_COUNT = 16;
	private static final int RANDOM_SPAWN_ATTEMPTS = 80;
	private static final int TARGET_LIFETIME_TICKS = GameConfig.TARGET_FPS * 4;
	private static final int TARGET_SPAWN_INTERVAL_TICKS = GameConfig.TARGET_FPS * 1;
	private static final int TARGET_HINT_MIN_TICKS = GameConfig.TARGET_FPS * 3;
	private static final int TIMER_NOTICE_TICKS = GameConfig.TARGET_FPS * 5;
	private static final int SESSION_DURATION_TICKS = GameConfig.TARGET_FPS * 60;
	private static final int COUNTDOWN_SOUND_SECONDS_REMAINING = 4;
	private static final int COUNTDOWN_SOUND_TICKS_REMAINING = GameConfig.TARGET_FPS * COUNTDOWN_SOUND_SECONDS_REMAINING;
	private static final String COUNTDOWN_SOUND_PATH = "sounds/countdown.wav";
	private static final float COUNTDOWN_SOUND_GAIN_DB = 9.5f;
	private static final String HIT_MILESTONE_SOUND_PATH = "sounds/ding_1.wav";
	private static final MapObjectType[] BORDER_TREE_TYPES = {
		MapObjectType.TRAINING_BORDER_TREE_1,
		MapObjectType.TRAINING_BORDER_TREE_2,
		MapObjectType.TRAINING_BORDER_TREE_3,
		MapObjectType.TRAINING_BORDER_TREE_4
	};

	// --- Persistencia ---
	private static final String CONFIG_PATH = "saves/training.cfg";
	private static final String CONFIG_KEY_COUNT = "count";
	private static final char CONFIG_KEY_VALUE_SEPARATOR = '=';

	// --- TutorialThread shutdown ---
	private static final long TUTORIAL_JOIN_TIMEOUT_MS = 200L;

	// --- Panel HUD ---
	private static final int PANEL_WIDTH = 240;
	private static final int PANEL_HEIGHT = 110;
	private static final int PANEL_MARGIN = 16;
	private static final int PANEL_TEXT_X_PADDING = 10;
	private static final int PANEL_TITLE_BASELINE_Y = 22;
	private static final int PANEL_LINE_COUNT_Y = 44;
	private static final int PANEL_LINE_SCORE_Y = 62;
	private static final int PANEL_LINE_RESET_Y = 84;
	private static final float PANEL_BG_ALPHA = 0.78f;
	private static final Color PANEL_BG_COLOR = new Color(20, 20, 22);
	private static final Color PANEL_BORDER_COLOR = Color.WHITE;
	private static final Color PANEL_TITLE_COLOR = new Color(240, 220, 120);
	private static final Color PANEL_BODY_COLOR = Color.WHITE;
	private static final String PANEL_TITLE_TEXT = "TRAINING MODE";
	private static final String AIM_HINT_TEXT = "Apunta con el mouse y dispara con click";
	private static final String TARGET_HINT_TEXT = "Disparale a las dianas para acumular puntos";
	private static final String TIMER_NOTICE_TEXT = "Tenes 60 segundos antes de que termine tu entrenamiento";
	private static final float HINT_START_ALPHA = 1.0f;
	private static final float HINT_SHOT_ALPHA_STEP = 0.2f;
	private static final float HINT_ALPHA_FADE_STEP = 0.035f;
	private static final int HINT_BASELINE_BELOW_OUTER_FENCE_BOTTOM = GameConfig.TILE_SIZE + 28;
	private static final int HINT_BOTTOM_SCREEN_MARGIN = 18;

	// --- Tutorial box ---
	private static final int TUTORIAL_BOX_MAX_WIDTH = 720;
	private static final int TUTORIAL_BOX_MIN_SCREEN_MARGIN = 80;
	private static final int TUTORIAL_BOX_PADDING = 16;
	private static final int TUTORIAL_BOX_LINE_HEIGHT = 22;
	private static final int TUTORIAL_BOX_TITLE_LINE_HEIGHT = 28;
	private static final int TUTORIAL_BOX_GROUP_GAP = 2;
	private static final int TUTORIAL_BOX_KEYS_GAP = 4;
	private static final int TUTORIAL_BOX_FOOTER_RESERVE = 18;
	private static final int TUTORIAL_BOX_BOTTOM_OFFSET = 40;
	private static final int TUTORIAL_BOX_FOOTER_TEXT_Y_OFFSET = 10;
	private static final float TUTORIAL_BOX_BG_ALPHA = 0.85f;
	private static final Color TUTORIAL_BOX_BG_COLOR = new Color(15, 15, 18);
	private static final Color TUTORIAL_BOX_BORDER_COLOR = new Color(240, 220, 120);
	private static final Color TUTORIAL_BOX_TITLE_COLOR = new Color(240, 220, 120);
	private static final Color TUTORIAL_BOX_BODY_COLOR = Color.WHITE;
	private static final Color TUTORIAL_BOX_KEYS_COLOR = new Color(180, 220, 255);
	private static final Color TUTORIAL_BOX_FOOTER_COLOR = new Color(160, 160, 160);

	// --- Death prompt ---
	private static final int DEATH_BOX_WIDTH = 360;
	private static final int DEATH_BOX_HEIGHT = 170;
	private static final int DEATH_BOX_Y_OFFSET_FROM_CENTER = 60;
	private static final int DEATH_BOX_TITLE_BASELINE_Y = 32;
	private static final float DEATH_BOX_BG_ALPHA = 0.88f;
	private static final Color DEATH_BOX_BG_COLOR = new Color(15, 15, 18);
	private static final Color DEATH_BOX_BORDER_COLOR = new Color(220, 60, 60);
	private static final Color DEATH_BOX_TITLE_COLOR = new Color(255, 90, 90);
	private static final Color DEATH_BOX_BODY_COLOR = Color.WHITE;
	private static final String DEATH_TITLE_TEXT = "Moriste";
	private static final String RESTART_BUTTON_TEXT = "Reiniciar";
	private static final String EXIT_BUTTON_TEXT = "Salir";

	// --- Final screen ---
	private static final int FINAL_BOX_WIDTH = 460;
	private static final int FINAL_BOX_HEIGHT = 290;
	private static final int FINAL_BOX_TITLE_BASELINE_Y = 42;
	private static final int FINAL_BOX_HITS_Y = 92;
	private static final int FINAL_BOX_PRECISION_Y = 126;
	private static final int FINAL_BOX_FINAL_SCORE_Y = 160;
	private static final String FINAL_TITLE_TEXT = "Entrenamiento terminado";
	private static final int END_BUTTON_WIDTH = 220;
	private static final int END_BUTTON_HEIGHT = 38;
	private static final int DEATH_RESTART_BUTTON_Y = 56;
	private static final int DEATH_EXIT_BUTTON_Y = 108;
	private static final int FINAL_RESTART_BUTTON_Y = 188;
	private static final int FINAL_EXIT_BUTTON_Y = 236;

	// --- Fonts ---
	private static final Font PANEL_TITLE_FONT = new Font("Arial", Font.BOLD, 14);
	private static final Font PANEL_BODY_FONT = new Font("Arial", Font.PLAIN, 12);
	private static final Font TUTORIAL_TITLE_FONT = new Font("Arial", Font.BOLD, 18);
	private static final Font TUTORIAL_BODY_FONT = new Font("Arial", Font.BOLD, 28);
	private static final Font TUTORIAL_KEYS_FONT = new Font("Arial", Font.BOLD, 13);
	private static final Font HINT_FONT = TUTORIAL_BODY_FONT.deriveFont(TUTORIAL_BODY_FONT.getSize2D() * 2.0f);
	private static final Font TIMER_FONT = TUTORIAL_BODY_FONT.deriveFont(TUTORIAL_BODY_FONT.getSize2D() * 1.5f);
	private static final Font PRECISION_FONT = TUTORIAL_BODY_FONT.deriveFont(TUTORIAL_BODY_FONT.getSize2D() * 1.5f);
	private static final int PRECISION_VALUE_LINE_GAP = 44;
	private static final TextRenderer.Style TRAINING_TEXT_STYLE = TextRenderer.Style.OUTLINED;

	// --- Tutorial step durations ---
	private static final long STEP_DEFAULT_MIN_MS = 600L;
	private static final long STEP_MOVEMENT_MIN_MS = 800L;
	private static final long STEP_MOVEMENT_MAX_MS = 15000L;
	private static final long STEP_SHOT_MAX_MS = 20000L;
	private static final long STEP_KILL_MAX_MS = 30000L;

	private final Game game;
	private final EnemySystem enemySystem;
	private final TrainingControls controls = new TrainingControls();
	private final int sessionSeedHash;
	private final Random terrainRandom;
	private final Random targetSpawnRandom;
	private final TutorialThread tutorialThread;

	private GameMap arena;
	private int playerSpawnWorldX;
	private int playerSpawnWorldY;
	private int lastEnemyCount;
	private int lastProjectileCount;
	private int aciertos;
	private int fallos;
	private int shotsFired;
	private int nextHitSoundMilestone;
	private int displayedFailures;
	private int displayedPrecisionPercent;
	private int sessionTicksRemaining;
	private int spawnCooldownTicks;
	private int targetHintTicks;
	private int hintFadeShotSteps;
	private float displayedHintAlpha = HINT_START_ALPHA;
	private boolean targetHintHit;
	private boolean shotFired;
	private boolean sessionFinished;
	private boolean targetLifetimeRunning;
	private boolean countdownSoundPlayed;
	private boolean debugUsed;
	private TutorialPhase tutorialPhase = TutorialPhase.AIM;

	public TrainingMode(Game game, EnemySystem enemySystem, int sessionSeedHash) {
		this.game = game;
		this.enemySystem = enemySystem;
		this.sessionSeedHash = sessionSeedHash;
		this.terrainRandom = new Random(sessionSeedHash);
		this.targetSpawnRandom = new Random(~sessionSeedHash);
		loadControls();
		this.tutorialThread = new TutorialThread(buildSteps());
	}

	// === Lifecycle ===

	public GameMap buildArena() {
		GameMap arena = new GameMap(ARENA_TILES_WIDE, ARENA_TILES_HIGH);
		arena.clear(TileType.SAND);
		scatterWildernessVegetation(arena);
		placePerimeterFence(arena);
		setPlayerSpawnFromTile(INTERIOR_CENTER_TILE_X, INTERIOR_CENTER_TILE_Y);
		placeCenterFence(arena, INTERIOR_CENTER_TILE_X, INTERIOR_CENTER_TILE_Y);
		placeDecorations(arena);
		arena.rebuildMinimap();
		this.arena = arena;
		return arena;
	}

	private static boolean isInsideInnerArena(int tileX, int tileY) {
		return tileX >= INTERIOR_ORIGIN_X && tileX < INTERIOR_ORIGIN_X + INTERIOR_TILES
			&& tileY >= INTERIOR_ORIGIN_Y && tileY < INTERIOR_ORIGIN_Y + INTERIOR_TILES;
	}

	private static boolean isPerimeterFenceTile(int tileX, int tileY) {
		if (isInsideInnerArena(tileX, tileY)) {
			return false;
		}
		return tileX >= INTERIOR_ORIGIN_X - 1 && tileX <= INTERIOR_ORIGIN_X + INTERIOR_TILES
			&& tileY >= INTERIOR_ORIGIN_Y - 1 && tileY <= INTERIOR_ORIGIN_Y + INTERIOR_TILES;
	}

	private void scatterWildernessVegetation(GameMap arena) {
		double p = GameConfig.TRAINING_WILDERNESS_PROP_PER_TILE;
		for (int tileY = 0; tileY < ARENA_TILES_HIGH; tileY++) {
			for (int tileX = 0; tileX < ARENA_TILES_WIDE; tileX++) {
				if (isInsideInnerArena(tileX, tileY) || isPerimeterFenceTile(tileX, tileY)) {
					continue;
				}
				if (this.terrainRandom.nextDouble() >= p) {
					continue;
				}
				if (this.terrainRandom.nextBoolean()) {
					if (arena.canPlaceObject(tileX, tileY)) {
						arena.placeSingleObject(MapObjectType.DRY_BUSH, tileX, tileY, false, false);
					}
				} else {
					placeWildernessTree(arena, this.terrainRandom, tileX, tileY);
				}
			}
		}
	}

	private void placePerimeterFence(GameMap arena) {
		for (int tileY = 0; tileY < ARENA_TILES_HIGH; tileY++) {
			for (int tileX = 0; tileX < ARENA_TILES_WIDE; tileX++) {
				if (!isPerimeterFenceTile(tileX, tileY)) {
					continue;
				}
				arena.placeSingleObject(MapObjectType.WOODEN_FENCE, tileX, tileY, true, false);
			}
		}
	}

	private void placeWildernessTree(GameMap arena, Random r, int rootTileX, int rootTileY) {
		if (!canPlaceWildernessTree(arena, rootTileX, rootTileY)) {
			return;
		}
		MapObjectType type = BORDER_TREE_TYPES[r.nextInt(BORDER_TREE_TYPES.length)];
		arena.placeObjectRect(type, rootTileX, rootTileY, borderTreeSolidMask(), borderTreeAboveMask());
	}

	private boolean canPlaceWildernessTree(GameMap arena, int rootTileX, int rootTileY) {
		if (rootTileX < 0 || rootTileY < 0 || rootTileX + BORDER_TREE_WIDTH_TILES > ARENA_TILES_WIDE
			|| rootTileY + BORDER_TREE_HEIGHT_TILES > ARENA_TILES_HIGH) {
			return false;
		}
		for (int tileX = rootTileX; tileX < rootTileX + BORDER_TREE_WIDTH_TILES; tileX++) {
			for (int tileY = rootTileY; tileY < rootTileY + BORDER_TREE_HEIGHT_TILES; tileY++) {
				if (isInsideInnerArena(tileX, tileY) || isPerimeterFenceTile(tileX, tileY)
					|| arena.getObject(tileX, tileY) != null) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean[] borderTreeSolidMask() {
		boolean[] solid = new boolean[BORDER_TREE_WIDTH_TILES * BORDER_TREE_HEIGHT_TILES];
		Arrays.fill(solid, true);
		return solid;
	}

	private boolean[] borderTreeAboveMask() {
		boolean[] above = new boolean[BORDER_TREE_WIDTH_TILES * BORDER_TREE_HEIGHT_TILES];
		Arrays.fill(above, true);
		return above;
	}

	private void setPlayerSpawnFromTile(int centerTileX, int centerTileY) {
		this.playerSpawnWorldX = tileToWorldCenter(centerTileX);
		this.playerSpawnWorldY = tileToWorldCenter(centerTileY);
	}

	private static int tileToWorldCenter(int tile) {
		return tile * GameConfig.TILE_SIZE + GameConfig.TILE_SIZE / 2;
	}

	private static boolean isInsideArenaBounds(int tileX, int tileY) {
		return isInsideInnerArena(tileX, tileY);
	}

	private void placeCenterFence(GameMap arena, int centerTileX, int centerTileY) {
		for (int tileX = centerTileX - CENTER_FENCE_HALF_SIZE_TILES; tileX <= centerTileX + CENTER_FENCE_HALF_SIZE_TILES; tileX++) {
			placeCenterFenceTile(arena, tileX, centerTileY - CENTER_FENCE_HALF_SIZE_TILES);
			placeCenterFenceTile(arena, tileX, centerTileY + CENTER_FENCE_HALF_SIZE_TILES);
		}
		for (int tileY = centerTileY - CENTER_FENCE_HALF_SIZE_TILES + 1; tileY < centerTileY + CENTER_FENCE_HALF_SIZE_TILES; tileY++) {
			placeCenterFenceTile(arena, centerTileX - CENTER_FENCE_HALF_SIZE_TILES, tileY);
			placeCenterFenceTile(arena, centerTileX + CENTER_FENCE_HALF_SIZE_TILES, tileY);
		}
	}

	private void placeCenterFenceTile(GameMap arena, int tileX, int tileY) {
		if (!isInsideArenaBounds(tileX, tileY)) {
			return;
		}
		arena.placeSingleObject(MapObjectType.WOODEN_FENCE, tileX, tileY, true, false);
	}

	private void placeDecorations(GameMap arena) {
		int loX = INTERIOR_ORIGIN_X;
		int hiX = INTERIOR_ORIGIN_X + INTERIOR_TILES;
		int loY = INTERIOR_ORIGIN_Y;
		int hiY = INTERIOR_ORIGIN_Y + INTERIOR_TILES;
		int placed = 0;
		for (int attempt = 0; attempt < RANDOM_SPAWN_ATTEMPTS && placed < DECORATION_COUNT; attempt++) {
			int tileX = this.terrainRandom.nextInt(loX, hiX);
			int tileY = this.terrainRandom.nextInt(loY, hiY);
			if (isInsideCenterFence(tileX, tileY) || !arena.canPlaceObject(tileX, tileY)) {
				continue;
			}
			arena.placeSingleObject(MapObjectType.DRY_BUSH, tileX, tileY, false, false);
			placed++;
		}
	}

	public void start() {
		this.targetSpawnRandom.setSeed(~this.sessionSeedHash);
		spawnInitialEnemies();
	}

	public void shutdown() {
		this.tutorialThread.skip();
		try {
			this.tutorialThread.join(TUTORIAL_JOIN_TIMEOUT_MS);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
		saveControls();
	}

	// === Enemy spawn / sync ===

	private void spawnInitialEnemies() {
		this.enemySystem.clear();
		ArrayList<Enemy> enemies = (ArrayList<Enemy>) this.enemySystem.getEnemies();
		enemies.add(createEnemy());
		this.lastEnemyCount = enemies.size();
		this.lastProjectileCount = 0;
		this.aciertos = 0;
		this.fallos = 0;
		this.shotsFired = 0;
		this.nextHitSoundMilestone = 10;
		this.displayedFailures = 0;
		this.displayedPrecisionPercent = 0;
		this.sessionTicksRemaining = SESSION_DURATION_TICKS;
		this.spawnCooldownTicks = TARGET_SPAWN_INTERVAL_TICKS;
		this.targetHintTicks = 0;
		this.hintFadeShotSteps = 0;
		this.displayedHintAlpha = HINT_START_ALPHA;
		this.targetHintHit = false;
		this.shotFired = false;
		this.sessionFinished = false;
		this.targetLifetimeRunning = false;
		this.countdownSoundPlayed = false;
		this.debugUsed = false;
		this.tutorialPhase = TutorialPhase.AIM;
	}

	private Enemy createEnemy() {
		int[] spawn = randomArenaSpawn();
		EnemyType type = EnemyType.DIANA;
		Enemy enemy = new Enemy(type, spawn[0], spawn[1], ENEMY_INITIAL_DAY_COUNT);
		enemy.setBehaviorOverride(EnemyBehavior.STATIC);
		return enemy;
	}

	private int[] randomArenaSpawn() {
		int loX = INTERIOR_ORIGIN_X;
		int hiX = INTERIOR_ORIGIN_X + INTERIOR_TILES;
		int loY = INTERIOR_ORIGIN_Y;
		int hiY = INTERIOR_ORIGIN_Y + INTERIOR_TILES;
		for (int attempt = 0; attempt < RANDOM_SPAWN_ATTEMPTS; attempt++) {
			int tileX = this.targetSpawnRandom.nextInt(loX, hiX);
			int tileY = this.targetSpawnRandom.nextInt(loY, hiY);
			int worldX = tileToWorldCenter(tileX);
			int worldY = tileToWorldCenter(tileY);
			if (!isInsideCenterFence(tileX, tileY) && (this.arena == null || this.arena.canPlaceObject(tileX, tileY))) {
				return new int[] { worldX, worldY };
			}
		}
		return new int[] { tileToWorldCenter(INTERIOR_CENTER_TILE_X), tileToWorldCenter(INTERIOR_CENTER_TILE_Y) };
	}

	private boolean isInsideCenterFence(int tileX, int tileY) {
		int centerTileX = INTERIOR_CENTER_TILE_X;
		int centerTileY = INTERIOR_CENTER_TILE_Y;
		return Math.abs(tileX - centerTileX) <= CENTER_FENCE_HALF_SIZE_TILES
			&& Math.abs(tileY - centerTileY) <= CENTER_FENCE_HALF_SIZE_TILES;
	}

	private void syncEnemyCountToControls(int replacementCount) {
		ArrayList<Enemy> enemies = (ArrayList<Enemy>) this.enemySystem.getEnemies();
		int desired = this.controls.getEnemyCount();
		while (replacementCount > 0 && enemies.size() < desired) {
			enemies.add(createEnemy());
			replacementCount--;
		}
		if (enemies.size() < desired) {
			if (this.spawnCooldownTicks > 0) {
				this.spawnCooldownTicks--;
				return;
			}
			enemies.add(createEnemy());
			this.spawnCooldownTicks = TARGET_SPAWN_INTERVAL_TICKS;
		}
		while (enemies.size() > desired && !enemies.isEmpty()) {
			enemies.remove(enemies.size() - 1);
		}
		this.lastEnemyCount = enemies.size();
	}

	public void resetArena() {
		ArrayList<Enemy> enemies = (ArrayList<Enemy>) this.enemySystem.getEnemies();
		enemies.clear();
		spawnInitialEnemies();
	}

	// === Update tick ===

	public void update(Player player, GameInput input, ProjectileSystem projectileSystem) {
		markDebugUsedIfNeeded();
		if (this.sessionFinished) {
			handleFinalInput(input);
			return;
		}
		if (player != null && player.isDead()) {
			handleDeadInput(input);
			return;
		}
		readPanelInput(input);
		updateTutorialFlow(projectileSystem);
		updateHintAlpha();
		if (isWaitingForFirstShot()) {
			return;
		}
		updateProjectileFailures(projectileSystem);
		updateSessionTimer(projectileSystem);
		int replacementCount = awardScoreForDestroyedTargets();
		replacementCount += removeExpiredTargets();
		syncEnemyCountToControls(replacementCount);
	}

	public void notifyShotFired() {
		this.shotFired = true;
		this.shotsFired++;
		if (this.tutorialPhase != TutorialPhase.NORMAL) {
			this.hintFadeShotSteps++;
		}
	}

	public boolean isSessionFinished() {
		return this.sessionFinished;
	}

	public boolean isWaitingForFirstShot() {
		return this.tutorialPhase == TutorialPhase.AIM;
	}

	private void updateTutorialFlow(ProjectileSystem projectileSystem) {
		int projectileCount = projectileSystem == null ? 0 : projectileSystem.getProjectiles().size();
		if (this.tutorialPhase == TutorialPhase.AIM && (this.shotFired || projectileCount > this.lastProjectileCount)) {
			this.tutorialPhase = TutorialPhase.TARGETS;
			this.targetHintTicks = 0;
			this.hintFadeShotSteps = 0;
			this.displayedHintAlpha = HINT_START_ALPHA;
			this.targetHintHit = false;
		}
		this.shotFired = false;
		this.lastProjectileCount = projectileCount;
		if (this.tutorialPhase == TutorialPhase.TARGETS) {
			this.targetHintTicks++;
			if (isHintFullyFaded()) {
				this.tutorialPhase = TutorialPhase.NORMAL;
				startTargetLifetime();
				return;
			}
			if (this.targetHintHit && this.targetHintTicks >= TARGET_HINT_MIN_TICKS) {
				this.tutorialPhase = TutorialPhase.TIMER_NOTICE;
				this.targetHintTicks = 0;
				this.hintFadeShotSteps = 0;
				this.displayedHintAlpha = HINT_START_ALPHA;
			}
		} else if (this.tutorialPhase == TutorialPhase.TIMER_NOTICE) {
			this.targetHintTicks++;
			if (isHintFullyFaded() || this.targetHintTicks >= TIMER_NOTICE_TICKS) {
				this.tutorialPhase = TutorialPhase.NORMAL;
				startTargetLifetime();
			}
		}
	}

	private void markDebugUsedIfNeeded() {
		DebugOptions debug = this.game == null ? null : this.game.getDebugOptions();
		if (debug != null && debug.hasAnyModeEnabled()) {
			this.debugUsed = true;
		}
	}

	private void startTargetLifetime() {
		if (this.targetLifetimeRunning) {
			return;
		}
		this.targetLifetimeRunning = true;
		resetTargetAnimationTicks();
	}

	private void resetTargetAnimationTicks() {
		ArrayList<Enemy> enemies = (ArrayList<Enemy>) this.enemySystem.getEnemies();
		for (int i = 0; i < enemies.size(); i++) {
			Enemy enemy = enemies.get(i);
			if (enemy.getType() == EnemyType.DIANA) {
				enemy.restoreTransientState(0, enemy.getFacing());
			}
		}
	}

	private void handleDeadInput(GameInput input) {
		drainPanelInputWhileDead(input);
		drainEndActionKeys(input);
		handleEndScreenClick(input, deathBoxX(), deathBoxY(), DEATH_BOX_WIDTH, DEATH_RESTART_BUTTON_Y, DEATH_EXIT_BUTTON_Y);
	}

	private void handleFinalInput(GameInput input) {
		drainPanelInputWhileDead(input);
		drainEndActionKeys(input);
		handleEndScreenClick(input, finalBoxX(), finalBoxY(), FINAL_BOX_WIDTH, FINAL_RESTART_BUTTON_Y, FINAL_EXIT_BUTTON_Y);
	}

	private void drainEndActionKeys(GameInput input) {
		input.consumeTrainingReset();
		input.consumeTrainingBackToMenu();
	}

	private void handleEndScreenClick(GameInput input, int boxX, int boxY, int boxWidth, int restartButtonY, int exitButtonY) {
		if (!input.consumePrimaryClick() || this.game == null) {
			return;
		}
		int mouseX = input.getMouseX();
		int mouseY = input.getMouseY();
		int buttonX = endButtonX(boxX, boxWidth);
		if (isInsideButton(mouseX, mouseY, buttonX, boxY + restartButtonY)) {
			this.game.restartTraining();
			return;
		}
		if (isInsideButton(mouseX, mouseY, buttonX, boxY + exitButtonY)) {
			this.game.exitTrainingToMenu();
		}
	}

	private static boolean isInsideButton(int mouseX, int mouseY, int buttonX, int buttonY) {
		return mouseX >= buttonX && mouseX <= buttonX + END_BUTTON_WIDTH
			&& mouseY >= buttonY && mouseY <= buttonY + END_BUTTON_HEIGHT;
	}

	private void drainPanelInputWhileDead(GameInput input) {
		input.consumeTrainingIncrement();
		input.consumeTrainingDecrement();
		input.consumeTrainingSkipTutorial();
	}

	private void readPanelInput(GameInput input) {
		if (input.consumeTrainingIncrement()) {
			this.controls.incCount();
		}
		if (input.consumeTrainingDecrement()) {
			this.controls.decCount();
		}
		if (input.consumeTrainingReset()) {
			resetArena();
		}
		if (input.consumeTrainingSkipTutorial()) {
			this.tutorialThread.skip();
		}
	}

	private void updateSessionTimer(ProjectileSystem projectileSystem) {
		if (this.sessionTicksRemaining <= 0) {
			finishSession(projectileSystem);
			return;
		}
		playCountdownSoundIfNeeded();
		this.sessionTicksRemaining--;
		if (this.sessionTicksRemaining <= 0) {
			finishSession(projectileSystem);
		}
	}

	private void finishSession(ProjectileSystem projectileSystem) {
		countPredictedFinalProjectileFailures(projectileSystem);
		updateDisplayedTrainingStats();
		this.sessionFinished = true;
	}

	private void playCountdownSoundIfNeeded() {
		if (this.countdownSoundPlayed || this.sessionTicksRemaining != COUNTDOWN_SOUND_TICKS_REMAINING) {
			return;
		}
		this.countdownSoundPlayed = true;
		this.game.getAudio().playOnce(COUNTDOWN_SOUND_PATH, COUNTDOWN_SOUND_GAIN_DB);
	}

	private int awardScoreForDestroyedTargets() {
		int currentEnemies = this.enemySystem.getEnemies().size();
		if (currentEnemies < this.lastEnemyCount) {
			int destroyedCount = this.lastEnemyCount - currentEnemies;
			this.aciertos += destroyedCount;
			playHitMilestoneSounds();
			updateDisplayedTrainingStats();
			if (this.tutorialPhase == TutorialPhase.TARGETS) {
				this.targetHintHit = true;
			}
			this.lastEnemyCount = currentEnemies;
			return destroyedCount;
		}
		this.lastEnemyCount = currentEnemies;
		return 0;
	}

	private void playHitMilestoneSounds() {
		while (this.aciertos >= this.nextHitSoundMilestone) {
			this.game.getAudio().playOnce(HIT_MILESTONE_SOUND_PATH, COUNTDOWN_SOUND_GAIN_DB);
			this.nextHitSoundMilestone += 10;
		}
	}

	private int removeExpiredTargets() {
		if (!this.targetLifetimeRunning) {
			return 0;
		}
		ArrayList<Enemy> enemies = (ArrayList<Enemy>) this.enemySystem.getEnemies();
		int removedCount = 0;
		for (int i = enemies.size() - 1; i >= 0; i--) {
			Enemy enemy = enemies.get(i);
			if (enemy.getType() == EnemyType.DIANA && enemy.getAnimationTicks() >= TARGET_LIFETIME_TICKS) {
				enemies.remove(i);
				removedCount++;
			}
		}
		if (removedCount > 0) {
			this.lastEnemyCount = Math.max(0, this.lastEnemyCount - removedCount);
		}
		return removedCount;
	}

	// === HUD render ===

	public void renderHud(Graphics2D g2) {
		drawTrainingStats(g2);
		if (this.sessionFinished) {
			drawFinalScreen(g2);
			return;
		}
		drawTutorialMessage(g2);
		drawDeathPrompt(g2);
	}

	private void drawTrainingStats(Graphics2D g2) {
		TextRenderer.drawCentered(g2, TUTORIAL_BODY_FONT, "Fallos: " + this.displayedFailures, Color.WHITE, GameConfig.SCREEN_CENTER_X - 260,
			40, TRAINING_TEXT_STYLE);
		TextRenderer.drawCentered(g2, PRECISION_FONT, "Precision", Color.WHITE, GameConfig.SCREEN_CENTER_X,
			36, TRAINING_TEXT_STYLE);
		TextRenderer.drawCentered(g2, PRECISION_FONT, precisionText(), Color.WHITE, GameConfig.SCREEN_CENTER_X,
			36 + PRECISION_VALUE_LINE_GAP, TRAINING_TEXT_STYLE);
		TextRenderer.drawCentered(g2, TUTORIAL_BODY_FONT, "Aciertos: " + this.aciertos, Color.WHITE,
			GameConfig.SCREEN_CENTER_X + 280, 40, TRAINING_TEXT_STYLE);
		if (this.tutorialPhase == TutorialPhase.NORMAL) {
			TextRenderer.drawCentered(g2, TIMER_FONT, timerText(), Color.WHITE, outerFenceScreenCenterX(), trainingPromptBaselineY(),
				TRAINING_TEXT_STYLE);
		}
	}

	private void updateProjectileFailures(ProjectileSystem projectileSystem) {
		if (projectileSystem == null) {
			return;
		}
		java.util.List<Projectile> projectiles = projectileSystem.getProjectiles();
		int newFailures = 0;
		for (int i = 0; i < projectiles.size(); i++) {
			Projectile projectile = projectiles.get(i);
			if (projectile == null || projectile.isCountedTrainingFailure()
				|| isWorldInsideTrainingPerimeter(projectile.getWorldX(), projectile.getWorldY())) {
				continue;
			}
			projectile.markCountedTrainingFailure();
			newFailures++;
		}
		if (newFailures > 0) {
			this.fallos += newFailures;
			updateDisplayedTrainingStats();
		}
	}

	private void countPredictedFinalProjectileFailures(ProjectileSystem projectileSystem) {
		if (projectileSystem == null) {
			return;
		}
		java.util.List<Projectile> projectiles = projectileSystem.getProjectiles();
		int predictedFailures = 0;
		for (int i = 0; i < projectiles.size(); i++) {
			Projectile projectile = projectiles.get(i);
			if (projectile == null || projectile.isCountedTrainingFailure()
				|| !isWorldInsideTrainingPerimeter(projectile.getWorldX(), projectile.getWorldY())) {
				continue;
			}
			if (projectileTrajectoryWouldHitTarget(projectile)) {
				continue;
			}
			projectile.markCountedTrainingFailure();
			predictedFailures++;
		}
		if (predictedFailures > 0) {
			this.fallos += predictedFailures;
		}
	}

	private boolean projectileTrajectoryWouldHitTarget(Projectile projectile) {
		double startX = projectile.getPreciseWorldX();
		double startY = projectile.getPreciseWorldY();
		double velocityX = projectile.getVelocityX();
		double velocityY = projectile.getVelocityY();
		double maxTicks = Math.min(projectile.getLifeTicks(), ticksUntilTrajectoryLeavesTrainingPerimeter(startX, startY, velocityX, velocityY));
		if (maxTicks <= 0.0) {
			return false;
		}
		double endX = startX + velocityX * maxTicks;
		double endY = startY + velocityY * maxTicks;
		java.util.List<Enemy> enemies = this.enemySystem.getEnemies();
		int projectilePadding = Math.max(projectile.getType().getDrawWidth(), projectile.getType().getDrawHeight()) / 2;
		for (int i = 0; i < enemies.size(); i++) {
			Enemy enemy = enemies.get(i);
			if (enemy == null || enemy.isDead()) {
				continue;
			}
			int hitRadius = enemy.getType().getCollisionRadius() + projectilePadding;
			if (segmentIntersectsCircle(startX, startY, endX, endY, enemy.getWorldX(), enemy.getWorldY(), hitRadius)) {
				return true;
			}
		}
		return false;
	}

	private double ticksUntilTrajectoryLeavesTrainingPerimeter(double startX, double startY, double velocityX, double velocityY) {
		double left = getFailurePerimeterLeftWorldX();
		double right = left + getFailurePerimeterWidthWorld();
		double top = getFailurePerimeterTopWorldY();
		double bottom = top + getFailurePerimeterHeightWorld();
		double maxTicks = Double.POSITIVE_INFINITY;
		if (velocityX > 0.0) {
			maxTicks = Math.min(maxTicks, (right - startX) / velocityX);
		} else if (velocityX < 0.0) {
			maxTicks = Math.min(maxTicks, (left - startX) / velocityX);
		}
		if (velocityY > 0.0) {
			maxTicks = Math.min(maxTicks, (bottom - startY) / velocityY);
		} else if (velocityY < 0.0) {
			maxTicks = Math.min(maxTicks, (top - startY) / velocityY);
		}
		return Double.isFinite(maxTicks) ? Math.max(0.0, maxTicks) : 0.0;
	}

	private static boolean segmentIntersectsCircle(double startX, double startY, double endX, double endY, double centerX, double centerY, double radius) {
		double segmentX = endX - startX;
		double segmentY = endY - startY;
		double lengthSquared = segmentX * segmentX + segmentY * segmentY;
		if (lengthSquared <= 0.0001) {
			double deltaX = centerX - startX;
			double deltaY = centerY - startY;
			return deltaX * deltaX + deltaY * deltaY <= radius * radius;
		}
		double t = ((centerX - startX) * segmentX + (centerY - startY) * segmentY) / lengthSquared;
		t = Math.max(0.0, Math.min(1.0, t));
		double closestX = startX + segmentX * t;
		double closestY = startY + segmentY * t;
		double deltaX = centerX - closestX;
		double deltaY = centerY - closestY;
		return deltaX * deltaX + deltaY * deltaY <= radius * radius;
	}

	private String precisionText() {
		return this.displayedPrecisionPercent + "%";
	}

	private void updateDisplayedTrainingStats() {
		int effectiveShots = effectiveShotsFired();
		this.displayedFailures = this.fallos;
		if (effectiveShots <= 0) {
			this.displayedPrecisionPercent = 0;
			return;
		}
		this.displayedPrecisionPercent = (int) Math.round((this.aciertos * 100.0) / effectiveShots);
	}

	private int effectiveShotsFired() {
		DebugOptions debug = this.game == null ? null : this.game.getDebugOptions();
		if (debug != null && debug.shouldForceTrainingPerfectAccuracy()) {
			return this.aciertos;
		}
		return this.aciertos + this.fallos;
	}

	private static boolean isWorldInsideTrainingPerimeter(int worldX, int worldY) {
		int tileX = Math.floorDiv(worldX, GameConfig.TILE_SIZE);
		int tileY = Math.floorDiv(worldY, GameConfig.TILE_SIZE);
		return tileX >= INTERIOR_ORIGIN_X && tileX < INTERIOR_ORIGIN_X + INTERIOR_TILES
			&& tileY >= INTERIOR_ORIGIN_Y && tileY < INTERIOR_ORIGIN_Y + INTERIOR_TILES;
	}

	private int finalScore() {
		double multiplicador = 1;
		if (this.displayedPrecisionPercent < 100) {
			multiplicador = 1;
		}
		else
			multiplicador = 1.5;
		return (int) Math.round((this.aciertos * (100 + this.displayedPrecisionPercent) / 10.0) * multiplicador);
	}

	private String timerText() {
		int seconds = Math.max(0, (this.sessionTicksRemaining + GameConfig.TARGET_FPS - 1) / GameConfig.TARGET_FPS);
		return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
	}

	// === Tutorial render ===

	private void drawTutorialMessage(Graphics2D g2) {
		float hintAlpha = this.displayedHintAlpha;
		if (hintAlpha <= 0.0f) {
			return;
		}
		Composite previousComposite = g2.getComposite();
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hintAlpha));
		int hintCenterX = outerFenceScreenCenterX();
		int hintBaselineY = trainingPromptBaselineY();
		if (this.tutorialPhase == TutorialPhase.TIMER_NOTICE) {
			TextRenderer.drawCentered(g2, HINT_FONT, TIMER_NOTICE_TEXT, Color.WHITE, hintCenterX, hintBaselineY,
				TRAINING_TEXT_STYLE);
			g2.setComposite(previousComposite);
			return;
		}
		String message = tutorialMessage();
		if (message == null) {
			g2.setComposite(previousComposite);
			return;
		}
		TextRenderer.drawCentered(g2, HINT_FONT, message, Color.WHITE, hintCenterX, hintBaselineY,
			TRAINING_TEXT_STYLE);
		g2.setComposite(previousComposite);
	}

	private int outerFenceScreenCenterX() {
		return tileToWorldCenter(INTERIOR_CENTER_TILE_X) - this.game.getCameraCenterWorldX() + GameConfig.SCREEN_CENTER_X;
	}

	private int outerFenceBottomScreenY() {
		int bottomFenceWorldY = (INTERIOR_ORIGIN_Y + INTERIOR_TILES + 1) * GameConfig.TILE_SIZE;
		return bottomFenceWorldY - this.game.getCameraCenterWorldY() + GameConfig.SCREEN_CENTER_Y;
	}

	private int trainingPromptBaselineY() {
		return Math.min(outerFenceBottomScreenY() + HINT_BASELINE_BELOW_OUTER_FENCE_BOTTOM,
			GameConfig.SCREEN_HEIGHT - HINT_BOTTOM_SCREEN_MARGIN);
	}

	private float hintAlpha() {
		return Math.max(0.0f, HINT_START_ALPHA - this.hintFadeShotSteps * HINT_SHOT_ALPHA_STEP);
	}

	private void updateHintAlpha() {
		float targetAlpha = hintAlpha();
		if (this.displayedHintAlpha <= targetAlpha) {
			this.displayedHintAlpha = targetAlpha;
			return;
		}
		this.displayedHintAlpha = Math.max(targetAlpha, this.displayedHintAlpha - HINT_ALPHA_FADE_STEP);
	}

	private boolean isHintFullyFaded() {
		return hintAlpha() <= 0.0f && this.displayedHintAlpha <= 0.0f;
	}

	private String tutorialMessage() {
		if (this.tutorialPhase == TutorialPhase.AIM) {
			return AIM_HINT_TEXT;
		}
		if (this.tutorialPhase == TutorialPhase.TARGETS) {
			return TARGET_HINT_TEXT;
		}
		return null;
	}

	// === Death prompt ===

	private void drawDeathPrompt(Graphics2D g2) {
		Player player = this.game == null ? null : this.game.getPlayer();
		if (player == null || !player.isDead()) {
			return;
		}
		int boxX = deathBoxX();
		int boxY = deathBoxY();
		drawTranslucentBox(g2, boxX, boxY, DEATH_BOX_WIDTH, DEATH_BOX_HEIGHT, DEATH_BOX_BG_ALPHA, DEATH_BOX_BG_COLOR, DEATH_BOX_BORDER_COLOR);
		drawDeathTitle(g2, boxX, boxY);
		drawEndButton(g2, RESTART_BUTTON_TEXT, endButtonX(boxX, DEATH_BOX_WIDTH), boxY + DEATH_RESTART_BUTTON_Y);
		drawEndButton(g2, EXIT_BUTTON_TEXT, endButtonX(boxX, DEATH_BOX_WIDTH), boxY + DEATH_EXIT_BUTTON_Y);
	}

	private static int deathBoxX() {
		return (GameConfig.SCREEN_WIDTH - DEATH_BOX_WIDTH) / 2;
	}

	private static int deathBoxY() {
		return GameConfig.SCREEN_HEIGHT / 2 + DEATH_BOX_Y_OFFSET_FROM_CENTER;
	}

	private static void drawDeathTitle(Graphics2D g2, int boxX, int boxY) {
		TextRenderer.drawCentered(g2, TUTORIAL_TITLE_FONT, DEATH_TITLE_TEXT, DEATH_BOX_TITLE_COLOR,
			boxX + DEATH_BOX_WIDTH / 2, boxY + DEATH_BOX_TITLE_BASELINE_Y, TRAINING_TEXT_STYLE);
	}

	private void drawFinalScreen(Graphics2D g2) {
		int boxX = finalBoxX();
		int boxY = finalBoxY();
		drawTranslucentBox(g2, boxX, boxY, FINAL_BOX_WIDTH, FINAL_BOX_HEIGHT, DEATH_BOX_BG_ALPHA, DEATH_BOX_BG_COLOR, PANEL_BORDER_COLOR);
		drawFinalCenteredLine(g2, FINAL_TITLE_TEXT, TUTORIAL_TITLE_FONT, PANEL_TITLE_COLOR, boxX, boxY + FINAL_BOX_TITLE_BASELINE_Y);
		drawFinalCenteredLine(g2, "Aciertos: " + this.aciertos, TUTORIAL_BODY_FONT, DEATH_BOX_BODY_COLOR, boxX, boxY + FINAL_BOX_HITS_Y);
		drawFinalCenteredLine(g2, "Precision: " + precisionText(), TUTORIAL_BODY_FONT, DEATH_BOX_BODY_COLOR, boxX, boxY + FINAL_BOX_PRECISION_Y);
		Color finalScoreColor = this.debugUsed ? Color.RED : DEATH_BOX_BODY_COLOR;
		String finalScoreText = "Puntaje final: " + finalScore() + (this.debugUsed ? " (debug)" : "");
		drawFinalCenteredLine(g2, finalScoreText, TUTORIAL_BODY_FONT, finalScoreColor, boxX, boxY + FINAL_BOX_FINAL_SCORE_Y);
		drawEndButton(g2, RESTART_BUTTON_TEXT, endButtonX(boxX, FINAL_BOX_WIDTH), boxY + FINAL_RESTART_BUTTON_Y);
		drawEndButton(g2, EXIT_BUTTON_TEXT, endButtonX(boxX, FINAL_BOX_WIDTH), boxY + FINAL_EXIT_BUTTON_Y);
	}

	private static int finalBoxX() {
		return (GameConfig.SCREEN_WIDTH - FINAL_BOX_WIDTH) / 2;
	}

	private static int finalBoxY() {
		return (GameConfig.SCREEN_HEIGHT - FINAL_BOX_HEIGHT) / 2;
	}

	private static void drawFinalCenteredLine(Graphics2D g2, String text, Font font, Color color, int boxX, int baselineY) {
		TextRenderer.drawCentered(g2, font, text, color, boxX + FINAL_BOX_WIDTH / 2, baselineY, TRAINING_TEXT_STYLE);
	}

	private static int endButtonX(int boxX, int boxWidth) {
		return boxX + (boxWidth - END_BUTTON_WIDTH) / 2;
	}

	private static void drawEndButton(Graphics2D g2, String text, int x, int y) {
		g2.setColor(GameTheme.DIRT);
		g2.fillRect(x, y, END_BUTTON_WIDTH, END_BUTTON_HEIGHT);
		g2.setColor(Color.BLACK);
		g2.drawRect(x, y, END_BUTTON_WIDTH, END_BUTTON_HEIGHT);
		int textX = TextRenderer.centeredX(g2, TUTORIAL_TITLE_FONT, text, x + END_BUTTON_WIDTH / 2);
		FontMetrics metrics = g2.getFontMetrics();
		int textY = y + (END_BUTTON_HEIGHT + metrics.getAscent()) / 2 - 4;
		TextRenderer.draw(g2, TUTORIAL_TITLE_FONT, text, DEATH_BOX_BODY_COLOR, textX, textY, TRAINING_TEXT_STYLE);
	}

	// === Render helpers ===

	private static void drawTranslucentBox(Graphics2D g2, int x, int y, int width, int height, float alpha, Color fill, Color border) {
		Composite previous = g2.getComposite();
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		g2.setColor(fill);
		g2.fillRect(x, y, width, height);
		g2.setComposite(previous);
		g2.setColor(border);
		g2.drawRect(x, y, width, height);
	}

	// === Persistencia ===

	private void loadControls() {
		File file = new File(CONFIG_PATH);
		if (!file.exists()) {
			return;
		}
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				applyConfigLine(line);
			}
		} catch (IOException ignored) {
		}
	}

	private void applyConfigLine(String line) {
		int separator = line.indexOf(CONFIG_KEY_VALUE_SEPARATOR);
		if (separator < 0) {
			return;
		}
		String key = line.substring(0, separator).trim();
		String value = line.substring(separator + 1).trim();
		if (CONFIG_KEY_COUNT.equals(key)) {
			applyCountValue(value);
		}
	}

	private void applyCountValue(String value) {
		try {
			this.controls.setEnemyCount(Integer.parseInt(value));
		} catch (NumberFormatException ignored) {
		}
	}

	private void saveControls() {
		File file = new File(CONFIG_PATH);
		ensureParentDirExists(file);
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
			writer.write(CONFIG_KEY_COUNT + CONFIG_KEY_VALUE_SEPARATOR + this.controls.getEnemyCount());
			writer.newLine();
		} catch (IOException ignored) {
		}
	}

	private static void ensureParentDirExists(File file) {
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
	}

	// === Public state ===

	public int getPlayerSpawnWorldX() {
		return this.playerSpawnWorldX;
	}

	public int getPlayerSpawnWorldY() {
		return this.playerSpawnWorldY;
	}

	public int getFailurePerimeterLeftWorldX() {
		return INTERIOR_ORIGIN_X * GameConfig.TILE_SIZE;
	}

	public int getFailurePerimeterTopWorldY() {
		return INTERIOR_ORIGIN_Y * GameConfig.TILE_SIZE;
	}

	public int getFailurePerimeterWidthWorld() {
		return INTERIOR_TILES * GameConfig.TILE_SIZE;
	}

	public int getFailurePerimeterHeightWorld() {
		return INTERIOR_TILES * GameConfig.TILE_SIZE;
	}

	// === Tutorial steps ===

	private static List<TutorialStep> buildSteps() {
		return Arrays.asList(
			new TutorialStep(
				TutorialEventType.FIRST_MOVEMENT,
				STEP_MOVEMENT_MIN_MS,
				STEP_MOVEMENT_MAX_MS
			),
			new TutorialStep(
				TutorialEventType.FIRST_SHOT,
				STEP_DEFAULT_MIN_MS,
				STEP_SHOT_MAX_MS
			),
			new TutorialStep(
				TutorialEventType.FIRST_KILL,
				STEP_DEFAULT_MIN_MS,
				STEP_KILL_MAX_MS
			)
		);
	}

	private enum TutorialPhase {
		AIM,
		TARGETS,
		TIMER_NOTICE,
		NORMAL
	}
}
