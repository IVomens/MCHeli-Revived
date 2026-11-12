package com.ivomens.mcheli.vehicledata;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Addon values of one vehicle that changed, or all of them for a player who just started seeing it. Each
 * value travels as the bytes its key's sync codec wrote, so a client that lacks the addon skips it.
 */
public record VehicleDataMessage(int entityId, List<Entry> entries) implements ClientboundMessage {

    /** The most values one message carries; more go in further messages. */
    public static final int MAX_ENTRIES = 32;
    /** The most bytes one value may take; a larger one is not synced (and logged). */
    public static final int MAX_VALUE_BYTES = 16384;

    /** One value: its key's id, and its encoded value or that the vehicle no longer has one. */
    public record Entry(ResourceLocation id, boolean removed, byte[] value) {

        static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::id,
                ByteBufCodecs.BOOL, Entry::removed,
                Codecs.bytes(MAX_VALUE_BYTES), Entry::value,
                Entry::new);
    }

    private static final StreamCodec<ByteBuf, VehicleDataMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VehicleDataMessage::entityId,
            Codecs.list(Entry.CODEC, MAX_ENTRIES), VehicleDataMessage::entries,
            VehicleDataMessage::new);

    public static final MessageType<VehicleDataMessage> TYPE = MessageType.clientbound("vehicledata/values", CODEC);

    @Override
    public MessageType<VehicleDataMessage> messageType() {
        return TYPE;
    }
}
