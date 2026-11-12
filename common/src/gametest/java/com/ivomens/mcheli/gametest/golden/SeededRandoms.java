package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.component.countermeasure.FlareDispenser;
import net.minecraft.util.RandomSource;
import com.ivomens.mcheli.combat.CombatRandom;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.tank.WheelManager;
import com.ivomens.mcheli.vehicle.component.CountermeasureComponent;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Field;

/**
 * Puts every random number generator the vehicle code can reach into a known state.
 * <p>
 * The weapons and explosions draw from the level's {@link CombatRandom}, seeded directly. The generators
 * a vehicle owns (its flare dispenser's, a tank's wheel dust) are reseeded in place through reflection;
 * when such a field moves or is renamed this class fails loudly instead of silently recording a
 * nondeterministic run.
 */
public final class SeededRandoms {

    /**
     * The seed offsets every generator always had, so the recorded runs keep their numbers: the weapon
     * streams of {@link CombatRandom} took the places of the old static generators.
     */
    private static final int AIM_SEED_OFFSET = 0;
    private static final int BARREL_SEED_OFFSET = 1;
    private static final int WHEEL_SEED_OFFSET = 3;
    private static final int BLAST_SEED_OFFSET = 4;
    private static final int RAY_SEED_OFFSET = 5;

    private SeededRandoms() {
    }

    /**
     * Seeds the level's generators. The level generator is shared
     * with chunk ticking, so this only fixes it up to the next precipitation roll; scripts keep
     * explosions away from blocks so it cannot matter.
     */
    public static void seedGlobal(ServerLevel level, long seed) {
        CombatRandom combat = CombatRandom.of(level);
        combat.aim().setSeed(seed + AIM_SEED_OFFSET);
        combat.barrels().setSeed(seed + BARREL_SEED_OFFSET);
        combat.blasts().setSeed(seed + BLAST_SEED_OFFSET);
        combat.rays().setSeed(seed + RAY_SEED_OFFSET);
        level.random.setSeed(seed);
    }

    /**
     * Seeds the vehicle's entity random, its flare dispenser and a tank's wheel dust right after it was
     * created. The wheel dust is a client effect; its seed keeps the offset the old static generator had.
     */
    public static void seedVehicle(AircraftEntity vehicle, long seed) {
        vehicle.getRandom().setSeed(seed);
        FlareDispenser flares = (FlareDispenser) read(field(CountermeasureComponent.class, "flares"),
                vehicle.countermeasures());
        ((RandomSource) read(field(FlareDispenser.class, "random"), flares)).setSeed(seed + 1);
        if (vehicle instanceof TankEntity tank) {
            ((RandomSource) read(field(WheelManager.class, "dustRandom"), tank.wheels())).setSeed(seed + WHEEL_SEED_OFFSET);
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("random generator " + owner.getSimpleName() + "." + name
                    + " moved; update SeededRandoms", e);
        }
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
