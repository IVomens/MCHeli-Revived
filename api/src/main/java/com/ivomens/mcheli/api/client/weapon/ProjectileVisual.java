package com.ivomens.mcheli.api.client.weapon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.HitResult;

/**
 * What a client draws for one of an addon's weapon behaviours: particles along the flight, a flash where the
 * projectile ends. It runs on each client's own copy of the projectile, which knows its weapon from MCHeli's normal
 * entity sync; nothing extra is sent, so a visual sees only what that copy knows (position, motion, target, the
 * weapon) and not the server's effect state.
 *
 * <pre>{@code
 * public final class EmpSparks implements ProjectileVisual {
 *     public void onTick(ProjectileVisualContext projectile) {
 *         Vec3 at = projectile.position();
 *         projectile.level().addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, 0, 0);
 *     }
 * }
 * }</pre>
 *
 * <p>One visual object is made per weapon file and shared by all its projectiles; keep per-projectile state in
 * {@link ProjectileVisualContext#data()}. A visual that throws is logged and skipped for the rest of that
 * projectile's life.
 *
 * <p><b>Side and thread.</b> Client only, on the client thread.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement this interface; new hooks only come as default methods.
 */
public interface ProjectileVisual {

    /** After each tick of the client's copy. */
    default void onTick(ProjectileVisualContext projectile) {
    }

    /**
     * The client's copy hit something by itself (rounds and rockets, which clients fly themselves, find their own
     * hits). Most impacts reach a client as the projectile's removal instead: see {@link #onRemoved}.
     */
    default void onImpact(ProjectileVisualContext projectile, HitResult hit) {
    }

    /** The projectile left the client's level: it ended on the server, or went out of the player's view. */
    default void onRemoved(ProjectileVisualContext projectile) {
    }

    /** Makes the visual of one weapon file from the settings written next to the behaviour's id. */
    @FunctionalInterface
    interface Factory {

        /**
         * @param settings the settings of the weapon file (a copy; empty when the file names only the id)
         * @throws RuntimeException when the settings are invalid; MCHeli logs it and draws nothing for this weapon
         */
        ProjectileVisual create(CompoundTag settings);
    }
}
