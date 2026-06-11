package SheriffsssPackage;

public class DebugBulletTrajectory {
	private final int startWorldX;
	private final int startWorldY;
	private final int endWorldX;
	private final int endWorldY;

	public DebugBulletTrajectory(int startWorldX, int startWorldY, int endWorldX, int endWorldY) {
		this.startWorldX = startWorldX;
		this.startWorldY = startWorldY;
		this.endWorldX = endWorldX;
		this.endWorldY = endWorldY;
	}

	public int getStartWorldX() {
		return this.startWorldX;
	}

	public int getStartWorldY() {
		return this.startWorldY;
	}

	public int getEndWorldX() {
		return this.endWorldX;
	}

	public int getEndWorldY() {
		return this.endWorldY;
	}
}
