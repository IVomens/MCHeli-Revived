package com.ivomens.mcheli.inspector.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.inspector.DefinitionSections;
import com.ivomens.mcheli.inspector.InspectorSnapshot;
import com.ivomens.mcheli.inspector.InspectorTargets;
import com.ivomens.mcheli.inspector.VehicleSnapshots;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * Answers an operator's request for the server's snapshot of a vehicle. Players below operator level get
 * {@link InspectorReplyMessage.Status#DENIED} and nothing else. A player gets at most one answer per
 * {@link #MIN_INTERVAL_TICKS} (four a second); requests in between are dropped, since the inspector asks
 * again anyway.
 */
public final class InspectorRequestHandler implements ServerMessageHandler<InspectorRequestMessage> {

    /** The permission level of {@code /mcheli inspect}: an operator. */
    public static final int OPERATOR = 2;
    /** Ticks between two answers to the same player. */
    public static final int MIN_INTERVAL_TICKS = 5;

    private final BiConsumer<ServerPlayer, InspectorReplyMessage> reply;
    /** The server tick of each player's last answer; players drop out when they log off. */
    private final Map<ServerPlayer, Integer> lastAnswer = new WeakHashMap<>();

    /** @param reply sends an answer to the player who asked */
    public InspectorRequestHandler(BiConsumer<ServerPlayer, InspectorReplyMessage> reply) {
        this.reply = reply;
    }

    @Override
    public void handle(InspectorRequestMessage message, ServerPlayer sender) {
        InspectorReplyMessage answer = answer(sender, message.entityId());
        if (answer != null) {
            this.reply.accept(sender, answer);
        }
    }

    /** The answer to a request from {@code sender}, or null when it comes too soon after the last one. */
    @Nullable
    public InspectorReplyMessage answer(ServerPlayer sender, int entityId) {
        if (!sender.hasPermissions(OPERATOR)) {
            return new InspectorReplyMessage(InspectorReplyMessage.Status.DENIED, InspectorSnapshot.empty(entityId));
        }
        int now = sender.server.getTickCount();
        Integer last = this.lastAnswer.get(sender);
        if (last != null && now - last < MIN_INTERVAL_TICKS && now >= last) {
            return null;
        }
        this.lastAnswer.put(sender, now);
        AircraftEntity vehicle = InspectorTargets.vehicleOf(ServerMessageHandler.entityNear(sender, entityId));
        if (vehicle == null) {
            return new InspectorReplyMessage(InspectorReplyMessage.Status.NOT_FOUND, InspectorSnapshot.empty(entityId));
        }
        return new InspectorReplyMessage(InspectorReplyMessage.Status.OK,
                VehicleSnapshots.capture(vehicle, DefinitionSections.Detail.CHANGED_ONLY));
    }
}
