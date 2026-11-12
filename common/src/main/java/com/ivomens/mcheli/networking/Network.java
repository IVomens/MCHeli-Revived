package com.ivomens.mcheli.networking;

import com.ivomens.mcheli.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Registers the mod's messages with the loader and sends them. The messages and their handlers are
 * declared by the features (see {@link MessageRegistry}); this class only moves them.
 */
public final class Network {

    /** Bumped whenever a message is added, removed or changes its encoding. */
    public static final String PROTOCOL = "14";

    private Network() {
    }

    /** Hands every message type to the loader. Call once, while the mod is constructed. */
    public static void register(MessageRegistry messages) {
        Services.NETWORK.registerPayloads(PROTOCOL, messages.payloads());
    }

    public static void sendToServer(ServerboundMessage message) {
        Services.NETWORK.sendToServer(message);
    }

    /**
     * Sends to one player. Players whose connection did not negotiate MCHeli's channel (vanilla
     * clients, test and fake players) are skipped.
     */
    public static void sendToPlayer(ServerPlayer player, ClientboundMessage message) {
        Services.NETWORK.sendToPlayer(player, message);
    }

    /** Does nothing unless the player is a server player. */
    public static void sendToPlayer(Player player, ClientboundMessage message) {
        if (player instanceof ServerPlayer serverPlayer) {
            sendToPlayer(serverPlayer, message);
        }
    }

    /** Every player on the running server; nothing when no server runs in this process. */
    public static void sendToAll(ClientboundMessage message) {
        MinecraftServer server = Services.PLATFORM.getServer();
        if (server != null) {
            server.getPlayerList().getPlayers().forEach(player -> sendToPlayer(player, message));
        }
    }

    /** Every player of the level within {@code radius}. Does nothing on a client level. */
    public static void sendToAllAround(Level level, Vec3 center, double radius, ClientboundMessage message) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double radiusSq = radius * radius;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(center) <= radiusSq) {
                sendToPlayer(player, message);
            }
        }
    }

    /** Players that currently track the entity (have it loaded), excluding the entity itself. */
    public static void sendToTracking(Entity entity, ClientboundMessage message) {
        for (ServerPlayer player : Services.NETWORK.getPlayersTracking(entity)) {
            if (player != entity) {
                sendToPlayer(player, message);
            }
        }
    }
}
