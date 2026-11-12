package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.networking.Codecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One line of a snapshot section.
 *
 * @param key    what the value is
 * @param value  the value as text
 * @param detail where it comes from or what it is compared with (a file position, the default), or empty
 * @param marked whether it stands out: a definition value that differs from the schema's default
 */
public record InspectorEntry(String key, String value, String detail, boolean marked) {

    public static final int MAX_KEY = 64;
    public static final int MAX_VALUE = 128;
    public static final int MAX_DETAIL = 96;

    public static final StreamCodec<ByteBuf, InspectorEntry> CODEC = StreamCodec.composite(
            Codecs.utf(MAX_KEY), InspectorEntry::key,
            Codecs.utf(MAX_VALUE), InspectorEntry::value,
            Codecs.utf(MAX_DETAIL), InspectorEntry::detail,
            ByteBufCodecs.BOOL, InspectorEntry::marked,
            InspectorEntry::new);

    /** An entry with every text cut to the length the codec accepts. */
    public static InspectorEntry of(String key, String value, String detail, boolean marked) {
        return new InspectorEntry(clip(key, MAX_KEY), clip(value, MAX_VALUE), clip(detail, MAX_DETAIL), marked);
    }

    /** {@code text} cut to {@code max} characters, ending in "..." when it was longer; null becomes empty. */
    public static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
