package SheriffsssPackage;

public class ItemDefinitionDrawConfig {
	public static final ItemDefinitionDrawConfig DEFAULT = new ItemDefinitionDrawConfig();

	private int heldDrawWidth;
	private int heldDrawHeight;
	private int gripAnchorOffsetX;
	private int gripAnchorOffsetY;
	private int barrelAnchorOffsetX;
	private int barrelAnchorOffsetY;

	private final int[] baseOffsetX = new int[Facing.values().length];
	private final int[] baseOffsetY = new int[Facing.values().length];
	private final double[] baseAngle = new double[Facing.values().length];
	private double recoil;
	private boolean recoilConfigured;
	private double recoilRotationRadians;
	private final boolean[] drawnBehind = new boolean[Facing.values().length];
	private final boolean[] mirrored = new boolean[Facing.values().length];

	public ItemDefinitionDrawConfig() {
		this.baseAngle[index(Facing.LEFT)] = 0.0;
		this.baseAngle[index(Facing.UP_LEFT)] = 0.75;
		this.baseAngle[index(Facing.RIGHT)] = 0.0;
		this.baseAngle[index(Facing.UP_RIGHT)] = -0.75;
		this.baseAngle[index(Facing.DOWN_LEFT)] = -0.75;
		this.baseAngle[index(Facing.DOWN_RIGHT)] = 0.75;
		this.baseAngle[index(Facing.DOWN)] = 1.5;
		this.baseAngle[index(Facing.UP)] = -1.5;
	}

	public ItemDefinitionDrawConfig withHeldDrawSize(int width, int height) {
		this.heldDrawWidth = Math.max(0, width);
		this.heldDrawHeight = Math.max(0, height);
		return this;
	}

	public ItemDefinitionDrawConfig withGripAnchorOffset(int x, int y) {
		this.gripAnchorOffsetX = x;
		this.gripAnchorOffsetY = y;
		return this;
	}

	public ItemDefinitionDrawConfig withBarrelAnchorOffset(int x, int y) {
		this.barrelAnchorOffsetX = x;
		this.barrelAnchorOffsetY = y;
		return this;
	}

	public ItemDefinitionDrawConfig withBaseOffset(Facing facing, int x, int y) {
		this.baseOffsetX[index(facing)] = x;
		this.baseOffsetY[index(facing)] = y;
		return this;
	}

	public ItemDefinitionDrawConfig withRecoil(double recoil) {
		this.recoil = Math.max(0.0, recoil);
		this.recoilConfigured = true;
		return this;
	}

	public ItemDefinitionDrawConfig applyDefaultRecoilForAccuracy(double accuracy) {
		if (!this.recoilConfigured) {
			double clampedAccuracy = Math.max(0.0, Math.min(1.0, accuracy));
			this.recoil = (1.0 - clampedAccuracy) * GameConfig.DEFAULT_RECOIL_ACCURACY_SCALE;
		}
		return this;
	}

	public ItemDefinitionDrawConfig withRecoilRotation(double radians) {
		this.recoilRotationRadians = Math.max(0.0, radians);
		return this;
	}

	public ItemDefinitionDrawConfig drawnBehind(Facing... facings) {
		for (int i = 0; i < facings.length; i++) {
			this.drawnBehind[index(facings[i])] = true;
		}
		return this;
	}

	public ItemDefinitionDrawConfig mirrored(Facing... facings) {
		for (int i = 0; i < facings.length; i++) {
			this.mirrored[index(facings[i])] = true;
		}
		return this;
	}

	public int getBaseOffsetX(Facing facing) {
		return this.baseOffsetX[index(facing)];
	}

	public int getHeldDrawWidth() {
		return this.heldDrawWidth;
	}

	public int getHeldDrawHeight() {
		return this.heldDrawHeight;
	}

	public int getGripAnchorOffsetX() {
		return this.gripAnchorOffsetX;
	}

	public int getGripAnchorOffsetY() {
		return this.gripAnchorOffsetY;
	}

	public int getBarrelAnchorOffsetX() {
		return this.barrelAnchorOffsetX;
	}

	public int getBarrelAnchorOffsetY() {
		return this.barrelAnchorOffsetY;
	}

	public int getBaseOffsetY(Facing facing) {
		return this.baseOffsetY[index(facing)];
	}

	public double getBaseAngle(Facing facing) {
		return this.baseAngle[index(facing)];
	}

	public int getSwingOffsetX(Facing facing, double swing) {
		return (int) (swing * this.recoil * recoilXDirection(facing));
	}

	public int getSwingOffsetY(Facing facing, double swing) {
		return (int) (Math.abs(swing) * this.recoil * recoilYDirection(facing));
	}

	public double getSwingAngle(Facing facing, double swing) {
		return swing * this.recoilRotationRadians * recoilRotationDirection(facing);
	}

	public boolean isDrawnBehind(Facing facing) {
		return this.drawnBehind[index(facing)];
	}

	public boolean isMirrored(Facing facing) {
		return this.mirrored[index(facing)];
	}

	private int index(Facing facing) {
		return facing == null ? 0 : facing.ordinal();
	}

	private int recoilXDirection(Facing facing) {
		if (facing == Facing.RIGHT || facing == Facing.UP_RIGHT || facing == Facing.DOWN_RIGHT || facing == Facing.UP) {
			return -1;
		}
		return 1;
	}

	private int recoilYDirection(Facing facing) {
		if (facing == Facing.UP || facing == Facing.UP_LEFT || facing == Facing.UP_RIGHT) {
			return 1;
		}
		return -1;
	}

	private int recoilRotationDirection(Facing facing) {
		return isMirrored(facing) ? 1 : -1;
	}
}
