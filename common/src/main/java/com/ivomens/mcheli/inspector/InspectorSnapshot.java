package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.networking.Codecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Optional;

/**
 * Everything the vehicle inspector shows of one vehicle at one moment, as seen from one side. The client
 * builds its own from the vehicle it has; an operator's client also asks the server for the server's, and
 * the two are compared section by section and key by key.
 * <p>
 * A snapshot that goes over the network is never larger than {@link #MAX_BYTES}: {@link SnapshotBuilder}
 * stops adding entries at that size and sets {@link #truncated}.
 *
 * @param entityId  the vehicle's network id
 * @param typeId    the vehicle type, {@code <kind>/<name>}
 * @param gameTime  the level's game time when it was taken
 * @param truncated whether entries were left out to stay within {@link #MAX_BYTES}
 */
public record InspectorSnapshot(int entityId, String typeId, long gameTime, boolean truncated,
                                List<InspectorSection> sections) {

    /** The largest encoded size of a snapshot the builder makes. */
    public static final int MAX_BYTES = 48 * 1024;
    public static final int MAX_TYPE_ID = 128;
    public static final int MAX_SECTIONS = 160;

    public static final StreamCodec<ByteBuf, InspectorSnapshot> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, InspectorSnapshot::entityId,
            Codecs.utf(MAX_TYPE_ID), InspectorSnapshot::typeId,
            ByteBufCodecs.VAR_LONG, InspectorSnapshot::gameTime,
            ByteBufCodecs.BOOL, InspectorSnapshot::truncated,
            Codecs.list(InspectorSection.CODEC, MAX_SECTIONS), InspectorSnapshot::sections,
            InspectorSnapshot::new);

    public InspectorSnapshot {
        sections = List.copyOf(sections);
    }

    /** A snapshot with nothing in it, for replies that carry none. */
    public static InspectorSnapshot empty(int entityId) {
        return new InspectorSnapshot(entityId, "", 0L, false, List.of());
    }

    /** The sections of one tab, in order. */
    public List<InspectorSection> sections(InspectorTab tab) {
        return this.sections.stream().filter(section -> section.tab() == tab).toList();
    }

    /** The section of a tab with this title, if there is one. */
    public Optional<InspectorSection> section(InspectorTab tab, String title) {
        return this.sections.stream().filter(section -> section.tab() == tab && section.title().equals(title))
                .findFirst();
    }

    /** The value of an entry, if the section and the entry exist. */
    public Optional<String> value(InspectorTab tab, String title, String key) {
        return section(tab, title).flatMap(section -> section.entry(key)).map(InspectorEntry::value);
    }
}
