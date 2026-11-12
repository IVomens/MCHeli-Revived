package com.ivomens.mcheli.api.vehicle.extension;

/**
 * Lets a mod add vehicle behaviours ({@link VehicleBehaviour}) and movement models ({@link MovementModel}) that
 * vehicle files can then name. MCHeli finds every provider with {@link java.util.ServiceLoader}, so a mod
 * registers by listing its class in
 * {@code META-INF/services/com.ivomens.mcheli.api.vehicle.extension.VehicleExtensionProvider}; no code has to
 * run first.
 *
 * <pre>{@code
 * public final class MyExtensions implements VehicleExtensionProvider {
 *     public void register(VehicleExtensionRegistrar registrar) {
 *         registrar.behaviour(ResourceLocation.fromNamespaceAndPath("mymod", "winch"),
 *                 BehaviourTiming.AFTER_MOVEMENT, WinchSettings::fromTag, WinchBehaviour::new);
 *         registrar.movementModel(ResourceLocation.fromNamespaceAndPath("mymod", "hovercraft"),
 *                 HoverSettings::fromTag, HovercraftModel::new);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Side and thread.</b> Called once on both sides, the first time MCHeli needs the registered types, which
 * is while it constructs and loads its content, before any vehicle exists. Only register here; the game is not
 * set up yet.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement this interface, so it only changes in a major version;
 * new kinds of extension arrive as new methods of {@link VehicleExtensionRegistrar}. It needs a public
 * constructor without parameters.
 */
@FunctionalInterface
public interface VehicleExtensionProvider {

    /** Registers the mod's behaviour types and movement models. */
    void register(VehicleExtensionRegistrar registrar);
}
