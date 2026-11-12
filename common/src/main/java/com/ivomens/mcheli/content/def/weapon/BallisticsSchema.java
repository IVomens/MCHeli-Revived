package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Ballistics;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * YAML {@code Ballistics}: how the projectile flies. The first five settings are always written, so a
 * converted file shows the flight model even when it is the default.
 */
final class BallisticsSchema {

    /** Speed added per tick, up to the projectile's speed. */
    static final Field<Ballistics, Float> ACCELERATION =
            Field.of("Acceleration", floats(0, 100), 1.0F, Ballistics::acceleration).alias("Accel")
                    .txt("acceleration").alwaysWritten();
    static final Field<Ballistics, Float> ACCELERATION_IN_WATER =
            Field.of("AccelerationInWater", floats(0, 100), 1.0F, Ballistics::accelerationInWater)
                    .txt("accelerationinwater").alwaysWritten();
    /**
     * Downward pull per tick. A legacy TXT {@code Type = bomb} or {@code dispenser} line sets it, and the in-water
     * one, to -0.03; see {@link WeaponTxtRules#type}.
     */
    static final Field<Ballistics, Float> GRAVITY =
            Field.of("Gravity", floats(-50, 50), 0.0F, Ballistics::gravity).txt("gravity").alwaysWritten();
    static final Field<Ballistics, Float> GRAVITY_IN_WATER =
            Field.of("GravityInWater", floats(-50, 50), 0.0F, Ballistics::gravityInWater).txt("gravityinwater")
                    .alwaysWritten();
    /** Speed kept per tick under water. */
    static final Field<Ballistics, Float> VELOCITY_IN_WATER =
            Field.of("VelocityInWater", FLOAT, 0.999F, Ballistics::velocityInWater).txt("velocityinwater")
                    .alwaysWritten();
    /** Speed change per tick between the start and end tick; positive accelerates. */
    static final Field<Ballistics, Float> SPEED_FACTOR =
            Field.of("SpeedFactor", FLOAT, 0.0F, Ballistics::speedFactor).txt("speedfactor");
    static final Field<Ballistics, Integer> SPEED_FACTOR_START_TICK =
            Field.of("SpeedFactorStartTick", INT, 0, Ballistics::speedFactorStartTick).txt("speedfactorstarttick");
    static final Field<Ballistics, Integer> SPEED_FACTOR_END_TICK =
            Field.of("SpeedFactorEndTick", INT, 0, Ballistics::speedFactorEndTick).txt("speedfactorendtick");
    /** The projectile starts with the shooter's velocity added. */
    static final Field<Ballistics, Boolean> SPEED_DEPENDS_AIRCRAFT =
            Field.of("SpeedDependsAircraft", BOOLEAN, false, Ballistics::speedDependsAircraft)
                    .txt("speeddependsaircraft");
    /** Blocks or entities the projectile passes through. */
    static final Field<Ballistics, Integer> PIERCING =
            Field.of("Piercing", ints(0, 100_000), 0, Ballistics::piercing).txt("piercing");
    /** Spread of the block-break particles on impact, 0.1 for a rifle to 0.6 for an anti-tank round. */
    static final Field<Ballistics, Float> FLAK_SPREAD =
            Field.of("FlakSpread", FLOAT, 0.3F, Ballistics::flakSpread).txt("flakparticlesdiff");

    static final Section<WeaponDefinition, Ballistics> SECTION = Section.of("Ballistics",
            WeaponDefinition::ballistics, ACCELERATION, ACCELERATION_IN_WATER, GRAVITY, GRAVITY_IN_WATER,
            VELOCITY_IN_WATER, SPEED_FACTOR, SPEED_FACTOR_START_TICK, SPEED_FACTOR_END_TICK, SPEED_DEPENDS_AIRCRAFT,
            PIERCING, FLAK_SPREAD);

    private BallisticsSchema() {
    }

    static Ballistics build(Values v) {
        return new Ballistics(v.get(ACCELERATION), v.get(ACCELERATION_IN_WATER), v.get(GRAVITY),
                v.get(GRAVITY_IN_WATER), v.get(VELOCITY_IN_WATER), v.get(SPEED_FACTOR),
                v.get(SPEED_FACTOR_START_TICK), v.get(SPEED_FACTOR_END_TICK), v.get(SPEED_DEPENDS_AIRCRAFT),
                v.get(PIERCING), v.get(FLAK_SPREAD));
    }
}
