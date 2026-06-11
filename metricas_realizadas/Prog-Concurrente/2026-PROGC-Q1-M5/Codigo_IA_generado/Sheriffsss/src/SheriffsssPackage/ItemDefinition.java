package SheriffsssPackage;

public enum ItemDefinition {
	BULLET(0, "Bullet", "sprites/commonBullet.png", 1.0,
		null, ProjectileStatModifiers.NONE, "", WeaponType.NONE, false,
		ItemDefinitionDrawConfig.DEFAULT),
	BRONZE_REVOLVER(1, "Revolver de Bronce", "sprites/RevolverBigga.png", 0.7,
		new ProjectileWeaponStats(ProjectileType.BULLET, 0, 24.0, 14.5, 22.0, 44, 58), ProjectileStatModifiers.NONE, "sounds/Shot.wav", WeaponType.ARMA_DE_FUEGO, true,
		0.10,
		new ItemDefinitionDrawConfig()
		.withHeldDrawSize(20, 12)
		.withBaseOffset(Facing.DOWN_LEFT, -23, 9)
		.withBaseOffset(Facing.LEFT, -28, -1)
		.withBaseOffset(Facing.RIGHT, 10, -1)
		.withBaseOffset(Facing.DOWN_RIGHT, 10, 9)
		.withBaseOffset(Facing.UP_RIGHT, 2, -12)
		.withBaseOffset(Facing.UP, -18, -15)
		.withBaseOffset(Facing.UP_LEFT, -30, -5)
		.withBaseOffset(Facing.DOWN, -1, 9)
		.withRecoil(6.0)
		.drawnBehind(Facing.UP, Facing.UP_RIGHT, Facing.RIGHT, Facing.UP_LEFT, Facing.DOWN_RIGHT)
		.mirrored(Facing.LEFT, Facing.UP_LEFT, Facing.DOWN_LEFT)),
	REINFORCED_REVOLVER(2, "Revolver Reforzado", "sprites/RevolverBiggaSteel.png", 0.8,
		new ProjectileWeaponStats(ProjectileType.BULLET, 0, 36.0, 15.5, 26.0, 34, 58), ProjectileStatModifiers.NONE, "sounds/Shot.wav", WeaponType.ARMA_DE_FUEGO, true,
		0.15,
		new ItemDefinitionDrawConfig()
		.withHeldDrawSize(20, 12)
		.withBaseOffset(Facing.DOWN_LEFT, -23, 9)
		.withBaseOffset(Facing.LEFT, -28, -1)
		.withBaseOffset(Facing.RIGHT, 10, -1)
		.withBaseOffset(Facing.DOWN_RIGHT, 10, 9)
		.withBaseOffset(Facing.UP_RIGHT, 2, -12)
		.withBaseOffset(Facing.UP, -18, -15)
		.withBaseOffset(Facing.UP_LEFT, -30, -5)
		.withBaseOffset(Facing.DOWN, -1, 9)
		.withRecoil(5.0)
		.drawnBehind(Facing.UP, Facing.UP_RIGHT, Facing.RIGHT, Facing.UP_LEFT, Facing.DOWN_RIGHT)
		.mirrored(Facing.LEFT, Facing.UP_LEFT, Facing.DOWN_LEFT)),
	LUGER(3, "Luger", "sprites/Luger.png", 0.85,
		new ProjectileWeaponStats(ProjectileType.BULLET, 0, 21.0, 15.0, 18.0, 30, 58), ProjectileStatModifiers.NONE, "sounds/Shot.wav", WeaponType.ARMA_DE_FUEGO, true,
		0.05,
		new ItemDefinitionDrawConfig()
		.withHeldDrawSize(20, 12)
		.withBaseOffset(Facing.DOWN_LEFT, -23, 9)
		.withBaseOffset(Facing.LEFT, -28, -1)
		.withBaseOffset(Facing.RIGHT, 10, -1)
		.withBaseOffset(Facing.DOWN_RIGHT, 10, 9)
		.withBaseOffset(Facing.UP_RIGHT, 2, -12)
		.withBaseOffset(Facing.UP, -18, -15)
		.withBaseOffset(Facing.UP_LEFT, -30, -5)
		.withBaseOffset(Facing.DOWN, -1, 9)
		.withRecoil(4.0)
		.drawnBehind(Facing.UP, Facing.UP_RIGHT, Facing.RIGHT, Facing.UP_LEFT, Facing.DOWN_RIGHT)
		.mirrored(Facing.LEFT, Facing.UP_LEFT, Facing.DOWN_LEFT)),
	NAIL(4, "Nail", "sprites/Nail.png", 1.0,
		null, ProjectileStatModifiers.NONE, "", WeaponType.NONE, false,
		ItemDefinitionDrawConfig.DEFAULT),
	NAIL_GUN(5, "Nail Gun", "sprites/NailGun-.png", 0.75,
		new ProjectileWeaponStats(ProjectileType.NAIL, 4, 10.0, 13.5, 4.0, 6, 58), ProjectileStatModifiers.NONE, "sounds/Nailgun.wav", WeaponType.ARMA_DE_FUEGO, true,
		0.1,
		new ItemDefinitionDrawConfig()
		.withHeldDrawSize(28, 16)
		.withBaseOffset(Facing.DOWN_LEFT, -24, 7)
		.withBaseOffset(Facing.LEFT, -33, -3)
		.withBaseOffset(Facing.RIGHT, 5, -3)
		.withBaseOffset(Facing.DOWN_RIGHT, 5, 7)
		.withBaseOffset(Facing.UP_RIGHT, 2, -12)
		.withBaseOffset(Facing.UP, -23, -17)
		.withBaseOffset(Facing.UP_LEFT, -35, -10)
		.withBaseOffset(Facing.DOWN, -2, 9)
		.withRecoil(1.0)
		.drawnBehind(Facing.UP, Facing.UP_RIGHT, Facing.RIGHT, Facing.UP_LEFT, Facing.DOWN_RIGHT)
		.mirrored(Facing.LEFT, Facing.UP_LEFT, Facing.DOWN_LEFT)),
	ALTA_PISTOLA_PLATEADA(6, "Alta Pistola Plateada", "sprites/Alta_Pistola_Plateada_Dovah.png", 0.95,
		new ProjectileWeaponStats(ProjectileType.FIRE_BULLET, 0, 60.0, 30.0, 30.0, 18, 300), ProjectileStatModifiers.NONE, "sounds/Alta_Pistola_Shot.wav", WeaponType.ARMA_DE_FUEGO, true,
		0.1,
		new ItemDefinitionDrawConfig()
		.withHeldDrawSize(28, 14)
		.withGripAnchorOffset(6, -11)
		.withBarrelAnchorOffset(-6, 3)
		.withBaseOffset(Facing.DOWN_LEFT, -24, 7)
		.withBaseOffset(Facing.LEFT, -33, -3)
		.withBaseOffset(Facing.RIGHT, 5, -3)
		.withBaseOffset(Facing.DOWN_RIGHT, 5, 7)
		.withBaseOffset(Facing.UP_RIGHT, 2, -12)
		.withBaseOffset(Facing.UP, -23, -17)
		.withBaseOffset(Facing.UP_LEFT, -35, -10)
		.withBaseOffset(Facing.DOWN, -5, 9)
		.withRecoil(3.5)
		.drawnBehind(Facing.UP, Facing.UP_RIGHT, Facing.RIGHT, Facing.UP_LEFT, Facing.DOWN_RIGHT)
		.mirrored(Facing.LEFT, Facing.UP_LEFT, Facing.DOWN_LEFT)),
	ALTA_PISTOLA_PRIMERA(7, "La Primera Alta Pistola", "sprites/Alta_Pistola_Primera.png", 0.99,
		new ProjectileWeaponStats(ProjectileType.FIRE_BULLET, 0, 70.0, 35.0, 40.0, 15, 300), ProjectileStatModifiers.NONE, "sounds/Alta_Pistola_Shot.wav", WeaponType.ARMA_DE_FUEGO, true,
		0.07,
		new ItemDefinitionDrawConfig()
		.withHeldDrawSize(30, 15)
		.withGripAnchorOffset(6, -11)
		.withBarrelAnchorOffset(-6, 3)
		.withBaseOffset(Facing.DOWN_LEFT, -24, 7)
		.withBaseOffset(Facing.LEFT, -33, -3)
		.withBaseOffset(Facing.RIGHT, 5, -3)
		.withBaseOffset(Facing.DOWN_RIGHT, 5, 7)
		.withBaseOffset(Facing.UP_RIGHT, 2, -12)
		.withBaseOffset(Facing.UP, -23, -17)
		.withBaseOffset(Facing.UP_LEFT, -35, -10)
		.withBaseOffset(Facing.DOWN, -5, 9)
		.withRecoil(3.0)
		.drawnBehind(Facing.UP, Facing.UP_RIGHT, Facing.RIGHT, Facing.UP_LEFT, Facing.DOWN_RIGHT)
		.mirrored(Facing.LEFT, Facing.UP_LEFT, Facing.DOWN_LEFT));

