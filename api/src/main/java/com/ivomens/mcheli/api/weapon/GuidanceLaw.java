package com.ivomens.mcheli.api.weapon;

import net.minecraft.nbt.CompoundTag;

/**
 * An addon's guidance law: how a projectile steers. A weapon file picks it with {@code Guidance: <id>}, and it then
 * replaces the guidance the weapon's {@code Type} picks; everything else about the projectile (how it is fired,
 * its motor, gravity, drag, fuzes, collisions and warhead) stays as the type and file make it.
 *
 * <pre>{@code
 * public final class PurePursuit implements GuidanceLaw {
 *     public SteeringCommand steer(GuidanceContext missile) {
 *         Entity target = missile.target();
 *         return target == null ? SteeringCommand.keepCourse() : SteeringCommand.track(target.position());
 *     }
 * }
 * }</pre>
 *
 * <p>Each tick after the projectile moved, MCHeli asks the law for a {@link SteeringCommand} and carries it out
 * within the weapon's limits, exactly as for its own missiles:
 * <ul>
 *   <li>the law is not asked during the rigid launch phase ({@code LockOn: Delay} ticks), nor after
 *       {@code TickEndHoming};</li>
 *   <li>a projectile more than 1850 blocks from its target is removed;</li>
 *   <li>a target hidden by flares or chaff the weapon falls for goes to {@link #decoyed} instead of
 *       {@link #steer};</li>
 *   <li>a turn is limited by the weapon's {@code TurningFactor}, and a tracked point outside the seeker cone or the
 *       doppler gate breaks the lock.</li>
 * </ul>
 * The projectile's fuzes (proximity, airburst, time) still work on their own.
 *
 * <p>One law object is made per weapon file and shared by all its projectiles: keep per-projectile state in
 * {@link ProjectileView#data()}, not in fields.
 *
 * <p><b>Side and thread.</b> Server side, on the server thread. Clients see the steered projectile through
 * MCHeli's normal entity sync; nothing is needed on the client.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement this interface; new methods only come as default methods.
 */
@FunctionalInterface
public interface GuidanceLaw {

    /** One tick's steering command. Never return null; {@link SteeringCommand#keepCourse()} steers nothing. */
    SteeringCommand steer(GuidanceContext projectile);

    /**
     * What the projectile does when countermeasures it falls for hide its target. The default self-destructs, as
     * MCHeli's air-to-air missiles do; return {@link SteeringCommand#dropTarget()} to fly on without a lock instead.
     */
    default SteeringCommand decoyed(GuidanceContext projectile) {
        return SteeringCommand.selfDestruct();
    }

    /** Makes the law of one weapon file from the settings written next to its id. */
    @FunctionalInterface
    interface Factory {

        /**
         * @param settings the settings of the weapon file (a copy; empty when the file names only the id)
         * @throws RuntimeException when the settings are invalid; MCHeli reports it and keeps its own guidance
         */
        GuidanceLaw create(CompoundTag settings);
    }
}
