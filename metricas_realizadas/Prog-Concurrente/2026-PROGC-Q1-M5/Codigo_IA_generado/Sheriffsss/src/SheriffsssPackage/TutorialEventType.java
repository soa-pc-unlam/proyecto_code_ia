package SheriffsssPackage;

/**
 * Eventos que el game loop publica al TutorialThread para hacerlo avanzar.
 * Comunicacion unidireccional: game loop -> tutorial thread.
 */
public enum TutorialEventType {
	FIRST_MOVEMENT,
	FIRST_SHOT,
	FIRST_KILL;
}
