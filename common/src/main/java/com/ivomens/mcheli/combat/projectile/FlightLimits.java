package com.ivomens.mcheli.combat.projectile;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

/**
 * How far steered missiles may fly from their shooter. The limit is a setting of the wingman module,
 * which hands it in here so the projectiles never depend on that module.
 */
public final class FlightLimits {

    /** No limit. */
    public static final int UNLIMITED = -1;

    private static final AtomicReference<IntSupplier> STEERED_MISSILE_RANGE = new AtomicReference<>(() -> UNLIMITED);

    private FlightLimits() {
    }

    /** Where the steered missile range comes from from now on; a negative range is no limit. */
    public static void setSteeredMissileRange(IntSupplier range) {
        STEERED_MISSILE_RANGE.set(range);
    }

    /** How far, in blocks, a TV missile may get from its shooter before it is removed; negative for no limit. */
    public static int steeredMissileRange() {
        return STEERED_MISSILE_RANGE.get().getAsInt();
    }
}
