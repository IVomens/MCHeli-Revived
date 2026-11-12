package com.ivomens.mcheli.gametest.fabric;

import com.ivomens.mcheli.gametest.bench.ModChannels;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;

/**
 * Adds the channels to the set Fabric's play network addon checks in {@code ServerPlayNetworking.canSend}.
 * Fabric has no API for it (a real client announces its channels), so the set is reached by reflection.
 */
public class FabricModChannels implements ModChannels {

    @Override
    @SuppressWarnings("unchecked")
    public void open(ServerPlayer player, Connection connection, Collection<ResourceLocation> payloadIds) {
        Object addon = ServerNetworkingImpl.getAddon(player.connection);
        try {
            Field field = findField(addon.getClass(), "sendableChannels");
            field.setAccessible(true);
            ((Set<ResourceLocation>) field.get(addon)).addAll(payloadIds);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot open MCHeli's channels on a mock connection", e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // look further up
            }
        }
        throw new NoSuchFieldException(name);
    }
}
