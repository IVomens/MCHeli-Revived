package com.ivomens.mcheli.combat.weapon;

/**
 * The numbers a mount fires with, taken from the definition once per side.
 *
 * @param power                 damage of a round
 * @param speed                 launch speed (the definition's {@code Acceleration})
 * @param explosionPower        explosion of the projectile
 * @param explosionPowerInWater explosion under water
 * @param modes                 how many modes the weapon cycles through
 * @param piercing              entities and blocks a round passes before it stops
 * @param heatPerShot           heat a shot adds; the client counts one more to stay on the safe side
 * @param interval              cooldown after a player's shot: the definition's on the server, padded on the client
 * @param paddedInterval        cooldown after a shot by anyone else (see {@link Cooldowns#padded})
 */
public record MountStats(int power, float speed, int explosionPower, int explosionPowerInWater, int modes, int piercing,
                         int heatPerShot, int interval, int paddedInterval) {

    /** Close air support never fires faster than this, in ticks. */
    private static final int MAX_STRIKE_INTERVAL = 65286;

    static MountStats of(WeaponSpec info, WeaponKind kind, boolean client) {
        int heat = info.missile().heat().maxHeatCount() > 0 ? Math.max(2, info.missile().heat().heatCount())
                : info.missile().heat().heatCount();
        int padded = Cooldowns.padded(info.delay());
        int interval = client ? padded : info.delay();
        // the high-explosive mode of a tracer gun needs an explosion to switch to
        int modes = kind == WeaponKind.MACHINE_GUN_TRACER && info.explosion().power() == 0 ? 0
                : info.definition().modes();
        if (kind == WeaponKind.CLOSE_AIR_SUPPORT) {
            interval = Math.min(interval, MAX_STRIKE_INTERVAL);
        }
        return new MountStats(info.definition().baseDamage(), info.ballistics().acceleration(),
                info.explosion().power(), info.explosionInWater(), modes, info.ballistics().piercing(),
                client ? heat + 1 : heat, interval, padded);
    }
}
