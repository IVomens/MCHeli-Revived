package com.ivomens.mcheli.networking;

/**
 * Handles one clientbound message type on the client thread. Implementations live in a
 * {@code client} package and are registered from client init only, so a dedicated server never
 * loads them.
 */
@FunctionalInterface
public interface ClientMessageHandler<M extends ClientboundMessage> {

    void handle(M message);
}
