package com.ivomens.mcheli.api.event;

import java.util.function.Consumer;

/**
 * MCHeli's own events, the same on every loader: an addon registers plain listeners here instead of on
 * NeoForge's or Fabric's event bus.
 *
 * <pre>{@code
 * EventBus events = McheliApi.get().events();
 * events.register(WeaponFired.class, fired -> {
 *     if (fired.shooter() instanceof Player player && player.isSpectator()) {
 *         fired.setCancelled(true);
 *     }
 * });
 * }</pre>
 *
 * <p>A listener gets the events of exactly the type it registered for, in the order listeners registered,
 * on the thread the event happens on (each event type says which side and thread that is). A listener that
 * throws is logged and the others still run. Listeners stay registered until unsubscribed, for as long as
 * the game runs, across worlds and servers.
 *
 * <p><b>Thread.</b> {@link #register} and {@link Subscription#unsubscribe()} may be called from any thread;
 * an event already being delivered still reaches the listeners it started with.
 *
 * <p><b>Stability.</b> Stable since API 1.0. Implemented by MCHeli only; a minor version may add methods and
 * event types, but never removes an event type or changes when it fires.
 */
public interface EventBus {

    /**
     * Adds a listener for one event type, one of the interfaces in this package that extend
     * {@link McheliEvent}.
     *
     * @return the handle that removes the listener again
     */
    <E extends McheliEvent> Subscription register(Class<E> type, Consumer<? super E> listener);
}
