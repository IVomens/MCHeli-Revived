package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.config.LiveryPolicy;
import com.ivomens.mcheli.livery.Livery;
import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Builds and sends the livery list a player sees, after a request or after one of their actions. */
final class LiveryListReply {

    private LiveryListReply() {
    }

    /** Sends the list for {@code vehicleType} with an optional feedback line ({@code message} may be empty). */
    static void send(LiveryManager manager, ServerPlayer player, String vehicleType, String message, boolean isError) {
        Network.sendToPlayer(player, of(manager, player, vehicleType, message, isError));
    }

    static LiveryListMessage of(LiveryManager manager, ServerPlayer player, String vehicleType, String message,
                                boolean isError) {
        LiveryPolicy policy = manager.policy();
        LiveryListMessage.Permissions permissions = new LiveryListMessage.Permissions(policy.enabled(),
                manager.canUpload(player), LiveryManager.isOperator(player));
        LiveryListMessage.Limits limits = new LiveryListMessage.Limits(policy.maxBytes(), policy.maxDimension(),
                policy.perPlayer(), manager.store().countOwnedBy(player.getUUID()));
        List<LiveryListMessage.Entry> entries = manager.listFor(player, vehicleType).stream()
                .limit(LiveryListMessage.MAX_ENTRIES)
                .map(livery -> entry(livery, player))
                .toList();
        return new LiveryListMessage(vehicleType, permissions, limits, message, isError, entries);
    }

    private static LiveryListMessage.Entry entry(Livery livery, ServerPlayer player) {
        return new LiveryListMessage.Entry(livery.id(), livery.vehicleType(), livery.name(), livery.ownerName(),
                livery.owner().equals(player.getUUID()),
                new LiveryListMessage.Image(livery.width(), livery.height(), livery.bytes()));
    }
}
