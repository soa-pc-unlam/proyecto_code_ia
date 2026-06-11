package SheriffsssPackage;

public class DayNightCycle {
	private static final int TICKS_PER_DAY = GameConfig.TARGET_FPS * 300;
	private static final double MORNING_END = 0.20;
	private static final double AFTERNOON_END = 0.52;
	private static final double SUNSET_END = 0.68;
	private static final double LATE_NIGHT_START = 0.88;

	private int dayCount = 1;
	private int tickOfDay;

	public void reset() {
		this.dayCount = 1;
		this.tickOfDay = 0;
	}

	public void setDayProgress(double progress) {
		double clampedProgress = Math.max(0.0, Math.min(0.999, progress));
		this.tickOfDay = Math.max(0, Math.min(TICKS_PER_DAY - 1, (int) Math.round(TICKS_PER_DAY * clampedProgress)));
	}

	public int getDayCount() {
		return this.dayCount;
	}

	public DayPhase getPhase() {
		double progress = getDayProgress();
		if (progress < MORNING_END) {
			return DayPhase.MORNING;
		}
		if (progress < AFTERNOON_END) {
			return DayPhase.AFTERNOON;
		}
		if (progress < SUNSET_END) {
			return DayPhase.SUNSET;
		}
		return DayPhase.NIGHT;
	}

	public double getAmbientLight() {
		double progress = getDayProgress();
		if (progress < MORNING_END) {
			return lerp(0.82, 1.0, progress / MORNING_END);
		}
		if (progress < AFTERNOON_END) {
			return 1.0;
		}
		if (progress < SUNSET_END) {
			return lerp(1.0, 0.38, (progress - AFTERNOON_END) / (SUNSET_END - AFTERNOON_END));
		}
		if (progress < LATE_NIGHT_START) {
			return lerp(0.38, 0.12, (progress - SUNSET_END) / (LATE_NIGHT_START - SUNSET_END));
		}
		return lerp(0.12, 0.82, (progress - LATE_NIGHT_START) / (1.0 - LATE_NIGHT_START));
	}

	public int getSunsetTintAlpha() {
		double progress = getDayProgress();
		if (progress < AFTERNOON_END || progress >= SUNSET_END) {
			return 0;
		}
		double sunsetProgress = (progress - AFTERNOON_END) / (SUNSET_END - AFTERNOON_END);
		double peak = 1.0 - Math.abs(sunsetProgress * 2.0 - 1.0);
		return (int) (68 * peak);
	}

	public boolean isNaturallyBright() {
		return getAmbientLight() >= 0.995;
	}

	private double getDayProgress() {
		return this.tickOfDay / (double) TICKS_PER_DAY;
	}

	private double lerp(double start, double end, double amount) {
		return start + (end - start) * amount;
	}
}
