package com.ivomens.mcheli.gametest.neoforge;

import com.ivomens.mcheli.gametest.bench.ModChannels;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.Collection;

/** Adds the channels to the connection's common (c:register) channels, which NeoForge checks before sending. */
public class NeoForgeModChannels implements ModChannels {

    @Override
    public void open(ServerPlayer player, Connection connection, Collection<ResourceLocation> payloadIds) {
        ChannelAttributes.getOrCreateCommonChannels(connection, ConnectionProtocol.PLAY).addAll(payloadIds);
    }
}
