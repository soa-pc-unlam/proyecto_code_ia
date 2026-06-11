package SheriffsssPackage;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hilo dedicado al tutorial del modo entrenamiento.
 *
 * Por que vive en su propio thread:
 *  - Los pasos esperan tiempos minimos sin bloquear el game loop.
 *    Hacer eso dentro del game loop forzaria a poluir update() con state machines de tiempo.
 *  - Mantiene la simulacion (game loop) libre para correr a 60 FPS sin gastar frames esperando.
 *  - El skip se resuelve atomicamente con interrupt(): no hay polling en el loop.
 *
 * Reglas de threading (ver THREADING.md):
 *  - El game loop posee TODO el estado del mundo. Este thread NO muta nada del mundo.
 *  - Estado del tutorial es propiedad exclusiva de este thread.
 *  - No hay locks compartidos.
 */
public final class TutorialThread extends Thread {
	private static final String THREAD_NAME = "SheriffsssTutorial";
	private static final long MIN_WAIT_AFTER_MIN_DURATION_MS = 1L;

	private final List<TutorialStep> steps;
	private final AtomicBoolean skipRequested = new AtomicBoolean(false);
	private final AtomicBoolean finished = new AtomicBoolean(false);

	public TutorialThread(List<TutorialStep> steps) {
		super(THREAD_NAME);
		setDaemon(true);
		this.steps = steps;
	}

	@Override
	public void run() {
		try {
			runAllSteps();
		} catch (InterruptedException ignored) {
			// Skip via interrupt: simplemente terminamos.
		} finally {
			this.finished.set(true);
		}
	}

	private void runAllSteps() throws InterruptedException {
		int total = this.steps.size();
		for (int i = 0; i < total; i++) {
			if (this.skipRequested.get()) {
				return;
			}
			runStep(this.steps.get(i), i + 1, total);
		}
	}

	private void runStep(TutorialStep step, int stepNumber, int totalSteps) throws InterruptedException {
		if (step.getMinDurationMs() > 0) {
			Thread.sleep(step.getMinDurationMs());
		}
		if (this.skipRequested.get()) {
			return;
		}
		TutorialEventType expected = step.getTriggerEvent();
		if (expected == null) {
			return;
		}
		Thread.sleep(remainingWaitMs(step));
	}

	private static long remainingWaitMs(TutorialStep step) {
		return Math.max(MIN_WAIT_AFTER_MIN_DURATION_MS, step.getMaxWaitMs() - step.getMinDurationMs());
	}

	/**
	 * Llamado desde el game loop o desde shutdown. Idempotente.
	 */
	public void skip() {
		if (this.skipRequested.compareAndSet(false, true)) {
			interrupt();
		}
	}
}
