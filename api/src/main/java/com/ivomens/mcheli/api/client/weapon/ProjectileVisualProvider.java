package com.ivomens.mcheli.api.client.weapon;

/**
 * The client half of {@link com.ivomens.mcheli.api.weapon.WeaponBehaviourProvider}: registers what clients draw
 * for an addon's weapon behaviours. MCHeli finds every provider with {@link java.util.ServiceLoader} on a client,
 * so a mod registers by listing its class in
 * {@code META-INF/services/com.ivomens.mcheli.api.client.weapon.ProjectileVisualProvider}. A dedicated server never
 * looks the providers up, so they may use client classes freely.
 *
 * <pre>{@code
 * public final class MyVisuals implements ProjectileVisualProvider {
 *     public void registerVisuals(ProjectileVisualRegistrar visuals) {
 *         visuals.visual(ResourceLocation.fromNamespaceAndPath("mymod", "emp"), settings -> new EmpSparks());
 *     }
 * }
 * }</pre>
 *
 * <p><b>Side and thread.</b> Client only, called once on the client thread, the first time a projectile with an
 * addon behaviour appears.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement this interface, so it only changes in a major version. It
 * needs a public constructor without parameters.
 */
@FunctionalInterface
public interface ProjectileVisualProvider {

    /** Adds the mod's visuals. */
    void registerVisuals(ProjectileVisualRegistrar visuals);
}
