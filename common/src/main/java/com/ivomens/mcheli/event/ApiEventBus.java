package com.ivomens.mcheli.event;

import com.ivomens.mcheli.api.event.EventBus;
import com.ivomens.mcheli.api.event.McheliEvent;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.util.ModLogger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The API's event bus: listeners by exact event type, called in registration order. One instance lives as
 * long as the game, owned by the API's entry point ({@code ApiService}); the hooks reach it through
 * {@link ApiEvents}.
 */
public final class ApiEventBus implements EventBus {

    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <E extends McheliEvent> Subscription register(Class<E> type, Consumer<? super E> listener) {
        if (!type.isInterface() || !McheliEvent.class.isAssignableFrom(type) || type == McheliEvent.class) {
            throw new IllegalArgumentException(type.getName() + " is not an MCHeli event type");
        }
        Consumer<Object> entry = event -> listener.accept((E) event);
        List<Consumer<Object>> ofType = this.listeners.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>());
        ofType.add(entry);
        return () -> ofType.remove(entry);
    }

    /** Whether anyone listens to the type, so a hook can skip building an event nobody reads. */
    public boolean hasListeners(Class<? extends McheliEvent> type) {
        List<Consumer<Object>> ofType = this.listeners.get(type);
        return ofType != null && !ofType.isEmpty();
    }

    /** Hands the event to every listener of its type; one that throws is logged and the rest still run. */
    public <E extends McheliEvent> E post(Class<E> type, E event) {
        List<Consumer<Object>> ofType = this.listeners.get(type);
        if (ofType == null) {
            return event;
        }
        for (Consumer<Object> listener : ofType) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                ModLogger.error("A listener of " + type.getSimpleName() + " failed", e);
            }
        }
        return event;
    }
}
