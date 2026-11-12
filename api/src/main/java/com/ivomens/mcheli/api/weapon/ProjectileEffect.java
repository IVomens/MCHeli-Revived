package com.ivomens.mcheli.api.weapon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * An addon's projectile effect: code that runs at points of a projectile's life. A weapon file lists effects under
 * {@code ProjectileEffects}; each of its projectiles (the bomblets of a cluster weapon included) runs them in that
 * order, next to everything the weapon does anyway. Implement only the hooks you need; the others do nothing.
 *
 * <pre>{@code
 * public final class Incendiary implements ProjectileEffect {
 *     public void onBlockHit(ProjectileEffectContext projectile, BlockHitResult hit) {
 *         BlockPos above = hit.getBlockPos().relative(hit.getDirection());
 *         if (projectile.level().isEmptyBlock(above)) {
 *             projectile.level().setBlockAndUpdate(above, Blocks.FIRE.defaultBlockState());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>The life of a projectile, as the hooks see it: {@link #onSpawn} once, then {@link #onTick} after each tick of
 * flight; {@link #onEntityHit} and {@link #onBlockHit} for what it hits (a piercing round hits several times), and
 * finally either {@link #onDetonate} when its warhead goes off or {@link #onExpire} when it ends without going off.
 * A projectile whose chunk unloads simply stops; no hook runs.
 *
 * <p>One effect object is made per weapon file and shared by all its projectiles: keep per-projectile state in
 * {@link ProjectileView#data()}, not in fields. An effect that throws is logged and skipped for the rest of that
 * projectile's life.
 *
 * <p><b>Side and thread.</b> Server side, on the server thread. For what clients should see, register a
 * {@code com.ivomens.mcheli.api.client.weapon.ProjectileVisual} under the same id.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement this interface; new hooks only come as default methods.
 */
public interface ProjectileEffect {

    /** Once, at the start of the projectile's first tick, before it moves. */
    default void onSpawn(ProjectileEffectContext projectile) {
    }

    /** After each tick of flight that the projectile survived, its guidance included. */
    default void onTick(ProjectileEffectContext projectile) {
    }

    /** It hit an entity, before the hit's damage and explosion. */
    default void onEntityHit(ProjectileEffectContext projectile, EntityHitResult hit) {
    }

    /** It hit a block (or a fuze set it off as if it hit the block below), before the explosion. */
    default void onBlockHit(ProjectileEffectContext projectile, BlockHitResult hit) {
    }

    /**
     * Its warhead goes off: at the end of an impact, or by a fuze (time, delay, airburst, proximity, altitude, a
     * mine's trigger). This runs even when the weapon has no explosion of its own. Call
     * {@link DetonationContext#cancelExplosion()} to replace MCHeli's explosion with your own; the damage of a
     * direct hit is still dealt.
     */
    default void onDetonate(DetonationContext projectile) {
    }

    /**
     * It ended without its warhead going off: out of range or fuel, lost its target, self-destructed, or removed by
     * something else.
     */
    default void onExpire(ProjectileEffectContext projectile) {
    }

    /** Makes the effect of one weapon file from the settings written next to its id. */
    @FunctionalInterface
    interface Factory {

        /**
         * @param settings the settings of the weapon file (a copy; empty when the file names only the id)
         * @throws RuntimeException when the settings are invalid; MCHeli reports it and leaves the effect out
         */
        ProjectileEffect create(CompoundTag settings);
    }
}