	private final int id;
	private final String displayName;
	private final String spritePath;
	private final double accuracy;
	private final ProjectileWeaponStats projectileWeaponStats;
	private final ProjectileStatModifiers projectileStatModifiers;
	private final String attackSoundPath;
	private final WeaponType weaponType;
	private final boolean handEquipable;
	private final double kickback;
	private final ItemDefinitionDrawConfig drawConfig;

	private static final ItemDefinition[] BY_ID = buildByIdIndex();
	private static final ItemDefinition[][] BY_WEAPON_TYPE = buildByWeaponTypeIndex();
	private static final ItemDefinition[] EMPTY_BY_WEAPON_TYPE = new ItemDefinition[0];

	ItemDefinition(int id, String displayName, String spritePath,
		double accuracy, ProjectileWeaponStats projectileWeaponStats, ProjectileStatModifiers projectileStatModifiers, String attackSoundPath, WeaponType weaponType,
		boolean handEquipable, ItemDefinitionDrawConfig drawConfig) {
		this(id, displayName, spritePath, accuracy, projectileWeaponStats, projectileStatModifiers,
			attackSoundPath, weaponType, handEquipable, 0.10, drawConfig);
	}

	ItemDefinition(int id, String displayName, String spritePath,
		double accuracy, ProjectileWeaponStats projectileWeaponStats, ProjectileStatModifiers projectileStatModifiers, String attackSoundPath, WeaponType weaponType,
		boolean handEquipable, double kickback, ItemDefinitionDrawConfig drawConfig) {
		this.id = id;
		this.displayName = displayName;
		this.spritePath = spritePath;
		this.accuracy = Math.max(0.0, Math.min(1.0, accuracy));
		this.projectileWeaponStats = projectileWeaponStats;
		this.projectileStatModifiers = projectileStatModifiers == null ? ProjectileStatModifiers.NONE : projectileStatModifiers;
		this.attackSoundPath = attackSoundPath == null ? "" : attackSoundPath;
		this.weaponType = weaponType == null ? WeaponType.NONE : weaponType;
		this.handEquipable = handEquipable;
		this.kickback = Math.max(0.0, kickback);
		this.drawConfig = drawConfig == null ? new ItemDefinitionDrawConfig() : drawConfig;
		if (this.handEquipable) {
			this.drawConfig.applyDefaultRecoilForAccuracy(this.accuracy);
			this.drawConfig.withRecoilRotation(this.kickback);
		}
	}

