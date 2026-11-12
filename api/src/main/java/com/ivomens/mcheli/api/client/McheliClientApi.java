package com.ivomens.mcheli.api.client;

/**
 * The client half of the MCHeli: Revived API: what an addon adds to what a player sees, such as widgets in
 * the vehicle HUD ({@link #hudWidgets()}).
 *
 * <pre>{@code
 * // in the addon's client set-up (NeoForge: FMLClientSetupEvent; Fabric: the client entrypoint)
 * McheliClientApi.get().hudWidgets().register(
 *         ResourceLocation.fromNamespaceAndPath("mymod", "clock"), new ClockWidget());
 * }</pre>
 *
 * <p><b>Side and thread.</b> Physical client only: call it from client set-up code, never from code a
 * dedicated server runs (there it does nothing useful). {@link #get()} works from any thread once MCHeli is
 * loaded; each method says on which thread it may be called.
 *
 * <p><b>Stability.</b> Since API 1.1. MCHeli implements this interface and addons only call it, so a minor
 * version may add methods to it.
 */
public interface McheliClientApi {

    /**
     * The running MCHeli's client API. It is found through {@link java.util.ServiceLoader} the first time this
     * is called; every call returns the same instance.
     *
     * @throws IllegalStateException when no implementation is on the class path, which means MCHeli itself is
     *                               not installed
     */
    static McheliClientApi get() {
        return ClientApiHolder.INSTANCE;
    }

    /** Where addons add widgets to the vehicle HUD. Never null; the same instance on every call. */
    HudWidgets hudWidgets();
}
