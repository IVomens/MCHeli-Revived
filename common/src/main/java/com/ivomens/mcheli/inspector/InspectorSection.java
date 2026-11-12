package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.networking.Codecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Optional;

/**
 * A titled group of entries in one tab: a component, a weapon, a seat, a part of the overview.
 *
 * @param detail a second line under the title (a component's phase and step), or empty
 */
public record InspectorSection(InspectorTab tab, String title, String detail, List<InspectorEntry> entries) {

    public static final int MAX_TITLE = 64;
    public static final int MAX_DETAIL = 96;
    public static final int MAX_ENTRIES = 128;

    public static final StreamCodec<ByteBuf, InspectorSection> CODEC = StreamCodec.composite(
            Codecs.enumOf(InspectorTab.class), InspectorSection::tab,
            Codecs.utf(MAX_TITLE), InspectorSection::title,
            Codecs.utf(MAX_DETAIL), InspectorSection::detail,
            Codecs.list(InspectorEntry.CODEC, MAX_ENTRIES), InspectorSection::entries,
            InspectorSection::new);

    public InspectorSection {
        entries = List.copyOf(entries);
    }

    /** The entry with this key, if the section has one. */
    public Optional<InspectorEntry> entry(String key) {
        return this.entries.stream().filter(entry -> entry.key().equals(key)).findFirst();
    }
}