	private static ItemDefinition[] buildByIdIndex() {
		int maxId = 0;
		ItemDefinition[] definitions = values();
		for (int i = 0; i < definitions.length; i++) {
			maxId = Math.max(maxId, definitions[i].id);
		}
		ItemDefinition[] byId = new ItemDefinition[maxId + 1];
		for (int i = 0; i < definitions.length; i++) {
			byId[definitions[i].id] = definitions[i];
		}
		return byId;
	}

	private static ItemDefinition[][] buildByWeaponTypeIndex() {
		WeaponType[] weaponTypes = WeaponType.values();
		ItemDefinition[] definitions = values();
		int[] counts = new int[weaponTypes.length];
		for (int i = 0; i < definitions.length; i++) {
			counts[definitions[i].weaponType.ordinal()]++;
		}
		ItemDefinition[][] byWeaponType = new ItemDefinition[weaponTypes.length][];
		for (int i = 0; i < weaponTypes.length; i++) {
			byWeaponType[i] = new ItemDefinition[counts[i]];
		}
		int[] writeIndices = new int[weaponTypes.length];
		for (int i = 0; i < definitions.length; i++) {
			int weaponTypeIndex = definitions[i].weaponType.ordinal();
			byWeaponType[weaponTypeIndex][writeIndices[weaponTypeIndex]] = definitions[i];
			writeIndices[weaponTypeIndex]++;
		}
		return byWeaponType;
	}

