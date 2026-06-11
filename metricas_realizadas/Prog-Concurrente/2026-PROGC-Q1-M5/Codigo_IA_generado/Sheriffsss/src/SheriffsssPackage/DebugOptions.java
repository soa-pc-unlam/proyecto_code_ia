package SheriffsssPackage;

import java.util.ArrayList;
import java.util.List;

public class DebugOptions {
	public static final int MAX_BULLET_TRAJECTORIES = 20;
	public static final int PANEL_X = 18;
	public static final int PANEL_Y = 84;
	public static final int PANEL_WIDTH = 390;
	public static final int PANEL_PADDING = 14;
	public static final int ROW_HEIGHT = 34;
	public static final int UNLOCK_ALL_WEAPONS_ROW = 12;
	public static final int SWITCH_SIZE = 18;
	public static final int SWITCH_X = PANEL_X + PANEL_PADDING;
	public static final int TEXT_X = SWITCH_X + SWITCH_SIZE + 12;
	public static final int FIRST_ROW_Y = PANEL_Y + 48;
	public static final int TITLE_BASELINE_Y = PANEL_Y + 28;
	public static final int TRAJECTORY_SLIDER_Y = FIRST_ROW_Y + ROW_HEIGHT * (UNLOCK_ALL_WEAPONS_ROW + 1) + 14;
	public static final int TRAJECTORY_SLIDER_X = TEXT_X;
	public static final int TRAJECTORY_SLIDER_WIDTH = 180;
	public static final int TRAJECTORY_SLIDER_HEIGHT = 8;
	public static final int PANEL_HEIGHT = TRAJECTORY_SLIDER_Y - PANEL_Y + 42;

	private boolean menuOpen;
	private boolean drawHitboxes;
	private boolean drawSpritePerimeters;
	private boolean drawPlayerMouseLine;
	private boolean drawPlayerOrigin;
	private boolean drawWeaponOrigin;
	private boolean drawWeaponGripAnchor;
	private boolean drawWeaponBarrelAnchor;
	private boolean drawFullAccuracyCone;
	private boolean drawWeaponAccuracyCone;
	private boolean drawBulletTrajectories;
	private boolean forceTrainingPerfectAccuracy;
	private boolean drawTrainingFailurePerimeter;
	private boolean unlockAllWeaponsRequested;
	private boolean allWeaponsUnlocked;
	private int bulletTrajectoryLimit = 5;
	private final ArrayList<DebugBulletTrajectory> bulletTrajectories = new ArrayList<DebugBulletTrajectory>(MAX_BULLET_TRAJECTORIES);

	public void toggleMenu() {
		this.menuOpen = !this.menuOpen;
	}

	public boolean handleClick(int mouseX, int mouseY) {
		if (!this.menuOpen || mouseX < PANEL_X || mouseX > PANEL_X + PANEL_WIDTH
			|| mouseY < PANEL_Y || mouseY > PANEL_Y + PANEL_HEIGHT) {
			return false;
		}
		if (isTrajectorySliderHovered(mouseX, mouseY)) {
			setBulletTrajectoryLimitFromMouse(mouseX);
			return true;
		}
		int row = (mouseY - FIRST_ROW_Y) / ROW_HEIGHT;
		if (row < 0 || row > UNLOCK_ALL_WEAPONS_ROW) {
			return true;
		}
		int rowTop = FIRST_ROW_Y + row * ROW_HEIGHT;
		if (mouseY < rowTop || mouseY > rowTop + ROW_HEIGHT) {
			return true;
		}
		if (row == UNLOCK_ALL_WEAPONS_ROW) {
			this.unlockAllWeaponsRequested = true;
			this.allWeaponsUnlocked = !this.allWeaponsUnlocked;
		} else {
			toggleRow(row);
		}
		return true;
	}

	public void resetAll() {
		this.menuOpen = false;
		this.drawHitboxes = false;
		this.drawSpritePerimeters = false;
		this.drawPlayerMouseLine = false;
		this.drawPlayerOrigin = false;
		this.drawWeaponOrigin = false;
		this.drawWeaponGripAnchor = false;
		this.drawWeaponBarrelAnchor = false;
		this.drawFullAccuracyCone = false;
		this.drawWeaponAccuracyCone = false;
		this.drawBulletTrajectories = false;
		this.forceTrainingPerfectAccuracy = false;
		this.drawTrainingFailurePerimeter = false;
		this.unlockAllWeaponsRequested = false;
		this.allWeaponsUnlocked = false;
		this.bulletTrajectoryLimit = 5;
		this.bulletTrajectories.clear();
	}

	private void toggleRow(int row) {
		if (row == 0) {
			this.drawHitboxes = !this.drawHitboxes;
		} else if (row == 1) {
			this.drawSpritePerimeters = !this.drawSpritePerimeters;
		} else if (row == 2) {
			this.drawPlayerMouseLine = !this.drawPlayerMouseLine;
		} else if (row == 3) {
			this.drawPlayerOrigin = !this.drawPlayerOrigin;
		} else if (row == 4) {
			this.drawWeaponOrigin = !this.drawWeaponOrigin;
		} else if (row == 5) {
			this.drawWeaponGripAnchor = !this.drawWeaponGripAnchor;
		} else if (row == 6) {
			this.drawWeaponBarrelAnchor = !this.drawWeaponBarrelAnchor;
		} else if (row == 7) {
			this.drawFullAccuracyCone = !this.drawFullAccuracyCone;
		} else if (row == 8) {
			this.drawWeaponAccuracyCone = !this.drawWeaponAccuracyCone;
		} else if (row == 9) {
			this.drawBulletTrajectories = !this.drawBulletTrajectories;
		} else if (row == 10) {
			this.forceTrainingPerfectAccuracy = !this.forceTrainingPerfectAccuracy;
		} else if (row == 11) {
			this.drawTrainingFailurePerimeter = !this.drawTrainingFailurePerimeter;
		}
	}

