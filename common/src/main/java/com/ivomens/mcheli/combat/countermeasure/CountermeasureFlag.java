package com.ivomens.mcheli.combat.countermeasure;

import com.ivomens.mcheli.platform.Services;
import net.minecraft.world.entity.Entity;

/**
 * The flags a vehicle carries in its persistent entity data while a countermeasure runs or a seeker
 * looks at it. Other mods read and write the same keys, so they are part of the public contract and
 * never change; this is the one place that spells them.
 */
public enum CountermeasureFlag {

    /** Flares are in the air; heat seekers near the vehicle are decoyed. */
    FLARE_USING("FlareUsing"),
    CHAFF_USING("ChaffUsing"),
    APS_USING("APSUsing"),
    /** A launcher locked on to the vehicle; cleared by the vehicle's missile warning. */
    LOCKED_ON("LockOn"),
    /** A missile is guided towards the vehicle; cleared by the vehicle's missile warning. */
    TRACKED("Tracking");

    private final String key;

    CountermeasureFlag(String key) {
        this.key = key;
    }

    public boolean isSet(Entity entity) {
        return Services.ENTITY_DATA.getPersistentData(entity).getBoolean(this.key);
    }

    public void set(Entity entity, boolean value) {
        Services.ENTITY_DATA.getPersistentData(entity).putBoolean(this.key, value);
    }

    /** Clears the flag if it is set, without touching the data otherwise; returns whether it was set. */
    public boolean clear(Entity entity) {
        if (!this.isSet(entity)) {
            return false;
        }
        this.set(entity, false);
        return true;
    }
}
