package com.ivomens.mcheli.networking;

import com.ivomens.mcheli.platform.NetworkHelper.PayloadSpec;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every message type of the mod, with its handler. Each feature declares its messages in one place
 * (a {@code *Messages} class next to the records) and registers them here together with the server
 * handlers; the client registers its handlers for the clientbound types from client init. The
 * finished registry is handed to the loader through {@link Network#register}.
 */
public final class MessageRegistry {

    private final Map<ResourceLocation, Entry<?>> entries = new LinkedHashMap<>();

    /** Declares a message the server receives, and how the server handles it. */
    public <M extends ServerboundMessage> void serverbound(MessageType<M> type, ServerMessageHandler<M> handler) {
        requireDirection(type, MessageType.Direction.SERVERBOUND);
        add(new Entry<>(type, handler));
    }

    /** Declares a message the client receives. Its handler is added by {@link #onClient} on the client only. */
    public <M extends ClientboundMessage> void clientbound(MessageType<M> type) {
        requireDirection(type, MessageType.Direction.CLIENTBOUND);
        add(new Entry<>(type, null));
    }

    /** Sets the client handler of a clientbound type declared earlier. Called from client init only. */
    public <M extends ClientboundMessage> void onClient(MessageType<M> type, ClientMessageHandler<M> handler) {
        Entry<?> entry = entries.get(type.id());
        if (entry == null || entry.type() != type) {
            throw new IllegalStateException("Client handler for undeclared message " + type.id());
        }
        if (entry.clientHandler != null) {
            throw new IllegalStateException("Two client handlers for " + type.id());
        }
        @SuppressWarnings("unchecked")
        Entry<M> typed = (Entry<M>) entry;
        typed.clientHandler = handler;
    }

    /** All types, in registration order. */
    public List<MessageType<?>> types() {
        return entries.values().stream().<MessageType<?>>map(Entry::type).toList();
    }

    /** The clientbound types that have no client handler yet. */
    public List<MessageType<?>> clientboundWithoutHandler() {
        return entries.values().stream()
                .filter(entry -> entry.type().direction() == MessageType.Direction.CLIENTBOUND)
                .filter(entry -> entry.clientHandler == null)
                .<MessageType<?>>map(Entry::type)
                .toList();
    }

    List<PayloadSpec<?>> payloads() {
        List<PayloadSpec<?>> payloads = new ArrayList<>(entries.size());
        entries.values().forEach(entry -> payloads.add(entry.payload()));
        return payloads;
    }

    private void add(Entry<?> entry) {
        if (entries.putIfAbsent(entry.type().id(), entry) != null) {
            throw new IllegalStateException("Message id registered twice: " + entry.type().id());
        }
    }

    private static void requireDirection(MessageType<?> type, MessageType.Direction direction) {
        if (type.direction() != direction) {
            throw new IllegalArgumentException(type.id() + " is " + type.direction() + ", not " + direction);
        }
    }

    /** A type with its server handler, or its client handler once the client added it. */
    private static final class Entry<M extends Message> {

        private final MessageType<M> type;
        @Nullable
        private final ServerMessageHandler<?> serverHandler;
        @Nullable
        private ClientMessageHandler<?> clientHandler;

        Entry(MessageType<M> type, @Nullable ServerMessageHandler<?> serverHandler) {
            this.type = type;
            this.serverHandler = serverHandler;
        }

        MessageType<M> type() {
            return type;
        }

        @SuppressWarnings("unchecked")
        PayloadSpec<M> payload() {
            if (type.direction() == MessageType.Direction.SERVERBOUND) {
                var handler = (ServerMessageHandler<ServerboundMessage>) serverHandler;
                return new PayloadSpec<>(type.payloadType(), type.codec(),
                        (message, sender) -> handler.handle((ServerboundMessage) message, sender), null);
            }
            // Looked up on arrival: the dedicated server never sets client handlers and never receives these.
            return new PayloadSpec<>(type.payloadType(), type.codec(), null, message -> {
                var handler = (ClientMessageHandler<ClientboundMessage>) clientHandler;
                if (handler != null) {
                    handler.handle((ClientboundMessage) message);
                }
            });
        }
    }
}
