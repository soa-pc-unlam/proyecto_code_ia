package SheriffsssPackage;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Game extends JPanel implements Runnable {
	private static final long serialVersionUID = 1L;
	private static final String MENU_MUSIC = "sounds/Menu.wav";
	private static final String NIGHT_MUSIC = "sounds/Night.wav";
	private static final String DEATH_SOUND = "sounds/OminousChatter.wav";
	private static final String DEFAULT_WEAPON_ATTACK_SOUND = "sounds/Shot.wav";
	private static final float MUSIC_GAIN_DB = -5f;
	private static final float WEAPON_GAIN_DB = 0f;
	private static final float ENEMY_HIT_GAIN_DB = 0f;
	private static final int REVOLVER_FLASH_TICKS = 7;
	private static final int REVOLVER_FLASH_RADIUS_TILES = 5;
	private static final int HIT_MARKER_TICKS = 8;
	private static final int SHOT_FACING_LOCK_TICKS = GameConfig.TARGET_FPS;
	private static final double MOVING_ACCURACY_PENALTY = 0.2;
	private static final double MOVING_ACCURACY_MIN_LINEAR_SPEED = 0.1;
	private static final int SETTINGS_SLIDER_NONE = 0;
	private static final int SETTINGS_SLIDER_MUSIC = 1;
	private static final int SETTINGS_SLIDER_SFX = 2;
	private static final int SETTINGS_SLIDER_RESOLUTION = 3;
	private static final int INFO_MESSAGE_SLOTS = 4;
	private static final int EQUIPMENT_PANEL_WIDTH = 250;
	private static final int EQUIPMENT_PANEL_HEIGHT = 310;
	private static final int EQUIPMENT_SELECTOR_X_OFFSET = 28;
	private static final int EQUIPMENT_SELECTOR_Y_OFFSET = 216;
	private static final int EQUIPMENT_SELECTOR_WIDTH = 194;
	private static final int EQUIPMENT_SELECTOR_HEIGHT = 74;
	private static final int EQUIPMENT_LIST_GAP = 8;
	private static final int EQUIPMENT_LIST_WIDTH = 330;
	private static final int EQUIPMENT_LIST_ROW_HEIGHT = 56;
	private final AssetManager assets;
	private final AudioManager audio;
	private final GameInput input;
	private final MenuRenderer menuRenderer;
	private final GameRenderer renderer;
	private final DayNightCycle dayNightCycle = new DayNightCycle();
	private final EnemySystem enemySystem = new EnemySystem();
	private final ProjectileSystem projectileSystem = new ProjectileSystem();
	private final DebugOptions debugOptions = new DebugOptions();
	private TrainingMode trainingMode;
	private boolean trainingActive;
	private final String[] infoMessages = new String[INFO_MESSAGE_SLOTS];
	private final int[] infoMessageTicks = new int[INFO_MESSAGE_SLOTS];
	private final HashSet<String> unavailableSfxPaths = new HashSet<String>();

	private volatile Thread gameThread;
	private GameMap map;
	private Player player;
	private PlayerRuntimeState localRuntimeState = new PlayerRuntimeState();
	private State state = State.MENU;
	private boolean usingTool;
	private boolean blockPrimaryGameplayUntilRelease;
	private boolean primaryGameplayPressedThisFrame;
	private MapObject toolTargetObject;
	private int toolUseTicks;
	private int toolUseDurationTicks = 1;
	private ItemDefinition toolAnimationDefinition;
	private int toolAnimationTicksRemaining;
	private String settingsMessage = "";
	private int activeSettingsSlider;
	private boolean activeDebugTrajectorySlider;
	private boolean debugPanelPrimaryHeld;
	private Random weaponRandom = new Random(0L);
	private long frameCount;
	private String activeMusicPath;
	private boolean deathOverlayActive;
	private double cameraZoom = GameConfig.CAMERA_MIN_ZOOM;
	private int revolverFlashWorldX;
	private int revolverFlashWorldY;
	private int revolverFlashTicks;
	private int hitMarkerTicks;
	private int shotFacingLockTicks;
	private JFrame window;
	private Dimension windowedSize = new Dimension(GameConfig.BASE_SCREEN_WIDTH, GameConfig.BASE_SCREEN_HEIGHT);
	private boolean fullscreen;
	private int pendingWindowResolutionIndex;
	private boolean pendingFullscreen;
	private int lastViewportWidth = GameConfig.BASE_SCREEN_WIDTH;
	private int lastViewportHeight = GameConfig.BASE_SCREEN_HEIGHT;
	private volatile boolean shuttingDown;

	public Game() {
		GameConfig.loadDisplayPreferences();
		this.windowedSize = GameConfig.getWindowResolution();
		this.fullscreen = GameConfig.isFullscreenPreferred();
		this.pendingWindowResolutionIndex = GameConfig.getWindowResolutionIndex();
		this.pendingFullscreen = this.fullscreen;
		this.assets = new AssetManager();
		this.audio = new AudioManager();
		this.audio.setMusicVolume(GameConfig.getMusicVolume());
		this.audio.setSfxVolume(GameConfig.getSfxVolume());
		this.input = new GameInput();
		this.menuRenderer = new MenuRenderer(this.assets);
		this.renderer = new GameRenderer(this.assets, this.menuRenderer);

		setPreferredSize(this.windowedSize);
		setDoubleBuffered(true);
		setFocusable(true);
		setFocusTraversalKeysEnabled(false);
		setLayout(null);

		addKeyListener(this.input);
		addMouseListener(this.input);
		addMouseWheelListener(this.input);
		addMouseMotionListener(this.input);

		setCursor(this.assets.getCursor(CursorType.IDLE));
	}

	public void startGame() {
		if (this.shuttingDown || this.gameThread != null) {
			return;
		}
		this.gameThread = new Thread(this, "SheriffsssGameLoop");
		this.gameThread.start();
		requestFocusInWindow();
	}

	public void setWindow(JFrame window) {
		this.window = window;
		if (this.fullscreen) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					applyFullscreen(true);
				}
			});
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		syncViewportSize();
		this.renderer.render((Graphics2D) g, this);
	}

	private void syncViewportSize() {
		int viewportWidth = getWidth() > 0 ? getWidth() : GameConfig.BASE_SCREEN_WIDTH;
		int viewportHeight = getHeight() > 0 ? getHeight() : GameConfig.BASE_SCREEN_HEIGHT;
		if (viewportWidth == this.lastViewportWidth && viewportHeight == this.lastViewportHeight) {
			return;
		}
		GameConfig.setViewportSize(viewportWidth, viewportHeight);
		this.lastViewportWidth = viewportWidth;
		this.lastViewportHeight = viewportHeight;
	}

	private void updateFullscreenToggle() {
		if (!this.input.consumeFullscreenToggle()) {
			return;
		}
		setFullscreen(!this.fullscreen);
	}

	private void setFullscreen(boolean fullscreen) {
		this.fullscreen = fullscreen;
		this.pendingFullscreen = fullscreen;
		this.pendingWindowResolutionIndex = GameConfig.getWindowResolutionIndex();
		GameConfig.setFullscreenPreferred(fullscreen);
		GameConfig.saveDisplayPreferences();
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				applyFullscreen(fullscreen);
			}
		});
	}

	private void applyFullscreen(boolean fullscreen) {
		JFrame targetWindow = this.window;
		if (targetWindow == null) {
			targetWindow = (JFrame) SwingUtilities.getWindowAncestor(this);
			this.window = targetWindow;
		}
		if (targetWindow == null) {
			return;
		}
		if (fullscreen) {
			targetWindow.dispose();
			targetWindow.setUndecorated(true);
			targetWindow.setResizable(false);
			targetWindow.setExtendedState(JFrame.MAXIMIZED_BOTH);
			targetWindow.setVisible(true);
		} else {
			targetWindow.dispose();
			targetWindow.setUndecorated(false);
			targetWindow.setExtendedState(JFrame.NORMAL);
			targetWindow.setResizable(false);
			setPreferredSize(this.windowedSize);
			targetWindow.pack();
			targetWindow.setLocationRelativeTo(null);
			targetWindow.setVisible(true);
		}
		targetWindow.revalidate();
		targetWindow.repaint();
		refreshViewportAfterWindowChange();
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				refreshViewportAfterWindowChange();
			}
		});
	}

	private void applyWindowedResolution() {
		JFrame targetWindow = this.window;
		if (targetWindow == null) {
			targetWindow = (JFrame) SwingUtilities.getWindowAncestor(this);
			this.window = targetWindow;
		}
		if (targetWindow == null) {
			return;
		}
		setPreferredSize(this.windowedSize);
		targetWindow.pack();
		targetWindow.setLocationRelativeTo(null);
		targetWindow.revalidate();
		targetWindow.repaint();
		refreshViewportAfterWindowChange();
	}

	private void refreshViewportAfterWindowChange() {
		syncViewportSize();
		revalidate();
		repaint();
		requestFocusInWindow();
	}

	@Override
	public void run() {
		double drawInterval = 1000000000.0 / GameConfig.TARGET_FPS;
		double delta = 0.0;
		long lastTime = System.nanoTime();

		while (!this.shuttingDown && Thread.currentThread() == this.gameThread) {
			long currentTime = System.nanoTime();
			delta += (currentTime - lastTime) / drawInterval;
			lastTime = currentTime;

			while (delta >= 1.0) {
				updateGame();
				repaint();
				this.frameCount++;
				delta--;
			}
		}
	}

	private void updateGame() {
		if (this.shuttingDown) {
			return;
		}
		syncViewportSize();
		updateFullscreenToggle();
		updateDebugMenuInput();
		if (this.state != State.PLAYING || this.player == null || this.player.isDead() || this.deathOverlayActive) {
			this.input.consumeZoomWheelSteps();
			this.input.consumeZoomKeySteps();
		}
		updateRevolverFlash();
		updateShotFeedback();
		if (this.state == State.MENU) {
			updateMenu();
		} else if (this.state == State.MENU_SETTINGS) {
			updateMenuSettings();
		} else if (this.state == State.PLAYING) {
			updatePlaying();
		} else if (this.state == State.SETTINGS) {
			updateSettings();
		}
		if (this.trainingActive && this.trainingMode != null
			&& (this.state == State.PLAYING || this.state == State.DEAD)
			&& this.player != null) {
			this.trainingMode.update(this.player, this.input, this.projectileSystem);
		}
		updateInfoMessages();
		updateMusic();
		updateCursor();
	}

	private void updateDebugMenuInput() {
		if (!this.trainingActive) {
			this.debugOptions.setMenuOpen(false);
			this.activeDebugTrajectorySlider = false;
			this.debugPanelPrimaryHeld = this.input.isPrimaryHeld();
			return;
		}
		if (!this.debugOptions.isMenuOpen()) {
			this.activeDebugTrajectorySlider = false;
			this.debugPanelPrimaryHeld = this.input.isPrimaryHeld();
			return;
		}
		if (!this.input.isPrimaryHeld()) {
			this.activeDebugTrajectorySlider = false;
			this.debugPanelPrimaryHeld = false;
			return;
		}
		if (this.activeDebugTrajectorySlider) {
			this.debugOptions.setBulletTrajectoryLimitFromMouse(this.input.getMouseX());
			this.input.consumePrimaryClick();
			return;
		}
		if (!this.debugPanelPrimaryHeld) {
			boolean handled = this.debugOptions.handleClick(this.input.getMouseX(), this.input.getMouseY());
			this.debugPanelPrimaryHeld = true;
			if (handled) {
				equipRequestedTrainingDebugWeapon();
				this.input.consumePrimaryClick();
				this.activeDebugTrajectorySlider = this.debugOptions.isTrajectorySliderHovered(this.input.getMouseX(), this.input.getMouseY());
			}
		}
	}

	private void equipRequestedTrainingDebugWeapon() {
		if (!this.debugOptions.consumeUnlockAllWeaponsRequest() || this.player == null || !this.trainingActive) {
			return;
		}
		if (this.debugOptions.shouldUnlockAllWeapons()) {
			unlockAllTrainingWeapons(this.player);
			this.player.getEquipment().openMenu();
		} else {
			resetTrainingWeapon(this.player);
		}
	}

	private void updateMenu() {
		if (!this.input.consumePrimaryClick()) {
			return;
		}
		if (this.menuRenderer.isExitButtonHovered(this.input.getMouseX(), this.input.getMouseY())) {
			shutdownApplication();
			return;
		}
		if (this.menuRenderer.isTrainingButtonHovered(this.input.getMouseX(), this.input.getMouseY())) {
			startTraining(true);
			return;
		}
		if (this.menuRenderer.isTrainingSettingsButtonHovered(this.input.getMouseX(), this.input.getMouseY())) {
			openMenuSettings();
			return;
		}
	}


	// El tutorial corre en su propio thread (SheriffsssTutorial). Ver THREADING.md.
	private static final String TRAINING_PLAYER_NAME = "Trainee";
	private static final double TRAINING_DAY_PROGRESS = 1;

	private void startTraining() {
		startTraining(false);
	}

	private void startTraining(boolean resetDebugOptions) {
		stopTrainingIfActive();
		if (resetDebugOptions) {
			this.debugOptions.resetAll();
		}
		String trainingWorldSeed = createRandomTrainingWorldSeed();
		prepareTrainingSystems(trainingWorldSeed);
		spawnTrainingPlayer();
		resetTrainingTransientState(trainingWorldSeed);
		clearMenuStateForTraining();
		this.trainingActive = true;
		this.state = State.PLAYING;
		this.input.consumeEscapePressed();
		this.input.clearPrimaryAction();
		this.trainingMode.start();
	}

	private String createRandomTrainingWorldSeed() {
		return "training-" + Long.toUnsignedString(new Random(System.nanoTime() ^ System.currentTimeMillis()).nextLong(), 36).toUpperCase();
	}

	private void prepareTrainingSystems(String trainingWorldSeed) {
		int trainingSeedHash = hashString(trainingWorldSeed);
		this.enemySystem.clear();
		this.enemySystem.reset(trainingSeedHash);
		this.enemySystem.setAutoSpawnEnabled(false);
		this.trainingMode = new TrainingMode(this, this.enemySystem, trainingSeedHash);
		this.map = this.trainingMode.buildArena();
	}

	private void spawnTrainingPlayer() {
		int spawnX = this.trainingMode.getPlayerSpawnWorldX();
		int spawnY = this.trainingMode.getPlayerSpawnWorldY();
		this.player = new Player(TRAINING_PLAYER_NAME, spawnX, spawnY, this.assets);
		this.localRuntimeState = new PlayerRuntimeState();
		giveTrainingLoadout(this.player);
	}

	private void resetTrainingTransientState(String trainingWorldSeed) {
		this.projectileSystem.clear();
		resetLocalToolAnimation();
		this.toolTargetObject = null;
	}

	private void clearMenuStateForTraining() {
		this.dayNightCycle.reset();
		this.dayNightCycle.setDayProgress(TRAINING_DAY_PROGRESS);
		resetDeathSpectatorState();
	}

	private void giveTrainingLoadout(Player targetPlayer) {
		if (targetPlayer == null) {
			return;
		}
		resetTrainingWeapon(targetPlayer);
		if (this.debugOptions.shouldUnlockAllWeapons()) {
			unlockAllTrainingWeapons(targetPlayer);
		}
	}

	private void equipTrainingWeapon(ItemDefinition weapon) {
		equipTrainingWeapon(this.player, weapon);
	}

	private void equipTrainingWeapon(Player targetPlayer, ItemDefinition weapon) {
		if (targetPlayer == null || weapon == null || weapon.getWeaponType() != WeaponType.ARMA_DE_FUEGO) {
			return;
		}
		Equipment equipment = targetPlayer.getEquipment();
		equipment.unlockWeapon(weapon);
		equipment.equipWeapon(weapon);
	}

	private void resetTrainingWeapon(Player targetPlayer) {
		if (targetPlayer == null) {
			return;
		}
		targetPlayer.getEquipment().resetToWeapon(ItemDefinition.ALTA_PISTOLA_PRIMERA);
	}

	private void unlockAllTrainingWeapons(Player targetPlayer) {
		if (targetPlayer == null) {
			return;
		}
		targetPlayer.getEquipment().unlockWeapons(ItemDefinition.byWeaponType(WeaponType.ARMA_DE_FUEGO));
	}

	private void stopTrainingIfActive() {
		if (this.trainingMode != null) {
			this.trainingMode.shutdown();
			this.trainingMode = null;
		}
		this.trainingActive = false;
		this.enemySystem.setAutoSpawnEnabled(true);
	}

	public boolean isTrainingActive() {
		return this.trainingActive;
	}

	public TrainingMode getTrainingMode() {
		return this.trainingMode;
	}

	private boolean isTrainingSessionFinished() {
		return this.trainingActive && this.trainingMode != null && this.trainingMode.isSessionFinished();
	}

	private boolean isTrainingWaitingForFirstShot() {
		return this.trainingActive && this.trainingMode != null && this.trainingMode.isWaitingForFirstShot();
	}

	public void restartTraining() {
		startTraining();
	}

	public void exitTrainingToMenu() {
		stopTrainingIfActive();
		this.player = null;
		this.map = null;
		this.projectileSystem.clear();
		this.deathOverlayActive = false;
		this.state = State.MENU;
		this.input.clearMovement();
	}

	private void updatePlaying() {
		if (isTrainingSessionFinished()) {
			clearToolTarget();
			resetLocalToolAnimation();
			return;
		}
		if (this.input.consumeEscapePressed()) {
			beginDisplaySettingsEdit();
			this.state = State.SETTINGS;
			this.settingsMessage = "";
			clearToolTarget();
			return;
		}
		if (this.input.consumeEquipmentToggle()) {
			if (this.player != null) {
				this.player.getEquipment().toggleMenu();
			}
			clearToolTarget();
			resetLocalToolAnimation();
			return;
		}
		this.input.consumeMapToggle();
		updateCameraZoomInput();

		this.primaryGameplayPressedThisFrame = false;
		boolean primaryPressed = consumePrimaryGameplayClick();
		if (primaryPressed && handleEquipmentClick(this.input.getMouseX(), this.input.getMouseY())) {
			primaryPressed = false;
			this.blockPrimaryGameplayUntilRelease = this.input.isPrimaryHeld();
		}
		this.primaryGameplayPressedThisFrame = primaryPressed;
		this.input.consumeSecondaryClick();
		this.input.consumeToolbarSelection();
		this.input.consumeWheelSteps();

		this.input.consumeInteractPressed();

		int moveX = this.input.getMoveX();
		int moveY = this.input.getMoveY();
		if (isTrainingWaitingForFirstShot()) {
			moveX = 0;
			moveY = 0;
		}
		double moveScale = normalizedMoveScale(moveX, moveY);
		this.player.setTakingDamage(false);
		int previousPlayerX = this.player.getX();
		int previousPlayerY = this.player.getY();

		if (moveX != 0) {
			int deltaX = this.player.consumeMoveDeltaX(moveX, moveScale);
			this.player.moveBy(deltaX, 0);
			if (this.player.isHitboxBlocked(this.map)) {
				this.player.moveBy(-deltaX, 0);
			}
		} else {
			this.player.consumeMoveDeltaX(0);
		}
		if (moveY != 0) {
			int deltaY = this.player.consumeMoveDeltaY(moveY, moveScale);
			this.player.moveBy(0, deltaY);
			if (this.player.isHitboxBlocked(this.map)) {
				this.player.moveBy(0, -deltaY);
			}
		} else {
			this.player.consumeMoveDeltaY(0);
		}
		this.player.updateKnockback(this.map);
		this.player.updateLinearVelocityFromPosition(previousPlayerX, previousPlayerY);
		if (this.shotFacingLockTicks <= 0) {
			this.player.updateFacing(moveX, moveY);
		}
		this.enemySystem.update(this.map, this.player, this.dayNightCycle);

		if (this.player.getCurrentHP() <= 0.0) {
			this.player.die();
			this.state = State.DEAD;
			this.audio.stopLoop();
			this.audio.playOnce(DEATH_SOUND, 0f);
			return;
		}
		updateToolUse();
		updateProjectiles();
		this.primaryGameplayPressedThisFrame = false;
	}

	private void updateToolUse() {
		updateProjectileWeaponCooldown(this.localRuntimeState);
		this.usingTool = false;
		ItemDefinition selectedDefinition = this.player.getEquipment().getEquippedWeapon();
		if (this.toolAnimationDefinition != null) {
			if (selectedDefinition == this.toolAnimationDefinition) {
				tickLocalToolAnimation(this.toolAnimationDefinition);
			} else {
				resetLocalToolAnimation();
			}
		}
		boolean primaryActive = isPrimaryGameplayHeld() || this.primaryGameplayPressedThisFrame;
		if (!this.trainingActive) {
			clearToolTarget();
			resetLocalToolAnimation();
			return;
		}
		if (!primaryActive) {
			clearToolTarget();
			return;
		}
		if (selectedDefinition == null || !selectedDefinition.isProjectileWeapon()) {
			clearToolTarget();
			return;
		}
		if (attemptFireProjectileWeapon(this.player, this.localRuntimeState, selectedDefinition, screenToWorldX(this.input.getMouseX()), screenToWorldY(this.input.getMouseY()))) {
			startLocalToolAnimation(selectedDefinition);
		}
		clearToolTarget();
	}

	private boolean handleEquipmentClick(int mouseX, int mouseY) {
		if (this.player == null || !this.trainingActive) {
			return false;
		}
		Equipment equipment = this.player.getEquipment();
		if (!equipment.isMenuOpen()) {
			return false;
		}
		if (isInsideEquipmentSelector(mouseX, mouseY)) {
			equipment.toggleWeaponSelector();
			return true;
		}
		if (!equipment.isWeaponSelectorOpen()) {
			return isInsideEquipmentPanel(mouseX, mouseY);
		}
		int index = equipmentWeaponIndexAt(mouseX, mouseY);
		if (index >= 0) {
			equipment.equipWeapon(equipment.getWeaponSelectionOrder().get(index));
		} else {
			equipment.closeWeaponSelector();
		}
		return true;
	}

	private boolean isInsideEquipmentPanel(int mouseX, int mouseY) {
		return mouseX >= getEquipmentPanelX() && mouseX < getEquipmentPanelX() + EQUIPMENT_PANEL_WIDTH
			&& mouseY >= getEquipmentPanelY() && mouseY < getEquipmentPanelY() + EQUIPMENT_PANEL_HEIGHT;
	}

	private boolean isInsideEquipmentSelector(int mouseX, int mouseY) {
		return mouseX >= getEquipmentSelectorX() && mouseX < getEquipmentSelectorX() + EQUIPMENT_SELECTOR_WIDTH
			&& mouseY >= getEquipmentSelectorY() && mouseY < getEquipmentSelectorY() + EQUIPMENT_SELECTOR_HEIGHT;
	}

	private int equipmentWeaponIndexAt(int mouseX, int mouseY) {
		if (this.player == null) {
			return -1;
		}
		int rowCount = this.player.getEquipment().getWeaponSelectionOrder().size();
		int listX = getEquipmentListX();
		int listY = getEquipmentListY();
		if (mouseX < listX || mouseX >= listX + EQUIPMENT_LIST_WIDTH
			|| mouseY < listY || mouseY >= listY + rowCount * EQUIPMENT_LIST_ROW_HEIGHT) {
			return -1;
		}
		int index = (mouseY - listY) / EQUIPMENT_LIST_ROW_HEIGHT;
		return index >= 0 && index < rowCount ? index : -1;
	}

	private void startLocalToolAnimation(ItemDefinition definition) {
		int durationTicks = Math.max(1, this.player == null ? definition.getUseAnimationTicks() : this.player.applyAttackSpeedToCooldown(definition.getUseAnimationTicks()));
		if (this.toolAnimationTicksRemaining <= 0 || this.toolAnimationDefinition != definition) {
			this.toolUseTicks = 0;
			this.toolAnimationTicksRemaining = durationTicks;
			this.toolAnimationDefinition = definition;
		}
		this.usingTool = true;
		this.toolUseDurationTicks = durationTicks;
	}

	private void tickLocalToolAnimation(ItemDefinition definition) {
		if (this.toolAnimationTicksRemaining <= 0 || this.toolAnimationDefinition != definition) {
			resetLocalToolAnimation();
			return;
		}
		this.usingTool = true;
		this.toolUseDurationTicks = Math.max(1, this.toolUseDurationTicks);
		this.toolUseTicks++;
		this.toolAnimationTicksRemaining--;
		if (this.toolAnimationTicksRemaining <= 0) {
			this.toolAnimationDefinition = null;
		}
	}

	private void resetLocalToolAnimation() {
		this.usingTool = false;
		this.toolUseTicks = 0;
		this.toolUseDurationTicks = 1;
		this.toolAnimationTicksRemaining = 0;
		this.toolAnimationDefinition = null;
	}

	private void updateProjectileWeaponCooldown(PlayerRuntimeState runtime) {
		if (runtime != null && runtime.projectileWeaponCooldownTicks > 0) {
			runtime.projectileWeaponCooldownTicks--;
		}
	}

	private boolean attemptFireProjectileWeapon(Player sourcePlayer, PlayerRuntimeState runtime, ItemDefinition weapon, int targetWorldX, int targetWorldY) {
		if (sourcePlayer == null || runtime == null || weapon == null || !weapon.isProjectileWeapon()
			|| runtime.projectileWeaponCooldownTicks > 0) {
			return false;
		}
		ItemDefinition ammoDefinition = weapon.getProjectileAmmoDefinition();
		if (ammoDefinition == null) {
			return false;
		}
		double initialDeltaX = targetWorldX - sourcePlayer.getX();
		double initialDeltaY = targetWorldY - sourcePlayer.getY();
		double initialLength = Math.sqrt(initialDeltaX * initialDeltaX + initialDeltaY * initialDeltaY);
		Facing shotFacing = initialLength <= 0.001 ? sourcePlayer.getFacing() : facingFromDelta(initialDeltaX, initialDeltaY);
		int originX = heldItemOriginWorldX(sourcePlayer, weapon, shotFacing);
		int originY = heldItemOriginWorldY(sourcePlayer, weapon, shotFacing);
		double deltaX = targetWorldX - originX;
		double deltaY = targetWorldY - originY;
		double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
		if (length <= 0.001) {
			double[] facingVector = facingVector(shotFacing);
			deltaX = facingVector[0];
			deltaY = facingVector[1];
			length = 1.0;
		} else {
			double accuracy = resolveEffectiveAccuracy(sourcePlayer, weapon);
			if (accuracy < 1.0) {
				double halfBasePixels = length * 0.5 * (1.0 - accuracy);
				double baseOffset = (this.weaponRandom.nextDouble() * 2.0 - 1.0) * halfBasePixels;
				double baseUnitX = -deltaY / length;
				double baseUnitY = deltaX / length;
				double deviatedTargetX = targetWorldX + baseUnitX * baseOffset;
				double deviatedTargetY = targetWorldY + baseUnitY * baseOffset;
				deltaX = deviatedTargetX - originX;
				deltaY = deviatedTargetY - originY;
				length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
			}
		}
		sourcePlayer.setFacing(shotFacing);
		if (sourcePlayer == this.player) {
			this.shotFacingLockTicks = SHOT_FACING_LOCK_TICKS;
		}
		double directionX = deltaX / length;
		double directionY = deltaY / length;
		int startWorldX = originX;
		int startWorldY = originY;
		int aimWorldX = startWorldX + (int) Math.round(directionX * 1000.0);
		int aimWorldY = startWorldY + (int) Math.round(directionY * 1000.0);
		ProjectileType projectileType = weapon.getProjectileType();
		boolean spawned = this.projectileSystem.spawn(projectileType, sourcePlayer, weapon, startWorldX, startWorldY, aimWorldX, aimWorldY,
			weapon.getProjectileSpeedPixels(ammoDefinition), weapon.getProjectileDamage(ammoDefinition),
			weapon.getProjectileKnockbackStrengthPixels(ammoDefinition), weapon.getProjectileLifeTicks(ammoDefinition));
		if (!spawned) {
			return false;
		}
		if (this.trainingActive) {
			this.debugOptions.recordBulletTrajectory(startWorldX, startWorldY, aimWorldX, aimWorldY);
		}
		runtime.projectileWeaponCooldownTicks = sourcePlayer.applyAttackSpeedToCooldown(weapon.getProjectileCooldownTicks(ammoDefinition));
		playProjectileWeaponEffects(weapon, projectileType, startWorldX, startWorldY);
		if (this.trainingActive && this.trainingMode != null && sourcePlayer == this.player) {
			this.trainingMode.notifyShotFired();
		}
		return true;
	}

	public int heldItemOriginWorldX(Player sourcePlayer, ItemDefinition definition, Facing facing) {
		if (sourcePlayer == null || definition == null || !definition.isHandEquipable()) {
			return sourcePlayer == null ? 0 : sourcePlayer.getX();
		}
		ItemDefinitionDrawConfig drawConfig = definition.getDrawConfig();
		return (int) Math.round(heldItemBarrelAnchorWorldX(sourcePlayer, definition, drawConfig, facing)
			+ rotatedBarrelAnchorOffsetX(drawConfig, facing));
	}

	public int heldItemOriginWorldY(Player sourcePlayer, ItemDefinition definition, Facing facing) {
		if (sourcePlayer == null || definition == null || !definition.isHandEquipable()) {
			return sourcePlayer == null ? 0 : sourcePlayer.getY();
		}
		ItemDefinitionDrawConfig drawConfig = definition.getDrawConfig();
		return (int) Math.round(heldItemBarrelAnchorWorldY(sourcePlayer, definition, drawConfig, facing)
			+ rotatedBarrelAnchorOffsetY(drawConfig, facing));
	}

	private double rotatedBarrelAnchorOffsetX(ItemDefinitionDrawConfig drawConfig, Facing facing) {
		double offsetX = drawConfig.isMirrored(facing) ? -drawConfig.getBarrelAnchorOffsetX() : drawConfig.getBarrelAnchorOffsetX();
		double offsetY = drawConfig.getBarrelAnchorOffsetY();
		double angle = drawConfig.getBaseAngle(facing);
		return offsetX * Math.cos(angle) - offsetY * Math.sin(angle);
	}

	private double rotatedBarrelAnchorOffsetY(ItemDefinitionDrawConfig drawConfig, Facing facing) {
		double offsetX = drawConfig.isMirrored(facing) ? -drawConfig.getBarrelAnchorOffsetX() : drawConfig.getBarrelAnchorOffsetX();
		double offsetY = drawConfig.getBarrelAnchorOffsetY();
		double angle = drawConfig.getBaseAngle(facing);
		return offsetX * Math.sin(angle) + offsetY * Math.cos(angle);
	}

	private double heldItemBarrelAnchorWorldX(Player sourcePlayer, ItemDefinition definition, ItemDefinitionDrawConfig drawConfig, Facing facing) {
		int drawX = sourcePlayer.getX() + drawConfig.getBaseOffsetX(facing);
		int drawY = sourcePlayer.getY() + drawConfig.getBaseOffsetY(facing);
		int itemWidth = definition.getHeldDrawWidth();
		int itemHeight = definition.getHeldDrawHeight();
		double centerX = drawX + itemWidth / 2.0;
		double centerY = drawY + itemHeight / 2.0;
		double anchorX = drawConfig.isMirrored(facing) ? drawX : drawX + itemWidth;
		double anchorY = drawY;
		return rotateX(anchorX, anchorY, centerX, centerY, drawConfig.getBaseAngle(facing));
	}

	private double heldItemBarrelAnchorWorldY(Player sourcePlayer, ItemDefinition definition, ItemDefinitionDrawConfig drawConfig, Facing facing) {
		int drawX = sourcePlayer.getX() + drawConfig.getBaseOffsetX(facing);
		int drawY = sourcePlayer.getY() + drawConfig.getBaseOffsetY(facing);
		int itemWidth = definition.getHeldDrawWidth();
		int itemHeight = definition.getHeldDrawHeight();
		double centerX = drawX + itemWidth / 2.0;
		double centerY = drawY + itemHeight / 2.0;
		double anchorX = drawConfig.isMirrored(facing) ? drawX : drawX + itemWidth;
		double anchorY = drawY;
		return rotateY(anchorX, anchorY, centerX, centerY, drawConfig.getBaseAngle(facing));
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

	private double resolveEffectiveAccuracy(Player sourcePlayer, ItemDefinition weapon) {
		if (weapon == null) {
			return 1.0;
		}
		double accuracy = weapon.getAccuracy();
		if (sourcePlayer == this.player && isPlayerMovingForAccuracy()) {
			accuracy -= MOVING_ACCURACY_PENALTY;
		}
		return clampAccuracy(accuracy);
	}

	private boolean isPlayerMovingForAccuracy() {
		return this.player != null && this.player.getLastLinearVelocityPixels() > MOVING_ACCURACY_MIN_LINEAR_SPEED;
	}

	private double clampAccuracy(double accuracy) {
		return Math.max(0.0, Math.min(1.0, accuracy));
	}

	private void playProjectileWeaponEffects(ItemDefinition weapon, ProjectileType type, int worldX, int worldY) {
		String fireSoundPath = weapon == null ? "" : weapon.getAttackSoundPath();
		if (fireSoundPath != null && !fireSoundPath.isEmpty()) {
			String fallbackSoundPath = weapon != null && weapon.getWeaponType() == WeaponType.ARMA_DE_FUEGO ? DEFAULT_WEAPON_ATTACK_SOUND : "";
			playPersistentSpatialSfxOrFallback(fireSoundPath, fallbackSoundPath, WEAPON_GAIN_DB, worldX, worldY);
		}
		if (type != null && type.triggersMuzzleFlash()) {
			triggerRevolverFlash(worldX, worldY);
		}
	}

	private void playPersistentSpatialSfxOrFallback(String resourcePath, String fallbackResourcePath, float gainDb, int sourceWorldX, int sourceWorldY) {
		if (resourcePath == null || resourcePath.isEmpty()) {
			return;
		}
		if (this.unavailableSfxPaths.contains(resourcePath)) {
			playFallbackPersistentSpatialSfx(resourcePath, fallbackResourcePath, gainDb, sourceWorldX, sourceWorldY);
			return;
		}
		try {
			playPersistentSpatialSfx(resourcePath, gainDb, sourceWorldX, sourceWorldY);
		} catch (IllegalStateException e) {
			this.unavailableSfxPaths.add(resourcePath);
			System.err.println("Unable to play audio resource " + resourcePath + ": " + e.getMessage());
			playFallbackPersistentSpatialSfx(resourcePath, fallbackResourcePath, gainDb, sourceWorldX, sourceWorldY);
		}
	}

	private void playFallbackPersistentSpatialSfx(String failedResourcePath, String fallbackResourcePath, float gainDb, int sourceWorldX, int sourceWorldY) {
		if (fallbackResourcePath == null || fallbackResourcePath.isEmpty() || fallbackResourcePath.equals(failedResourcePath)) {
			return;
		}
		if (this.unavailableSfxPaths.contains(fallbackResourcePath)) {
			return;
		}
		try {
			playPersistentSpatialSfx(fallbackResourcePath, gainDb, sourceWorldX, sourceWorldY);
		} catch (IllegalStateException e) {
			this.unavailableSfxPaths.add(fallbackResourcePath);
			System.err.println("Unable to play fallback audio resource " + fallbackResourcePath + ": " + e.getMessage());
		}
	}

	private void playPersistentSpatialSfx(String resourcePath, float gainDb, int sourceWorldX, int sourceWorldY) {
		double volumeScale = spatialSfxVolumeScale(sourceWorldX, sourceWorldY);
		if (volumeScale > 0.0) {
			this.audio.playOnceUntilFinished(resourcePath, gainDb, volumeScale);
		}
	}

	private double spatialSfxVolumeScale(int sourceWorldX, int sourceWorldY) {
		if (this.player == null || this.player.isDead()) {
			return 1.0;
		}
		int listenerX = this.player.getX();
		int listenerY = this.player.getY();
		int deltaX = sourceWorldX - listenerX;
		int deltaY = sourceWorldY - listenerY;
		int fullRadius = GameConfig.SPATIAL_SFX_FULL_VOLUME_RADIUS_PIXELS;
		int audibleRadius = GameConfig.SPATIAL_SFX_AUDIBLE_RADIUS_PIXELS;
		int distanceSquared = deltaX * deltaX + deltaY * deltaY;
		if (distanceSquared <= fullRadius * fullRadius) {
			return 1.0;
		}
		if (distanceSquared >= audibleRadius * audibleRadius || audibleRadius <= fullRadius) {
			return 0.0;
		}
		double distance = Math.sqrt(distanceSquared);
		return Math.max(0.0, Math.min(1.0, 1.0 - (distance - fullRadius) / (audibleRadius - fullRadius)));
	}

	private void triggerRevolverFlash(int worldX, int worldY) {
		this.revolverFlashWorldX = worldX;
		this.revolverFlashWorldY = worldY;
		this.revolverFlashTicks = REVOLVER_FLASH_TICKS;
	}

	private void updateRevolverFlash() {
		if (this.revolverFlashTicks > 0) {
			this.revolverFlashTicks--;
		}
	}

	private void updateShotFeedback() {
		if (this.hitMarkerTicks > 0) {
			this.hitMarkerTicks--;
		}
		if (this.shotFacingLockTicks > 0) {
			this.shotFacingLockTicks--;
		}
	}

	private double[] facingVector(Facing facing) {
		if (facing == Facing.LEFT) {
			return new double[] { -1.0, 0.0 };
		}
		if (facing == Facing.RIGHT) {
			return new double[] { 1.0, 0.0 };
		}
		if (facing == Facing.UP) {
			return new double[] { 0.0, -1.0 };
		}
		if (facing == Facing.UP_LEFT) {
			return new double[] { -0.70710678118, -0.70710678118 };
		}
		if (facing == Facing.UP_RIGHT) {
			return new double[] { 0.70710678118, -0.70710678118 };
		}
		if (facing == Facing.DOWN_LEFT) {
			return new double[] { -0.70710678118, 0.70710678118 };
		}
		if (facing == Facing.DOWN_RIGHT) {
			return new double[] { 0.70710678118, 0.70710678118 };
		}
		return new double[] { 0.0, 1.0 };
	}

	private Facing facingFromDelta(double deltaX, double deltaY) {
		double angle = Math.toDegrees(Math.atan2(deltaY, deltaX));
		if (angle >= -22.5 && angle < 22.5) {
			return Facing.RIGHT;
		}
		if (angle >= 22.5 && angle < 67.5) {
			return Facing.DOWN_RIGHT;
		}
		if (angle >= 67.5 && angle < 112.5) {
			return Facing.DOWN;
		}
		if (angle >= 112.5 && angle < 157.5) {
			return Facing.DOWN_LEFT;
		}
		if (angle >= -67.5 && angle < -22.5) {
			return Facing.UP_RIGHT;
		}
		if (angle >= -112.5 && angle < -67.5) {
			return Facing.UP;
		}
		if (angle >= -157.5 && angle < -112.5) {
			return Facing.UP_LEFT;
		}
		return Facing.LEFT;
	}

	private void updateProjectiles() {
		this.projectileSystem.update(this.map, this.enemySystem);
		if (this.projectileSystem.didHitTargetThisUpdate()) {
			this.hitMarkerTicks = HIT_MARKER_TICKS;
		}
		playEnemyHitSounds();
		this.enemySystem.collectDeadEnemies();
	}

	private void playEnemyHitSounds() {
		List<EnemyHitSound> hitSounds = this.enemySystem.getHitSounds();
		for (int i = 0; i < hitSounds.size(); i++) {
			EnemyHitSound hitSound = hitSounds.get(i);
			playPersistentSpatialSfxOrFallback(hitSound.getResourcePath(), "", ENEMY_HIT_GAIN_DB, hitSound.getWorldX(), hitSound.getWorldY());
		}
		this.enemySystem.clearHitSounds();
	}

	private double normalizedMoveScale(int moveX, int moveY) {
		if (moveX == 0 || moveY == 0) {
			return 1.0;
		}
		return 1.0 / Math.sqrt(2.0);
	}


	private void clearToolTarget() {
		if (this.toolTargetObject != null) {
			this.toolTargetObject.resetDurabilityDamage();
			this.toolTargetObject = null;
		}
	}

	private void updateSettings() {
		if (this.input.consumeEscapePressed()) {
			resumeFromSettings(false);
			return;
		}
		boolean primaryPressed = this.input.consumePrimaryClick();
		if (primaryPressed) {
			if (isMusicSliderHovered()) {
				this.activeSettingsSlider = SETTINGS_SLIDER_MUSIC;
			} else if (isSfxSliderHovered()) {
				this.activeSettingsSlider = SETTINGS_SLIDER_SFX;
			} else if (isResolutionSliderHovered()) {
				this.activeSettingsSlider = SETTINGS_SLIDER_RESOLUTION;
			}
		}
		if (!this.input.isPrimaryHeld()) {
			this.activeSettingsSlider = SETTINGS_SLIDER_NONE;
		}
		if (this.activeSettingsSlider != SETTINGS_SLIDER_NONE) {
			if (this.activeSettingsSlider == SETTINGS_SLIDER_MUSIC) {
				setMusicVolume(sliderValueFromMouse());
			} else if (this.activeSettingsSlider == SETTINGS_SLIDER_SFX) {
				setSfxVolume(sliderValueFromMouse());
			} else if (this.activeSettingsSlider == SETTINGS_SLIDER_RESOLUTION) {
				setWindowResolutionFromSlider();
			}
			return;
		}
		if (!primaryPressed) {
			return;
		}
		if (this.trainingActive && isDebugSettingsButtonHovered()) {
			this.debugOptions.toggleMenu();
			return;
		}
		if (isFullscreenButtonHovered()) {
			this.pendingFullscreen = !this.pendingFullscreen;
			return;
		}
		if (isResumeButtonHovered()) {
			resumeFromSettings(true);
			return;
		}
		if (isExitToMenuButtonHovered()) {
			applyPendingDisplaySettings();
			returnToMenu();
			return;
		}
		if (isQuitButtonHovered()) {
			shutdownApplication();
		}
	}

	private void updateMenuSettings() {
		if (this.input.consumeEscapePressed()) {
			closeMenuSettings();
			return;
		}
		boolean primaryPressed = this.input.consumePrimaryClick();
		if (primaryPressed) {
			if (isMusicSliderHovered()) {
				this.activeSettingsSlider = SETTINGS_SLIDER_MUSIC;
			} else if (isSfxSliderHovered()) {
				this.activeSettingsSlider = SETTINGS_SLIDER_SFX;
			} else if (isResolutionSliderHovered()) {
				this.activeSettingsSlider = SETTINGS_SLIDER_RESOLUTION;
			}
		}
		if (!this.input.isPrimaryHeld()) {
			this.activeSettingsSlider = SETTINGS_SLIDER_NONE;
		}
		if (this.activeSettingsSlider != SETTINGS_SLIDER_NONE) {
			if (this.activeSettingsSlider == SETTINGS_SLIDER_MUSIC) {
				setMusicVolume(sliderValueFromMouse());
			} else if (this.activeSettingsSlider == SETTINGS_SLIDER_SFX) {
				setSfxVolume(sliderValueFromMouse());
			} else if (this.activeSettingsSlider == SETTINGS_SLIDER_RESOLUTION) {
				setWindowResolutionFromSlider();
			}
			return;
		}
		if (primaryPressed && this.menuRenderer.isMenuFullscreenButtonHovered(this.input.getMouseX(), this.input.getMouseY())) {
			this.pendingFullscreen = !this.pendingFullscreen;
			return;
		}
		if (primaryPressed && this.menuRenderer.isMenuBackButtonHovered(this.input.getMouseX(), this.input.getMouseY())) {
			closeMenuSettings();
		}
	}

	private boolean isMusicSliderHovered() {
		return isPointInside(settingsSliderInteractionX(), settingsSliderInteractionY(GameConfig.SETTINGS_MUSIC_SLIDER_Y) - 10, GameConfig.SETTINGS_SLIDER_WIDTH, 24);
	}

	private boolean isSfxSliderHovered() {
		return isPointInside(settingsSliderInteractionX(), settingsSliderInteractionY(GameConfig.SETTINGS_SFX_SLIDER_Y) - 10, GameConfig.SETTINGS_SLIDER_WIDTH, 24);
	}

	private boolean isResolutionSliderHovered() {
		return isPointInside(settingsSliderInteractionX(), settingsSliderInteractionY(GameConfig.SETTINGS_RESOLUTION_SLIDER_Y) - 10, GameConfig.SETTINGS_SLIDER_WIDTH, 24);
	}

	private boolean isFullscreenButtonHovered() {
		return isPointInside(GameConfig.SETTINGS_BUTTON_X, settingsOverlayY(GameConfig.SETTINGS_FULLSCREEN_BUTTON_Y), GameConfig.SETTINGS_BUTTON_WIDTH, GameConfig.SETTINGS_BUTTON_HEIGHT);
	}

	private boolean isDebugSettingsButtonHovered() {
		return this.trainingActive
			&& isPointInside(GameConfig.SETTINGS_BUTTON_X, settingsOverlayY(GameConfig.SETTINGS_DEBUG_BUTTON_Y), GameConfig.SETTINGS_BUTTON_WIDTH,
				GameConfig.SETTINGS_BUTTON_HEIGHT);
	}

	private int settingsDebugButtonX() {
		return GameConfig.SETTINGS_BUTTON_X;
	}

	private boolean isResumeButtonHovered() {
		return isPointInside(GameConfig.SETTINGS_BUTTON_X, settingsOverlayY(GameConfig.SETTINGS_RESUME_BUTTON_Y), GameConfig.SETTINGS_BUTTON_WIDTH, GameConfig.SETTINGS_BUTTON_HEIGHT);
	}

	private boolean isExitToMenuButtonHovered() {
		return isPointInside(GameConfig.SETTINGS_BUTTON_X, settingsOverlayY(GameConfig.SETTINGS_MENU_BUTTON_Y), GameConfig.SETTINGS_BUTTON_WIDTH, GameConfig.SETTINGS_BUTTON_HEIGHT);
	}

	private boolean isQuitButtonHovered() {
		return isPointInside(GameConfig.SETTINGS_BUTTON_X, settingsOverlayY(GameConfig.SETTINGS_QUIT_BUTTON_Y), GameConfig.SETTINGS_BUTTON_WIDTH, GameConfig.SETTINGS_BUTTON_HEIGHT);
	}

	private boolean isPointInside(int x, int y, int width, int height) {
		return this.input.getMouseX() >= x && this.input.getMouseX() <= x + width
			&& this.input.getMouseY() >= y && this.input.getMouseY() <= y + height;
	}

	private double sliderValueFromMouse() {
		return Math.max(0.0, Math.min(1.0, (this.input.getMouseX() - settingsSliderInteractionX()) / (double) GameConfig.SETTINGS_SLIDER_WIDTH));
	}

	private void setWindowResolutionFromSlider() {
		int maxIndex = GameConfig.getWindowResolutionCount() - 1;
		int index = (int) Math.round(sliderValueFromMouse() * maxIndex);
		this.pendingWindowResolutionIndex = Math.max(0, Math.min(maxIndex, index));
	}

	private int settingsSliderInteractionX() {
		if (this.state == State.MENU_SETTINGS) {
			return this.menuRenderer.getMenuOffsetX() + GameConfig.BASE_SCREEN_WIDTH / 2 - 90;
		}
		return GameConfig.SETTINGS_SLIDER_X;
	}

	private int settingsSliderInteractionY(int baseY) {
		if (this.state == State.MENU_SETTINGS) {
			return this.menuRenderer.getMenuOffsetY() + baseY;
		}
		return settingsOverlayY(baseY);
	}

	private int settingsOverlayY(int baseY) {
		return baseY + settingsOverlayOffsetY();
	}

	private int settingsOverlayOffsetY() {
		return this.trainingActive && this.state == State.SETTINGS ? GameConfig.TRAINING_SETTINGS_OVERLAY_Y_OFFSET : 0;
	}

	private int screenToWorldX(int screenX) {
		return getCameraCenterWorldX() + (int) Math.round((screenX - GameConfig.SCREEN_CENTER_X) / this.cameraZoom);
	}

	private int screenToWorldY(int screenY) {
		return getCameraCenterWorldY() + (int) Math.round((screenY - GameConfig.SCREEN_CENTER_Y) / this.cameraZoom);
	}

	private void updateCameraZoomInput() {
		int wheelSteps = this.input.consumeZoomWheelSteps();
		int keySteps = this.input.consumeZoomKeySteps();
		int heldDirection = this.input.getZoomKeyDirection();
		if ((wheelSteps == 0 && keySteps == 0 && heldDirection == 0) || this.player == null || this.player.isDead() || this.deathOverlayActive) {
			return;
		}
		setCameraZoom(this.cameraZoom
			+ keySteps * GameConfig.CAMERA_ZOOM_HELD_STEP
			+ heldDirection * GameConfig.CAMERA_ZOOM_HELD_STEP
			- wheelSteps * GameConfig.CAMERA_ZOOM_STEP);
	}

	private void setCameraZoom(double cameraZoom) {
		this.cameraZoom = Math.max(GameConfig.CAMERA_MIN_ZOOM, Math.min(GameConfig.CAMERA_MAX_ZOOM, cameraZoom));
	}

	private void returnToMenu() {
		this.audio.stopLoop();
		this.audio.stopSfxLoop();
		this.activeMusicPath = null;
		this.enemySystem.clear();
		this.projectileSystem.clear();
		this.map = null;
		this.player = null;
		resetLocalToolAnimation();
		this.toolTargetObject = null;
		this.activeSettingsSlider = SETTINGS_SLIDER_NONE;
		this.dayNightCycle.reset();
		resetDeathSpectatorState();
		this.state = State.MENU;
	}

	private void openMenuSettings() {
		this.settingsMessage = "";
		this.activeSettingsSlider = SETTINGS_SLIDER_NONE;
		beginDisplaySettingsEdit();
		this.state = State.MENU_SETTINGS;
	}

	private void closeMenuSettings() {
		applyPendingDisplaySettings();
		this.settingsMessage = "";
		this.activeSettingsSlider = SETTINGS_SLIDER_NONE;
		this.state = State.MENU;
	}

	public synchronized void shutdown() {
		if (this.shuttingDown) {
			return;
		}
		this.shuttingDown = true;
		this.gameThread = null;
		stopTrainingIfActive();
		this.audio.stopSfxLoop();
		this.audio.shutdown();
		this.activeMusicPath = null;
		GameConfig.saveDisplayPreferences();
	}

	private void shutdownApplication() {
		shutdown();
		System.exit(0);
	}

	private void updateMusic() {
		if (this.shuttingDown) {
			return;
		}
		String desiredMusicPath = desiredMusicPath();
		if (desiredMusicPath == null) {
			if (this.activeMusicPath != null) {
				this.audio.stopLoop();
				this.activeMusicPath = null;
			}
			return;
		}
		if (desiredMusicPath.equals(this.activeMusicPath)) {
			return;
		}
		this.audio.playLoop(desiredMusicPath, MUSIC_GAIN_DB);
		this.activeMusicPath = desiredMusicPath;
	}

	private String desiredMusicPath() {
		if (this.deathOverlayActive) {
			return null;
		}
		if (this.state == State.MENU || this.state == State.MENU_SETTINGS) {
			return MENU_MUSIC;
		}
		if ((this.state == State.PLAYING || this.state == State.SETTINGS) && this.player != null) {
			return NIGHT_MUSIC;
		}
		return null;
	}

	private void updateCursor() {
		CursorType cursorType = CursorType.IDLE;
		if (this.hitMarkerTicks > 0 && this.state == State.PLAYING) {
			cursorType = CursorType.HIT_MARKER;
		} else if (this.state == State.MENU && isRootMenuButtonHovered()) {
			cursorType = CursorType.SELECT;
		} else if (this.state == State.MENU_SETTINGS
			&& (this.menuRenderer.isMenuBackButtonHovered(this.input.getMouseX(), this.input.getMouseY())
			|| isMusicSliderHovered()
			|| isSfxSliderHovered()
			|| isResolutionSliderHovered()
			|| this.menuRenderer.isMenuFullscreenButtonHovered(this.input.getMouseX(), this.input.getMouseY()))) {
			cursorType = CursorType.SELECT;
		} else if (this.state == State.SETTINGS
			&& (isMusicSliderHovered()
			|| isSfxSliderHovered()
			|| isResolutionSliderHovered()
			|| (this.trainingActive && isDebugSettingsButtonHovered())
			|| isFullscreenButtonHovered()
			|| isResumeButtonHovered()
			|| isExitToMenuButtonHovered()
			|| isQuitButtonHovered())) {
			cursorType = CursorType.SELECT;
		}
		setCursor(this.assets.getCursor(cursorType));
	}

	public long getFrameCount() {
		return this.frameCount;
	}

	public DayNightCycle getDayNightCycle() {
		return this.dayNightCycle;
	}

	private void updateInfoMessages() {
		for (int i = 0; i < INFO_MESSAGE_SLOTS; i++) {
			if (this.infoMessageTicks[i] > 0) {
				this.infoMessageTicks[i]--;
			}
		}
	}

	private void resetDeathSpectatorState() {
		this.deathOverlayActive = false;
	}

	private int hashString(String value) {
		int hash = 0x811C9DC5;
		for (char character : value.toCharArray()) {
			hash ^= character;
			hash *= 0x01000193;
		}
		return hash;
	}

	private boolean consumePrimaryGameplayClick() {
		boolean clicked = this.input.consumePrimaryClick();
		if (!this.blockPrimaryGameplayUntilRelease) {
			return clicked;
		}
		if (!this.input.isPrimaryHeld()) {
			this.blockPrimaryGameplayUntilRelease = false;
		}
		return false;
	}

	private boolean isPrimaryGameplayHeld() {
		if (!this.blockPrimaryGameplayUntilRelease) {
			return this.input.isPrimaryHeld();
		}
		if (!this.input.isPrimaryHeld()) {
			this.blockPrimaryGameplayUntilRelease = false;
		}
		return false;
	}

	private void resumeFromSettings(boolean blockPrimaryGameplay) {
		applyPendingDisplaySettings();
		this.state = State.PLAYING;
		this.settingsMessage = "";
		this.activeSettingsSlider = SETTINGS_SLIDER_NONE;
		if (blockPrimaryGameplay && this.input.isPrimaryHeld()) {
			this.blockPrimaryGameplayUntilRelease = true;
		}
	}

	private void beginDisplaySettingsEdit() {
		this.pendingFullscreen = this.fullscreen;
		this.pendingWindowResolutionIndex = GameConfig.getWindowResolutionIndex();
	}

	private void applyPendingDisplaySettings() {
		int currentResolutionIndex = GameConfig.getWindowResolutionIndex();
		boolean resolutionChanged = this.pendingWindowResolutionIndex != currentResolutionIndex;
		boolean fullscreenChanged = this.pendingFullscreen != this.fullscreen;
		GameConfig.setWindowResolutionIndex(this.pendingWindowResolutionIndex);
		GameConfig.setFullscreenPreferred(this.pendingFullscreen);
		GameConfig.saveDisplayPreferences();
		this.windowedSize = GameConfig.getWindowResolution();
		this.fullscreen = this.pendingFullscreen;
		if (fullscreenChanged) {
			final boolean targetFullscreen = this.fullscreen;
			SwingUtilities.invokeLater(new Runnable() { @Override public void run() { applyFullscreen(targetFullscreen); } });
		} else if (!this.fullscreen && resolutionChanged) {
			SwingUtilities.invokeLater(new Runnable() { @Override public void run() { applyWindowedResolution(); } });
		}
	}

	private void setMusicVolume(double volume) {
		this.audio.setMusicVolume(volume);
		GameConfig.setMusicVolume(this.audio.getMusicVolume());
	}

	private void setSfxVolume(double volume) {
		this.audio.setSfxVolume(volume);
		GameConfig.setSfxVolume(this.audio.getSfxVolume());
	}

	public AssetManager getAssets() { return this.assets; }
	public AudioManager getAudio() { return this.audio; }
	public GameInput getInput() { return this.input; }
	public DebugOptions getDebugOptions() { return this.debugOptions; }
	public State getState() { return this.state; }
	public GameMap getMap() { return this.map; }
	public Player getPlayer() { return this.player; }
	public int getCameraCenterWorldX() { return this.player == null ? GameConfig.SCREEN_CENTER_X : this.player.getX(); }
	public int getCameraCenterWorldY() { return this.player == null ? GameConfig.SCREEN_CENTER_Y : this.player.getY(); }
	public double getCameraZoom() { return this.cameraZoom; }
	public boolean isSpectating() { return false; }
	public List<Enemy> getEnemies() { return this.enemySystem.getEnemies(); }
	public List<Projectile> getProjectiles() { return this.projectileSystem.getProjectiles(); }
	public List<FlameBurstEffect> getFlameBurstEffects() { return this.enemySystem.getFlameBurstEffects(); }
	public List<CombatFloatingText> getCombatFloatingTexts() { return this.enemySystem.getCombatFloatingTexts(); }
	public boolean isDeathOverlayActive() { return this.deathOverlayActive; }
	public boolean isUsingTool() { return this.usingTool; }
	public int getToolUseTicks() { return this.toolUseTicks; }
	public int getToolUseDurationTicks() { return this.toolUseDurationTicks; }
	public MapObject getToolTargetObject() { return this.toolTargetObject; }
	public int getRevolverFlashTicks() { return this.revolverFlashTicks; }
	public int getRevolverFlashWorldX() { return this.revolverFlashWorldX; }
	public int getRevolverFlashWorldY() { return this.revolverFlashWorldY; }
	public int getRevolverFlashRadiusTiles() { return REVOLVER_FLASH_RADIUS_TILES; }
	public double getRevolverFlashIntensity() { return this.revolverFlashTicks / (double) REVOLVER_FLASH_TICKS; }
	public double getEquippedPlayerAccuracy() {
		if (this.player == null) {
			return 1.0;
		}
		ItemDefinition weapon = this.player.getEquipment().getEquippedWeapon();
		return weapon == null ? 1.0 : resolveEffectiveAccuracy(this.player, weapon);
	}

	public int getEquipmentPanelX() { return (GameConfig.SCREEN_WIDTH - EQUIPMENT_PANEL_WIDTH) / 2; }
	public int getEquipmentPanelY() { return (GameConfig.SCREEN_HEIGHT - EQUIPMENT_PANEL_HEIGHT) / 2; }
	public int getEquipmentPanelWidth() { return EQUIPMENT_PANEL_WIDTH; }
	public int getEquipmentPanelHeight() { return EQUIPMENT_PANEL_HEIGHT; }
	public int getEquipmentSelectorX() { return getEquipmentPanelX() + EQUIPMENT_SELECTOR_X_OFFSET; }
	public int getEquipmentSelectorY() { return getEquipmentPanelY() + EQUIPMENT_SELECTOR_Y_OFFSET; }
	public int getEquipmentSelectorWidth() { return EQUIPMENT_SELECTOR_WIDTH; }
	public int getEquipmentSelectorHeight() { return EQUIPMENT_SELECTOR_HEIGHT; }
	public int getEquipmentListX() { return getEquipmentPanelX() + EQUIPMENT_PANEL_WIDTH + EQUIPMENT_LIST_GAP; }
	public int getEquipmentListWidth() { return EQUIPMENT_LIST_WIDTH; }
	public int getEquipmentListRowHeight() { return EQUIPMENT_LIST_ROW_HEIGHT; }
	public int getEquipmentListY() { return getEquipmentSelectorY(); }
	public int getInfoMessageSlotCount() { return INFO_MESSAGE_SLOTS; }
	public String getInfoMessage(int index) { return this.infoMessages[index]; }
	public int getInfoMessageTicks(int index) { return this.infoMessageTicks[index]; }
	public String getSettingsMessage() { return this.settingsMessage; }
	public boolean isPendingFullscreen() { return this.pendingFullscreen; }
	public boolean isDebugMenuOpen() { return this.debugOptions.isMenuOpen(); }
	public boolean isSettingsDebugButtonHovered() { return isDebugSettingsButtonHovered(); }
	public int getSettingsDebugButtonX() { return settingsDebugButtonX(); }
	public int getSettingsDebugButtonY() { return settingsOverlayY(GameConfig.SETTINGS_DEBUG_BUTTON_Y); }
	public int getSettingsDebugButtonWidth() { return GameConfig.SETTINGS_BUTTON_WIDTH; }
	public int getSettingsDebugButtonHeight() { return GameConfig.SETTINGS_BUTTON_HEIGHT; }
	public int getSettingsOverlayOffsetY() { return settingsOverlayOffsetY(); }
	public double getWindowResolutionSliderValue() { return this.pendingWindowResolutionIndex / (double) Math.max(1, GameConfig.getWindowResolutionCount() - 1); }
	public String getWindowResolutionLabel() { return GameConfig.getWindowResolutionLabel(this.pendingWindowResolutionIndex); }

	private boolean isRootMenuButtonHovered() {
		int mouseX = this.input.getMouseX();
		int mouseY = this.input.getMouseY();
		if (this.menuRenderer.isExitButtonHovered(mouseX, mouseY)) {
			return true;
		}
		return this.menuRenderer.isTrainingButtonHovered(mouseX, mouseY)
			|| this.menuRenderer.isTrainingSettingsButtonHovered(mouseX, mouseY);
	}

}
