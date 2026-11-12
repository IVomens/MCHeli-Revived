package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Arrays;
import java.util.List;

/** Entities spotted by a teammate, marked on the HUD for {@code markTime} ticks. */
public record SpotEnemyMessage(int markTime, List<Integer> entityIds) implements ClientboundMessage {

    private static final int MAX_MARK_TIME = 30000;
    private static final int MAX_ENTITIES = 300;

    private static final StreamCodec<ByteBuf, SpotEnemyMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpotEnemyMessage::markTime,
            Codecs.list(ByteBufCodecs.VAR_INT, MAX_ENTITIES), SpotEnemyMessage::entityIds,
            SpotEnemyMessage::new);

    public static final MessageType<SpotEnemyMessage> TYPE = MessageType.clientbound("multiplay/spot_enemy", CODEC);

    public SpotEnemyMessage {
        entityIds = List.copyOf(entityIds);
    }

    /** The message for these entities, with the mark time and the entity count capped. */
    public static SpotEnemyMessage of(int markTime, int[] entityIds) {
        List<Integer> ids = Arrays.stream(entityIds).limit(MAX_ENTITIES).boxed().toList();
        return new SpotEnemyMessage(Math.min(markTime, MAX_MARK_TIME), ids);
    }

    @Override
    public MessageType<SpotEnemyMessage> messageType() {
        return TYPE;
    }
}
