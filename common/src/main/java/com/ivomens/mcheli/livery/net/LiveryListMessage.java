package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.livery.Livery;
import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * The liveries the livery screen shows, what the player may do with them, the server's limits, and
 * optionally a line about the action that triggered the refresh.
 *
 * @param message        feedback for the last action; empty for none
 * @param messageIsError whether {@code message} reports a failure
 */
public record LiveryListMessage(String vehicleType, Permissions permissions, Limits limits, String message,
                                boolean messageIsError, List<Entry> entries) implements ClientboundMessage {

    /** Newest entries win when a server has more than this for one vehicle type. */
    public static final int MAX_ENTRIES = 512;
    private static final int MAX_MESSAGE_LENGTH = 256;

    private static final StreamCodec<ByteBuf, LiveryListMessage> CODEC = StreamCodec.composite(
            Codecs.utf(LiveryManager.MAX_TEXT_LENGTH), LiveryListMessage::vehicleType,
            Permissions.CODEC, LiveryListMessage::permissions,
            Limits.CODEC, LiveryListMessage::limits,
            Codecs.utf(MAX_MESSAGE_LENGTH), LiveryListMessage::message,
            ByteBufCodecs.BOOL, LiveryListMessage::messageIsError,
            Codecs.list(Entry.CODEC, MAX_ENTRIES), LiveryListMessage::entries,
            LiveryListMessage::new);

    public static final MessageType<LiveryListMessage> TYPE = MessageType.clientbound("livery/list", CODEC);

    public LiveryListMessage {
        entries = List.copyOf(entries);
    }

    @Override
    public MessageType<LiveryListMessage> messageType() {
        return TYPE;
    }

    /** Whether liveries are on, and what the receiving player may do. */
    public record Permissions(boolean enabled, boolean canUpload, boolean operator) {

        static final StreamCodec<ByteBuf, Permissions> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Permissions::enabled,
                ByteBufCodecs.BOOL, Permissions::canUpload,
                ByteBufCodecs.BOOL, Permissions::operator,
                Permissions::new);
    }

    /** The server's upload limits and how many liveries the player already has. */
    public record Limits(int maxBytes, int maxDimension, int perPlayer, int owned) {

        static final StreamCodec<ByteBuf, Limits> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Limits::maxBytes,
                ByteBufCodecs.VAR_INT, Limits::maxDimension,
                ByteBufCodecs.VAR_INT, Limits::perPlayer,
                ByteBufCodecs.VAR_INT, Limits::owned,
                Limits::new);
    }

    /** One livery as the screen shows it. {@code own} is true for the receiving player's uploads. */
    public record Entry(String id, String vehicleType, String name, String ownerName, boolean own, Image image) {

        static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                Codecs.utf(Livery.ID_LENGTH), Entry::id,
                Codecs.utf(LiveryManager.MAX_TEXT_LENGTH), Entry::vehicleType,
                Codecs.utf(LiveryManager.MAX_TEXT_LENGTH), Entry::name,
                Codecs.utf(LiveryManager.MAX_TEXT_LENGTH), Entry::ownerName,
                ByteBufCodecs.BOOL, Entry::own,
                Image.CODEC, Entry::image,
                Entry::new);
    }

    /** Size of the livery's PNG, in pixels and on disk. */
    public record Image(int width, int height, int bytes) {

        static final StreamCodec<ByteBuf, Image> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Image::width,
                ByteBufCodecs.VAR_INT, Image::height,
                ByteBufCodecs.VAR_INT, Image::bytes,
                Image::new);
    }
}