	public static ItemDefinition fromId(int id) {
		if (id < 0 || id >= BY_ID.length) {
			return null;
		}
		return BY_ID[id];
	}

	public static ItemDefinition[] byWeaponType(WeaponType weaponType) {
		return weaponType == null ? EMPTY_BY_WEAPON_TYPE : BY_WEAPON_TYPE[weaponType.ordinal()];
	}

	public int getId() { return this.id; }
	public String getDisplayName() { return this.displayName; }
	public String getSpritePath() { return this.spritePath; }
	public int getHeldDrawWidth() { return this.drawConfig.getHeldDrawWidth(); }
	public int getHeldDrawHeight() { return this.drawConfig.getHeldDrawHeight(); }
	public ItemDefinitionDrawConfig getDrawConfig() { return this.drawConfig; }
	public boolean isHandEquipable() { return this.handEquipable; }
	public double getAccuracy() { return this.accuracy; }
	public String getAttackSoundPath() { return this.attackSoundPath; }
	public WeaponType getWeaponType() { return this.weaponType; }
	public boolean canCrit() { return false; }
	public int getBaseCritChancePercent() { return 0; }
	public int getUseAnimationTicks() { return isProjectileWeapon() ? getProjectileCooldownTicks() : 1; }
	public boolean isProjectileWeapon() { return this.projectileWeaponStats != null; }
	public ProjectileType getProjectileType() { return this.projectileWeaponStats == null ? null : this.projectileWeaponStats.getProjectileType(); }
	public ItemDefinition getProjectileAmmoDefinition() { return this.projectileWeaponStats == null ? null : fromId(this.projectileWeaponStats.getAmmoDefinitionId()); }
	public double getProjectileDamage() { return getProjectileDamage(null); }
	public double getProjectileDamage(ItemDefinition ammoDefinition) { return this.projectileWeaponStats == null ? 0.0 : this.projectileWeaponStats.getDamage(projectileModifiers(ammoDefinition)); }
	public double getProjectileKnockbackStrengthPixels() { return getProjectileKnockbackStrengthPixels(null); }
	public double getProjectileKnockbackStrengthPixels(ItemDefinition ammoDefinition) { return this.projectileWeaponStats == null ? 0.0 : this.projectileWeaponStats.getKnockbackStrengthPixels(projectileModifiers(ammoDefinition)); }
	public double getProjectileSpeedPixels() { return getProjectileSpeedPixels(null); }
	public double getProjectileSpeedPixels(ItemDefinition ammoDefinition) { return this.projectileWeaponStats == null ? 0.0 : this.projectileWeaponStats.getSpeedPixels(projectileModifiers(ammoDefinition)); }
	public int getProjectileCooldownTicks() { return getProjectileCooldownTicks(null); }
	public int getProjectileCooldownTicks(ItemDefinition ammoDefinition) { return this.projectileWeaponStats == null ? 1 : this.projectileWeaponStats.getCooldownTicks(projectileModifiers(ammoDefinition)); }
	public int getProjectileLifeTicks() { return getProjectileLifeTicks(null); }
	public int getProjectileLifeTicks(ItemDefinition ammoDefinition) { return this.projectileWeaponStats == null ? 0 : this.projectileWeaponStats.getLifeTicks(projectileModifiers(ammoDefinition)); }
	public int getLightRadiusTiles() { return 0; }
	public double getLightIntensity() { return 0.0; }

	private ProjectileStatModifiers projectileModifiers(ItemDefinition ammoDefinition) {
		return ammoDefinition == null ? ProjectileStatModifiers.NONE : ammoDefinition.projectileStatModifiers;
	}
}
