package com.ivomens.mcheli.networking.client;

import com.ivomens.mcheli.networking.ClientMessageHandler;
import com.ivomens.mcheli.networking.EntitySpawnDataMessage;
import com.ivomens.mcheli.networking.SpawnDataEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;

/** Hands the extra spawn data to the client-side entity it belongs to. */
public final class EntitySpawnDataHandler implements ClientMessageHandler<EntitySpawnDataMessage> {

    @Override
    public void handle(EntitySpawnDataMessage message) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level != null && message.entityId() >= 0 ? level.getEntity(message.entityId()) : null;
        if (entity instanceof SpawnDataEntity spawnData) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(message.data()),
                    entity.registryAccess());
            spawnData.readSpawnData(buffer);
        }
    }
}
