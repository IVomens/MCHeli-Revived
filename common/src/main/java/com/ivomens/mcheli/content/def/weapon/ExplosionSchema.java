package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Explosion;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * YAML {@code Explosion}: what happens on impact. Legacy TXT clamps several of these values where YAML does
 * not; both ranges are kept so files load as they always did. Times are in ticks in both formats.
 */
final class ExplosionSchema {

    /** Explosion strength; 0 for none. TXT {@code Explosion} is clamped to 0..200. */
    static final Field<Explosion, Integer> POWER =
            Field.of("Power", INT, 0, Explosion::power).txt(ints(0, 200), "explosion");
    /** Explosion size; 0 means {@link #POWER}. */
    static final Field<Explosion, Float> RADIUS = Field.of("Radio", floats(0, 100), 0.0F, Explosion::radius);
    /** Radius within which the explosion hurts; 0 means the explosion size. */
    static final Field<Explosion, Float> DAMAGE_RADIUS =
            Field.of("DamageRadio", floats(0, 100), 0.0F, Explosion::damageRadius);
    /** Strength against blocks; -1 means {@link #POWER}. */
    static final Field<Explosion, Integer> DESTRUCTION_POWER =
            Field.of("DestructionPower", INT, -1, Explosion::destructionPower).txt(ints(0, 200), "explosionblock");
    static final Field<Explosion, Integer> POWER_UNDERWATER =
            Field.of("PowerUnderwater", INT, 0, Explosion::powerUnderwater).txt(ints(0, 50), "explosioninwater");
    /** Height above ground at which the projectile explodes on its own. */
    static final Field<Explosion, Integer> ALTITUDE =
            Field.of("ExplosionAltitude", INT, 0, Explosion::altitude).txt(ints(0, 100), "explosionaltitude");
    /** Ticks of flight before the projectile explodes; 0 for no time fuse. */
    static final Field<Explosion, Integer> FUSE_TIME =
            Field.of("FuseTime", INT, 0, Explosion::fuseTime).txt(ints(0, 100_000), "timefuse");
    /** Ticks between the first impact and the explosion. */
    static final Field<Explosion, Integer> FUSE_DELAY =
            Field.of("FuseDelay", INT, 0, Explosion::fuseDelay).txt(ints(0, 100_000), "delayfuse");
    /** Bounciness while a delayed fuse burns; ignored without one. */
    static final Field<Explosion, Float> FUSE_REBOUND =
            Field.of("FuseRebound", floats(0, 100_000), 0.0F, Explosion::fuseRebound).txt("bound");
    static final Field<Explosion, Boolean> FLAMING =
            Field.of("Flaming", BOOLEAN, false, Explosion::flaming).txt("flaming");
    static final Field<Explosion, Boolean> CAN_AIRBURST =
            Field.of("CanAirburst", BOOLEAN, false, Explosion::canAirburst).txt("canairburst");
    /** Strength of an airburst; 0 means {@link #POWER}. */
    static final Field<Explosion, Integer> AIRBURST_POWER =
            Field.of("ExplosionAirburst", ints(0, 50), 0, Explosion::airburstPower).txt("explosionairburst");
    static final Field<Explosion, Boolean> THROUGH_WALL =
            Field.of("ThroughWall", BOOLEAN, false, Explosion::throughWall).txt("explosionthroughwall");
    /** Share of the damage that passes a wall. TXT clamps it to 0..1. */
    static final Field<Explosion, Float> THROUGH_WALL_FACTOR = Field.of("ThroughWallFactor", FLOAT, 1.0F,
            Explosion::throughWallFactor).txt(floats(0, 1), "explosionthroughwallfactor");
    /** Use the newer block breaking, which honours {@link #DESTRUCTION_POWER} and the radii. */
    static final Field<Explosion, Boolean> NEW_BREAK =
            Field.of("NewBreak", BOOLEAN, false, Explosion::newBreak).txt("isnewexplosionbreak");
    /** Distance to a target at which the projectile explodes. */
    static final Field<Explosion, Float> PROXIMITY_FUSE_DIST =
            Field.of("ProximityFuseDist", floats(0, 2000), 0.0F, Explosion::proximityFuseDist)
                    .txt("proximityfusedist");
    /** A fuel-air explosion: no underwater power, needs an explosion power. */
    static final Field<Explosion, Boolean> FUEL_AIR =
            Field.of("FAE", BOOLEAN, false, Explosion::fuelAir).alias("FuelAir").txt("fae");

    static final Section<WeaponDefinition, Explosion> SECTION = Section.of("Explosion", WeaponDefinition::explosion,
                    POWER, RADIUS, DAMAGE_RADIUS, DESTRUCTION_POWER, POWER_UNDERWATER, ALTITUDE, FUSE_TIME, FUSE_DELAY,
                    FUSE_REBOUND, FLAMING, CAN_AIRBURST, AIRBURST_POWER, THROUGH_WALL, THROUGH_WALL_FACTOR, NEW_BREAK,
                    PROXIMITY_FUSE_DIST, FUEL_AIR)
            .unsupported("Type", "CanDestroyBlocks");

    private ExplosionSchema() {
    }

    static Explosion build(Values v) {
        return new Explosion(v.get(POWER), v.get(RADIUS), v.get(DAMAGE_RADIUS), v.get(DESTRUCTION_POWER),
                v.get(POWER_UNDERWATER), v.get(ALTITUDE), v.get(FUSE_TIME), v.get(FUSE_DELAY), v.get(FUSE_REBOUND),
                v.get(FLAMING), v.get(CAN_AIRBURST), v.get(AIRBURST_POWER), v.get(THROUGH_WALL),
                v.get(THROUGH_WALL_FACTOR), v.get(NEW_BREAK), v.get(PROXIMITY_FUSE_DIST), v.get(FUEL_AIR));
    }
}
