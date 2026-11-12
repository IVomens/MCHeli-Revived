package com.ivomens.mcheli.api.weapon;

/**
 * Lets a mod add weapon behaviours that content files then pick by id: guidance laws ({@link GuidanceLaw}) and
 * projectile effects ({@link ProjectileEffect}). MCHeli finds every provider with {@link java.util.ServiceLoader}
 * before it loads its content, so a mod registers by listing its class in
 * {@code META-INF/services/com.ivomens.mcheli.api.weapon.WeaponBehaviourProvider}; no code has to run first.
 *
 * <pre>{@code
 * public final class MyBehaviours implements WeaponBehaviourProvider {
 *     public void registerBehaviours(WeaponBehaviourRegistrar behaviours) {
 *         behaviours.guidance(ResourceLocation.fromNamespaceAndPath("mymod", "beam_rider"), BeamRider::new);
 *         behaviours.projectileEffect(ResourceLocation.fromNamespaceAndPath("mymod", "emp"), Emp::new);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Side and thread.</b> Called once on both sides, on the thread that constructs MCHeli, before any content
 * loads and before the registries open. Only register here; the game is not set up yet.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement this interface, so it only changes in a major version.
 * It needs a public constructor without parameters.
 */
@FunctionalInterface
public interface WeaponBehaviourProvider {

    /** Adds the mod's behaviours. */
    void registerBehaviours(WeaponBehaviourRegistrar behaviours);
}
