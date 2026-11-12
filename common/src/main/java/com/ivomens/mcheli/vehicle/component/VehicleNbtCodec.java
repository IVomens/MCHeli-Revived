package com.ivomens.mcheli.vehicle.component;

/**
 * A vehicle's save format: every NBT key a vehicle has ever saved under. The names are part of the
 * save format and must never change, spelling mistakes included ({@link #ROTOR_ANGLE}); saves from
 * before a key existed are handled where the key is read. The order the components are read and
 * written in is the assembly's ({@code AircraftComponents#save}).
 */
public final class VehicleNbtCodec {

    // identity and appearance
    public static final String TYPE_NAME = "TypeName";
    public static final String UNIQUE_ID = "AircraftUniqueId";
    public static final String TEXTURE_NAME = "TextureName";
    public static final String LIVERY = "Livery";
    public static final String COMMAND = "AcCommand";

    // body and attitude
    public static final String ROLL = "AcRoll";
    public static final String LAST_RIDER_YAW = "AcLastRYaw";
    public static final String LAST_RIDER_PITCH = "AcLastRPitch";
    public static final String DISMOUNTED_FROM_MINECART = "AcDismounted";

    // parts and lights
    public static final String PART_STATUS = "PartStatus";
    public static final String SEARCH_LIGHT = "SearchLight";

    // damage
    public static final String DAMAGE = "AcDamage";
    public static final String DESPAWN_COUNT = "AcDespawnCount";
    public static final String ERA_STATE = "AcERAState";

    // crew, fuel and weapons
    public static final String GUNNER_STATUS = "AcGunnerStatus";
    public static final String FUEL = "AcFuel";
    public static final String FUEL_TYPE = "AcFuelType";
    public static final String WEAPON_AMMO = "AcWeaponsAmmo";

    // UAV
    public static final String PAIRED_STATION = "PairedStation";

    // ownership; only written for vehicles that have an owner, older saves have none
    public static final String OWNER = "McheliOwner";
    public static final String OWNER_NAME = "McheliOwnerName";
    public static final String LOCKED = "McheliLocked";

    // helicopters; "rotetionRotor" is misspelled in every save there is
    public static final String ROTOR_SPEED = "RotorSpeed";
    public static final String ROTOR_ANGLE = "rotetionRotor";
    public static final String FOLDED_BLADES = "FoldBlade";
    /** Very old helicopter saves used these instead of the shared keys. */
    public static final String LEGACY_HELI_UNIQUE_ID = "HeliUniqueId";
    public static final String LEGACY_HELI_TYPE = "HeliType";

    // the vehicle's item, when it is picked up and placed again
    public static final String ITEM_COMMAND = "McheliCommand";
    public static final String ITEM_FUEL = "MCH_Fuel";

    private VehicleNbtCodec() {
    }
}
