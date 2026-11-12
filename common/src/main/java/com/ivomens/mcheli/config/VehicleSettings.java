package com.ivomens.mcheli.config;

/** Placing, riding, repairing and losing vehicles. Server side unless noted. */
public record VehicleSettings(boolean placeableOnSpongeOnly, boolean itemDamage, boolean itemFuel,
                              double autoRepairHp, boolean infinityFuel, boolean dismountAll,
                              boolean mountMinecartHeli, boolean mountMinecartPlane, boolean mountMinecartVehicle,
                              boolean mountMinecartTank, boolean preventingBroken, boolean dropItemInCreativeMode,
                              boolean breakableOnlyPickaxe, boolean enablePutRackInFlying, int despawnCount,
                              boolean fixVehicleAtPlacedPoint, boolean killPassengersWhenDestroyed,
                              double soundRange) {

    public static final BoolOption PLACEABLE_ON_SPONGE_ONLY = BoolOption.of("PlaceableOnSpongeOnly", false);
    public static final BoolOption ITEM_DAMAGE = BoolOption.of("ItemDamage", true);
    public static final BoolOption ITEM_FUEL = BoolOption.of("ItemFuel", true);
    public static final DoubleOption AUTO_REPAIR_HP = DoubleOption.of("AutoRepairHP", 0.5);
    public static final BoolOption INFINITY_FUEL = BoolOption.of("InfinityFuel", false);
    public static final BoolOption DISMOUNT_ALL = BoolOption.of("DismountAll", false);
    public static final BoolOption MOUNT_MINECART_HELI = BoolOption.of("MountMinecartHeli", true);
    public static final BoolOption MOUNT_MINECART_PLANE = BoolOption.of("MountMinecartPlane", true);
    public static final BoolOption MOUNT_MINECART_VEHICLE = BoolOption.of("MountMinecartVehicle", false);
    public static final BoolOption MOUNT_MINECART_TANK = BoolOption.of("MountMinecartTank", true);
    public static final BoolOption PREVENTING_BROKEN = BoolOption.of("PreventingBroken", false);
    public static final BoolOption DROP_ITEM_IN_CREATIVE_MODE = BoolOption.of("DropItemInCreativeMode", false);
    public static final BoolOption BREAKABLE_ONLY_PICKAXE = BoolOption.of("BreakableOnlyPickaxe", false);
    public static final BoolOption ENABLE_PUT_RACK_IN_FLYING = BoolOption.of("EnablePutRackInFlying", true);
    public static final IntOption DESPAWN_COUNT = IntOption.of("DespawnCount", 25).range(1, Integer.MAX_VALUE);
    public static final BoolOption FIX_VEHICLE_AT_PLACED_POINT = BoolOption.of("FixVehicleAtPlacedPoint", true);
    public static final BoolOption KILL_PASSENGERS_WHEN_DESTROYED = BoolOption.of("KillPassengersWhenDestroyed",
            false);
    /** Client side. */
    public static final DoubleOption SOUND_RANGE = DoubleOption.of("VehicleSoundRange", 256.0).range(16.0, 4096.0)
            .describe("VehicleSoundRange = distance in blocks at which engine, rotor and turbine sounds and heavy"
                    + " weapon sounds can be heard (default 256).");

    static VehicleSettings from(ConfigValues v) {
        return new VehicleSettings(v.get(PLACEABLE_ON_SPONGE_ONLY), v.get(ITEM_DAMAGE), v.get(ITEM_FUEL),
                v.get(AUTO_REPAIR_HP), v.get(INFINITY_FUEL), v.get(DISMOUNT_ALL), v.get(MOUNT_MINECART_HELI),
                v.get(MOUNT_MINECART_PLANE), v.get(MOUNT_MINECART_VEHICLE), v.get(MOUNT_MINECART_TANK),
                v.get(PREVENTING_BROKEN), v.get(DROP_ITEM_IN_CREATIVE_MODE), v.get(BREAKABLE_ONLY_PICKAXE),
                v.get(ENABLE_PUT_RACK_IN_FLYING), v.get(DESPAWN_COUNT), v.get(FIX_VEHICLE_AT_PLACED_POINT),
                v.get(KILL_PASSENGERS_WHEN_DESTROYED), v.get(SOUND_RANGE));
    }
}
