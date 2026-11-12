package com.ivomens.mcheli.networking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Handles one serverbound message type on the server thread. */
@FunctionalInterface
public interface ServerMessageHandler<M extends ServerboundMessage> {

    void handle(M message, ServerPlayer sender);

    /** The entity with this network id in the sender's level; null for unknown or negative ids. */
    @Nullable
    static Entity entityNear(ServerPlayer sender, int entityId) {
        return entityId >= 0 ? sender.level().getEntity(entityId) : null;
    }
}
