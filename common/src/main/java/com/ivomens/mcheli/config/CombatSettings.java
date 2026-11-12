package com.ivomens.mcheli.config;

import java.util.List;

/** Weapons, damage, collisions and what explosions and vehicles may destroy. Server side. */
public record CombatSettings(boolean explosionDestroyBlock, boolean explosionFlamingBlock,
                             boolean collisionDestroyBlock, boolean collisionEntityDamage,
                             boolean collisionEntityTankDamage, boolean collisionDamage, double collisionDamageFactor,
                             boolean infinityAmmo, double hurtResistantTime, double stingerLockRange,
                             int rangeFinderSpotDist, int rangeFinderSpotTime, boolean rangeFinderConsume,
                             GunnerRange gunnerVsMonster, GunnerRange gunnerVsPlayer, DamageFactors damage,
                             List<String> ignoredBulletHits, BreakRules blocks) {

    public static final BoolOption EXPLOSION_DESTROY_BLOCK = BoolOption.of("Explosion_DestroyBlock", true);
    public static final BoolOption EXPLOSION_FLAMING_BLOCK = BoolOption.of("Explosion_FlamingBlock", true);
    public static final BoolOption COLLISION_DESTROY_BLOCK = BoolOption.of("Collision_DestroyBlock", true);
    public static final BoolOption COLLISION_ENTITY_DAMAGE = BoolOption.of("Collision_EntityDamage", true);
    public static final BoolOption COLLISION_ENTITY_TANK_DAMAGE = BoolOption.of("Collision_EntityTankDamage", false);
    public static final BoolOption COLLISION_DAMAGE = BoolOption.of("CollisionDamage", true)
            .describe("CollisionDamage = true lets vehicles take damage when they crash into blocks or other"
                    + " vehicles at speed; false disables collision damage completely.");
    public static final DoubleOption COLLISION_DAMAGE_FACTOR = DoubleOption.of("CollisionDamageFactor", 1.0)
            .atLeast(0.0)
            .describe("CollisionDamageFactor = multiplier applied to collision damage (1.0 = default, 0.5 = half,"
                    + " 2.0 = double). Only used when CollisionDamage = true.");
    public static final BoolOption INFINITY_AMMO = BoolOption.of("InfinityAmmo", false);
    public static final DoubleOption HURT_RESISTANT_TIME = DoubleOption.of("HurtResistantTime", 0.0)
            .range(0.0, 10000.0);
    public static final DoubleOption STINGER_LOCK_RANGE = DoubleOption.of("StingerLockRange", 320.0).since("1.0.0");
    public static final IntOption RANGE_FINDER_SPOT_DIST = IntOption.of("RangeFinderSpotDist", 400);
    public static final IntOption RANGE_FINDER_SPOT_TIME = IntOption.of("RangeFinderSpotTime", 15);
    public static final BoolOption RANGE_FINDER_CONSUME = BoolOption.of("RangeFinderConsume", true);
    public static final IntOption GUNNER_VS_MONSTER_HORIZONTAL = IntOption.of("RangeOfGunner_VsMonster_Horizontal", 80);
    public static final IntOption GUNNER_VS_MONSTER_VERTICAL = IntOption.of("RangeOfGunner_VsMonster_Vertical", 160);
    public static final IntOption GUNNER_VS_PLAYER_HORIZONTAL = IntOption.of("RangeOfGunner_VsPlayer_Horizontal", 200);
    public static final IntOption GUNNER_VS_PLAYER_VERTICAL = IntOption.of("RangeOfGunner_VsPlayer_Vertical", 300);
    /** Entity classes bullets fly through, matched by part of the class name. */
    public static final ListOption IGNORED_BULLET_HITS = ListOption.of("IgnoreBulletHit",
            List.of("flansmod.common.guns.EntityBullet", "flansmod.common.guns.EntityGrenade"));

    /** How far an AI gunner looks for targets, in blocks. */
    public record GunnerRange(int horizontal, int vertical) {
    }

    public CombatSettings {
        ignoredBulletHits = List.copyOf(ignoredBulletHits);
    }

    static CombatSettings from(ConfigValues v) {
        return new CombatSettings(v.get(EXPLOSION_DESTROY_BLOCK), v.get(EXPLOSION_FLAMING_BLOCK),
                v.get(COLLISION_DESTROY_BLOCK), v.get(COLLISION_ENTITY_DAMAGE), v.get(COLLISION_ENTITY_TANK_DAMAGE),
                v.get(COLLISION_DAMAGE), v.get(COLLISION_DAMAGE_FACTOR), v.get(INFINITY_AMMO),
                v.get(HURT_RESISTANT_TIME), v.get(STINGER_LOCK_RANGE), v.get(RANGE_FINDER_SPOT_DIST),
                v.get(RANGE_FINDER_SPOT_TIME), v.get(RANGE_FINDER_CONSUME),
                new GunnerRange(v.get(GUNNER_VS_MONSTER_HORIZONTAL), v.get(GUNNER_VS_MONSTER_VERTICAL)),
                new GunnerRange(v.get(GUNNER_VS_PLAYER_HORIZONTAL), v.get(GUNNER_VS_PLAYER_VERTICAL)),
                DamageFactors.from(v.get(DamageFactors.OPTION)),
                ListOption.values(v.get(IGNORED_BULLET_HITS)), BreakRules.from(v));
    }
}
