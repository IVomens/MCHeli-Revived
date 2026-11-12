package com.ivomens.mcheli.api.weapon;

import net.minecraft.world.phys.Vec3;

/**
 * A projectile whose warhead is going off, as {@link ProjectileEffect#onDetonate} sees it. The effects of the
 * weapon run in order; when any of them cancels the explosion, MCHeli's own explosion is left out (the
 * weapon's hit sound still plays).
 *
 * <p><b>Side and thread.</b> Server side, on the server thread, only during {@link ProjectileEffect#onDetonate}.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface DetonationContext extends ProjectileEffectContext {

    /** Where the warhead goes off. */
    Vec3 at();

    /** Leaves MCHeli's own explosion out, for an effect that makes its own instead. */
    void cancelExplosion();

    /** Whether an earlier effect of this detonation cancelled the explosion. */
    boolean isExplosionCancelled();
}
