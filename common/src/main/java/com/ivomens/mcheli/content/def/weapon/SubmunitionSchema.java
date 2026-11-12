package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Submunition;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * YAML {@code Submunition} (older files say {@code Bomblet}): bomblets a cluster weapon releases, and
 * projectiles a carrier spawns in flight.
 */
final class SubmunitionSchema {

    static final Field<Submunition, Integer> COUNT =
            Field.of("Count", ints(0, 1000), 0, Submunition::count).txt("bomblet");
    /** Ticks of flight before the bomblets deploy. */
    static final Field<Submunition, Integer> DEPLOY_TIME =
            Field.of("DeployTime", ints(0, 1000), 10, Submunition::deployTime).txt("bombletstime");
    static final Field<Submunition, Float> SPREAD =
            Field.of("Spread", floats(0, 1000), 0.3F, Submunition::spread).txt("bombletdiff");
    static final Field<Submunition, Boolean> SPAWN_IN_AIR =
            Field.of("SpawnInAir", BOOLEAN, false, Submunition::spawnInAir).txt("spawnbulletinair");
    static final Field<Submunition, Integer> SPAWN_MAX_NUM =
            Field.of("SpawnMaxNum", INT, 1, Submunition::spawnMaxNum).txt("spawnbulletmaxnum");
    static final Field<Submunition, Integer> SPAWN_INTERVAL_TICK =
            Field.of("SpawnIntervalTick", INT, 20, Submunition::spawnIntervalTick).txt("spawnbulletintervaltick");
    static final Field<Submunition, Integer> SPAWN_PER_NUM =
            Field.of("SpawnPerNum", INT, 1, Submunition::spawnPerNum).txt("spawnbulletpernum");
    static final Field<Submunition, Boolean> SPAWN_INHERIT_SPEED =
            Field.of("SpawnInheritSpeed", BOOLEAN, false, Submunition::spawnInheritSpeed)
                    .txt("spawnbulletinheritspeed");
    static final Field<Submunition, Boolean> DESTRUCT_AFTER_SPAWN =
            Field.of("DestructAfterSpawn", BOOLEAN, false, Submunition::destructAfterSpawn)
                    .txt("destructafterspawnbullet");
    /** An AHEAD round: bursts ahead of the target. */
    static final Field<Submunition, Boolean> AHEAD = Field.of("Ahead", BOOLEAN, false, Submunition::ahead)
            .txt("ahead");

    static final Section<WeaponDefinition, Submunition> SECTION = Section.of("Submunition",
                    WeaponDefinition::submunition, COUNT, DEPLOY_TIME, SPREAD, SPAWN_IN_AIR, SPAWN_MAX_NUM,
                    SPAWN_INTERVAL_TICK, SPAWN_PER_NUM, SPAWN_INHERIT_SPEED, DESTRUCT_AFTER_SPAWN, AHEAD)
            .alias("Bomblet")
            .unsupported("AheadSolveIntervalTick");

    private SubmunitionSchema() {
    }

    static Submunition build(Values v) {
        return new Submunition(v.get(COUNT), v.get(DEPLOY_TIME), v.get(SPREAD), v.get(SPAWN_IN_AIR),
                v.get(SPAWN_MAX_NUM), v.get(SPAWN_INTERVAL_TICK), v.get(SPAWN_PER_NUM), v.get(SPAWN_INHERIT_SPEED),
                v.get(DESTRUCT_AFTER_SPAWN), v.get(AHEAD));
    }
}
