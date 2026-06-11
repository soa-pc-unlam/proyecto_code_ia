package SheriffsssPackage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Equipment {
	private final ArrayList<ItemDefinition> unlockedWeapons = new ArrayList<ItemDefinition>();
	private ItemDefinition equippedWeapon;
	private boolean menuOpen;
	private boolean weaponSelectorOpen;

	public void clear() {
		this.unlockedWeapons.clear();
		this.equippedWeapon = null;
		this.menuOpen = false;
		this.weaponSelectorOpen = false;
	}

	public void resetToWeapon(ItemDefinition weapon) {
		clear();
		unlockWeapon(weapon);
		equipWeapon(weapon);
	}

	public boolean unlockWeapon(ItemDefinition weapon) {
		if (!isFirearm(weapon) || this.unlockedWeapons.contains(weapon)) {
			return false;
		}
		this.unlockedWeapons.add(weapon);
		if (this.equippedWeapon == null) {
			this.equippedWeapon = weapon;
		}
		return true;
	}

	public int unlockWeapons(ItemDefinition[] weapons) {
		if (weapons == null) {
			return 0;
		}
		int added = 0;
		for (int i = 0; i < weapons.length; i++) {
			if (unlockWeapon(weapons[i])) {
				added++;
			}
		}
		return added;
	}

	public boolean equipWeapon(ItemDefinition weapon) {
		if (!this.unlockedWeapons.contains(weapon)) {
			return false;
		}
		this.equippedWeapon = weapon;
		this.weaponSelectorOpen = false;
		return true;
	}

	public ItemDefinition getEquippedWeapon() {
		return this.equippedWeapon;
	}

	public List<ItemDefinition> getUnlockedWeapons() {
		return Collections.unmodifiableList(this.unlockedWeapons);
	}

	public List<ItemDefinition> getWeaponSelectionOrder() {
		ArrayList<ItemDefinition> orderedWeapons = new ArrayList<ItemDefinition>(this.unlockedWeapons);
		Collections.sort(orderedWeapons, new Comparator<ItemDefinition>() {
			@Override
			public int compare(ItemDefinition first, ItemDefinition second) {
				if (first == equippedWeapon) {
					return -1;
				}
				if (second == equippedWeapon) {
					return 1;
				}
				return Integer.compare(first.getId(), second.getId());
			}
		});
		return orderedWeapons;
	}

	public boolean hasUnlockedWeapons() {
		return !this.unlockedWeapons.isEmpty();
	}

	public boolean isWeaponSelectorOpen() {
		return this.menuOpen && this.weaponSelectorOpen;
	}

	public boolean isMenuOpen() {
		return this.menuOpen;
	}

	public void toggleMenu() {
		this.menuOpen = !this.menuOpen;
		if (!this.menuOpen) {
			this.weaponSelectorOpen = false;
		}
	}

	public void openMenu() {
		this.menuOpen = true;
	}

	public void toggleWeaponSelector() {
		this.menuOpen = true;
		this.weaponSelectorOpen = !this.weaponSelectorOpen && hasUnlockedWeapons();
	}

	public void closeWeaponSelector() {
		this.weaponSelectorOpen = false;
	}

	private boolean isFirearm(ItemDefinition weapon) {
		return weapon != null && weapon.getWeaponType() == WeaponType.ARMA_DE_FUEGO;
	}
}
