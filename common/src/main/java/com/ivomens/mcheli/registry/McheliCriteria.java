package com.ivomens.mcheli.registry;

import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.registries.Registries;

public class McheliCriteria {

    public static final SimpleTrigger PUT_AIRCRAFT = create("put_aircraft");
    public static final SimpleTrigger SUPPLY_AMMO = create("supply_ammo");
    public static final SimpleTrigger SUPPLY_FUEL = create("supply_fuel");
    public static final SimpleTrigger RELIEF_SUPPLIES = create("relief_supplies");
    public static final SimpleTrigger RIDING_VALKYRIES = create("riding_valkyries");
    public static final SimpleTrigger VILLAGER_HURT_BULLET = create("villager_hurt_bullet");

    /** Loading the class registers the trigger types. Call once during mod construction. */
    public static void registerTriggers() {
    }

    private static SimpleTrigger create(String shortName) {
        SimpleTrigger trigger = new SimpleTrigger(Ids.of(shortName));
        Services.REGISTRY.register(Registries.TRIGGER_TYPE, shortName, () -> trigger);
        return trigger;
    }
}
