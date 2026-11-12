package com.ivomens.mcheli.gametest.bench;

import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.ServiceLoader;

/**
 * Makes a mock player's connection look as if its client had agreed on MCHeli's payload channels, so
 * the loader sends it the mod's messages instead of skipping them as it does for players without the
 * mod. Each loader's game test layer provides one, listed in its {@code META-INF/services}.
 */
public interface ModChannels {

    static ModChannels get() {
        return ServiceLoader.load(ModChannels.class, ModChannels.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No ModChannels for this loader"));
    }

    void open(ServerPlayer player, Connection connection, Collection<ResourceLocation> payloadIds);
}
