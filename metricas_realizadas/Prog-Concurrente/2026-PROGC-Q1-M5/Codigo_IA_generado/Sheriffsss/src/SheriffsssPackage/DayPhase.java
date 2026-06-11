package SheriffsssPackage;

public enum DayPhase {
	MORNING("MANANA"),
	AFTERNOON("TARDE"),
	SUNSET("ATARDECER"),
	NIGHT("NOCHE");

	private final String displayName;

	DayPhase(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return this.displayName;
	}
}
