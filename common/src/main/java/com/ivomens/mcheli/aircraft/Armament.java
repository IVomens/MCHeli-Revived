package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.aircraft.VehicleParts.Chaff;
import com.ivomens.mcheli.aircraft.VehicleParts.Flare;
import com.ivomens.mcheli.aircraft.VehicleParts.Weapon;
import com.ivomens.mcheli.aircraft.VehicleParts.WeaponSet;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A vehicle's weapons and countermeasures.
 *
 * @param weaponSets mounts of one weapon type that the crew selects together, in file order
 * @param flare      where flares leave the vehicle and the flare patterns
 * @param chaff      the chaff launcher, or null without one
 */
public record Armament(List<WeaponSet> weaponSets, Flare flare, @Nullable Chaff chaff) {

    public Armament {
        weaponSets = List.copyOf(weaponSets);
    }

    public int getWeaponCount() {
        return this.weaponSets.size();
    }

    @Nullable
    public WeaponSet getWeaponSetById(int id) {
        return id >= 0 && id < this.weaponSets.size() ? this.weaponSets.get(id) : null;
    }

    /** The first mount of the weapon set, or null for an id that names none. */
    @Nullable
    public Weapon getWeaponById(int id) {
        WeaponSet set = getWeaponSetById(id);
        return set != null ? set.weapons.get(0) : null;
    }

    /** The id of the first weapon set of this type, ignoring case, or -1. */
    public int getWeaponIdByName(String type) {
        for (int i = 0; i < this.weaponSets.size(); i++) {
            if (this.weaponSets.get(i).type.equalsIgnoreCase(type)) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    public Weapon getWeaponByName(String type) {
        int id = getWeaponIdByName(type);
        return id >= 0 ? getWeaponById(id) : null;
    }

    public boolean haveFlare() {
        return this.flare.types.length > 0;
    }

    public boolean haveChaff() {
        return this.chaff != null;
    }
}