	public boolean isTrajectorySliderHovered(int mouseX, int mouseY) {
		return this.menuOpen
			&& mouseX >= TRAJECTORY_SLIDER_X
			&& mouseX <= TRAJECTORY_SLIDER_X + TRAJECTORY_SLIDER_WIDTH
			&& mouseY >= TRAJECTORY_SLIDER_Y + 10
			&& mouseY <= TRAJECTORY_SLIDER_Y + 40;
	}

	public void setBulletTrajectoryLimitFromMouse(int mouseX) {
		double value = (mouseX - TRAJECTORY_SLIDER_X) / (double) TRAJECTORY_SLIDER_WIDTH;
		setBulletTrajectoryLimit((int) Math.round(Math.max(0.0, Math.min(1.0, value)) * MAX_BULLET_TRAJECTORIES));
	}

	public void setBulletTrajectoryLimit(int limit) {
		this.bulletTrajectoryLimit = Math.max(0, Math.min(MAX_BULLET_TRAJECTORIES, limit));
		trimBulletTrajectories();
	}

	public void recordBulletTrajectory(int startWorldX, int startWorldY, int endWorldX, int endWorldY) {
		if (this.bulletTrajectoryLimit <= 0) {
			this.bulletTrajectories.clear();
			return;
		}
		this.bulletTrajectories.add(new DebugBulletTrajectory(startWorldX, startWorldY, endWorldX, endWorldY));
		trimBulletTrajectories();
	}

	private void trimBulletTrajectories() {
		while (this.bulletTrajectories.size() > this.bulletTrajectoryLimit) {
			this.bulletTrajectories.remove(0);
		}
	}

	public boolean isMenuOpen() {
		return this.menuOpen;
	}

	public void setMenuOpen(boolean menuOpen) {
		this.menuOpen = menuOpen;
	}

	public boolean shouldDrawHitboxes() {
		return this.drawHitboxes;
	}

	public boolean shouldDrawSpritePerimeters() {
		return this.drawSpritePerimeters;
	}

	public boolean shouldDrawPlayerMouseLine() {
		return this.drawPlayerMouseLine;
	}

	public boolean shouldDrawPlayerOrigin() {
		return this.drawPlayerOrigin;
	}

	public boolean shouldDrawWeaponOrigin() {
		return this.drawWeaponOrigin;
	}

	public boolean shouldDrawWeaponGripAnchor() {
		return this.drawWeaponGripAnchor;
	}

	public boolean shouldDrawWeaponBarrelAnchor() {
		return this.drawWeaponBarrelAnchor;
	}

	public boolean shouldDrawFullAccuracyCone() {
		return this.drawFullAccuracyCone;
	}

	public boolean shouldDrawWeaponAccuracyCone() {
		return this.drawWeaponAccuracyCone;
	}

	public boolean shouldDrawBulletTrajectories() {
		return this.drawBulletTrajectories;
	}

	public boolean shouldForceTrainingPerfectAccuracy() {
		return this.forceTrainingPerfectAccuracy;
	}

	public boolean shouldDrawTrainingFailurePerimeter() {
		return this.drawTrainingFailurePerimeter;
	}

	public boolean shouldUnlockAllWeapons() {
		return this.allWeaponsUnlocked;
	}

	public boolean hasAnyModeEnabled() {
		return this.drawHitboxes
			|| this.drawSpritePerimeters
			|| this.drawPlayerMouseLine
			|| this.drawPlayerOrigin
			|| this.drawWeaponOrigin
			|| this.drawWeaponGripAnchor
			|| this.drawWeaponBarrelAnchor
			|| this.drawFullAccuracyCone
			|| this.drawWeaponAccuracyCone
			|| this.drawBulletTrajectories
			|| this.forceTrainingPerfectAccuracy
			|| this.drawTrainingFailurePerimeter
			|| this.allWeaponsUnlocked;
	}

	public int getBulletTrajectoryLimit() {
		return this.bulletTrajectoryLimit;
	}

	public double getBulletTrajectorySliderValue() {
		return this.bulletTrajectoryLimit / (double) MAX_BULLET_TRAJECTORIES;
	}

	public List<DebugBulletTrajectory> getBulletTrajectories() {
		return this.bulletTrajectories;
	}

	public boolean consumeUnlockAllWeaponsRequest() {
		boolean requested = this.unlockAllWeaponsRequested;
		this.unlockAllWeaponsRequested = false;
		return requested;
	}

	public boolean isRowEnabled(int row) {
		if (row == 0) {
			return this.drawHitboxes;
		}
		if (row == 1) {
			return this.drawSpritePerimeters;
		}
		if (row == 2) {
			return this.drawPlayerMouseLine;
		}
		if (row == 3) {
			return this.drawPlayerOrigin;
		}
		if (row == 4) {
			return this.drawWeaponOrigin;
		}
		if (row == 5) {
			return this.drawWeaponGripAnchor;
		}
		if (row == 6) {
			return this.drawWeaponBarrelAnchor;
		}
		if (row == 7) {
			return this.drawFullAccuracyCone;
		}
		if (row == 8) {
			return this.drawWeaponAccuracyCone;
		}
		if (row == 9) {
			return this.drawBulletTrajectories;
		}
		if (row == 10) {
			return this.forceTrainingPerfectAccuracy;
		}
		if (row == 11) {
			return this.drawTrainingFailurePerimeter;
		}
		if (row == UNLOCK_ALL_WEAPONS_ROW) {
			return this.allWeaponsUnlocked;
		}
		return false;
	}
}
