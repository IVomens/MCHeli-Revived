package com.ivomens.mcheli.api.event;

/**
 * An event of the {@link EventBus}. Every event type is an interface that extends this one and that MCHeli
 * implements; addons only read events (and cancel the {@link Cancellable} ones), never create them.
 *
 * <p>An event object is only valid while its listeners run: do not keep it. Stable since API 1.0.
 */
public interface McheliEvent {
}
