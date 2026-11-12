package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.vehicle.component.StateSink;
import io.netty.buffer.ByteBufUtil;
import net.minecraft.network.VarInt;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a snapshot section by section within a byte budget: once the next section or entry would make the
 * encoded snapshot larger than the budget, or a section or entry count reaches its codec's bound, it is left
 * out and the snapshot is marked truncated. Every text is cut to its codec's length first, so whatever the
 * builder makes always encodes.
 */
public final class SnapshotBuilder {

    /** The largest encoding of a varint, a varlong, and a list length within our bounds. */
    private static final int VAR_INT = 5;
    private static final int VAR_LONG = 10;
    private static final int LIST_LENGTH = 2;

    private final int entityId;
    private final String typeId;
    private final long gameTime;
    private final int budget;
    private final List<InspectorSection> sections = new ArrayList<>();
    private int used;
    private boolean truncated;
    private SectionWriter open;

    public SnapshotBuilder(int entityId, String typeId, long gameTime) {
        this(entityId, typeId, gameTime, InspectorSnapshot.MAX_BYTES);
    }

    /** A builder with its own budget in bytes, at most {@link InspectorSnapshot#MAX_BYTES}. */
    public SnapshotBuilder(int entityId, String typeId, long gameTime, int budget) {
        this.entityId = entityId;
        this.typeId = InspectorEntry.clip(typeId, InspectorSnapshot.MAX_TYPE_ID);
        this.gameTime = gameTime;
        this.budget = Math.min(budget, InspectorSnapshot.MAX_BYTES);
        this.used = VAR_INT + utfSize(this.typeId) + VAR_LONG + 1 + LIST_LENGTH;
    }

    /** Starts the next section; the one before it is finished. */
    public SectionWriter section(InspectorTab tab, String title, String detail) {
        closeOpen();
        String clippedTitle = InspectorEntry.clip(title, InspectorSection.MAX_TITLE);
        String clippedDetail = InspectorEntry.clip(detail, InspectorSection.MAX_DETAIL);
        int header = 1 + utfSize(clippedTitle) + utfSize(clippedDetail) + LIST_LENGTH;
        boolean fits = this.sections.size() < InspectorSnapshot.MAX_SECTIONS && this.used + header <= this.budget;
        if (fits) {
            this.used += header;
        } else {
            this.truncated = true;
        }
        this.open = new SectionWriter(tab, clippedTitle, clippedDetail, fits);
        return this.open;
    }

    public InspectorSnapshot build() {
        closeOpen();
        return new InspectorSnapshot(this.entityId, this.typeId, this.gameTime, this.truncated, this.sections);
    }

    /** The encoded size so far, never more than the budget. */
    public int size() {
        return this.used;
    }

    private void closeOpen() {
        if (this.open != null && this.open.kept) {
            this.sections.add(new InspectorSection(this.open.tab, this.open.title, this.open.detail, this.open.entries));
        }
        this.open = null;
    }

    /** The encoded size of a string: its UTF-8 length as a varint, then the bytes. */
    static int utfSize(String text) {
        int bytes = ByteBufUtil.utf8Bytes(text);
        return VarInt.getByteSize(bytes) + bytes;
    }

    /** Writes the entries of one section; entries past the budget are dropped. */
    public final class SectionWriter implements StateSink {

        private final InspectorTab tab;
        private final String title;
        private final String detail;
        private final boolean kept;
        private final List<InspectorEntry> entries = new ArrayList<>();

        private SectionWriter(InspectorTab tab, String title, String detail, boolean kept) {
            this.tab = tab;
            this.title = title;
            this.detail = detail;
            this.kept = kept;
        }

        @Override
        public void put(String key, String value) {
            entry(key, value, "", false);
        }

        /** Adds an entry with a detail (a file position, a default) and whether it stands out. */
        public void entry(String key, String value, String detail, boolean marked) {
            InspectorEntry entry = InspectorEntry.of(key, value, detail, marked);
            int size = utfSize(entry.key()) + utfSize(entry.value()) + utfSize(entry.detail()) + 1;
            SnapshotBuilder builder = SnapshotBuilder.this;
            if (!this.kept || this.entries.size() >= InspectorSection.MAX_ENTRIES || builder.used + size > builder.budget) {
                builder.truncated = true;
                return;
            }
            builder.used += size;
            this.entries.add(entry);
        }
    }
}
