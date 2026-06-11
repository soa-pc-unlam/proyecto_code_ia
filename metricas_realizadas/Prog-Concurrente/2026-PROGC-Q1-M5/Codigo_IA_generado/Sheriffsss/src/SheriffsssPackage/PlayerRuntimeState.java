package SheriffsssPackage;

public class PlayerRuntimeState {
	public boolean usingTool;
	public MapObject toolTargetObject;
	public int toolUseTicks;
	public int toolUseDurationTicks = 1;
	public ItemDefinition toolAnimationDefinition;
	public int toolAnimationTicksRemaining;
	public int lastInputFrame = -1;
	public int projectileWeaponCooldownTicks;
	public int respawnDeathCount;
	public int respawnTicksRemaining;
}
