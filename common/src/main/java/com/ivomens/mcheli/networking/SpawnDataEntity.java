package com.ivomens.mcheli.networking;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * An entity that sends extra data to a client when the client starts tracking it. Call
 * {@link #sendSpawnData} from {@link Entity#startSeenByPlayer}: vanilla calls it right after sending
 * the spawn packets on the same connection, so the client already has the entity when the data
 * arrives. Until then the client-side entity has only what the vanilla spawn packet and synced data
 * carry.
 */
public interface SpawnDataEntity {

    void writeSpawnData(RegistryFriendlyByteBuf buffer);

    void readSpawnData(RegistryFriendlyByteBuf buffer);

    static <T extends Entity & SpawnDataEntity> void sendSpawnData(T entity, ServerPlayer player) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), entity.registryAccess());
        try {
            entity.writeSpawnData(buffer);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            Network.sendToPlayer(player, new EntitySpawnDataMessage(entity.getId(), data));
        } finally {
            buffer.release();
        }
    }
}
