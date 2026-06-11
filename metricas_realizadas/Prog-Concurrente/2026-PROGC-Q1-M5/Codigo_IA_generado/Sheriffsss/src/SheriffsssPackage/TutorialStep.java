package SheriffsssPackage;

/**
 * Definicion estatica de un paso del tutorial. Inmutable.
 */
public final class TutorialStep {
	private final TutorialEventType triggerEvent;
	private final long minDurationMs;
	private final long maxWaitMs;

	public TutorialStep(TutorialEventType triggerEvent, long minDurationMs, long maxWaitMs) {
		this.triggerEvent = triggerEvent;
		this.minDurationMs = Math.max(0L, minDurationMs);
		this.maxWaitMs = Math.max(this.minDurationMs, maxWaitMs);
	}

	public TutorialEventType getTriggerEvent() {
		return this.triggerEvent;
	}

	public long getMinDurationMs() {
		return this.minDurationMs;
	}

	public long getMaxWaitMs() {
		return this.maxWaitMs;
	}
}
