package com.ivomens.mcheli.content.registry;

import com.ivomens.mcheli.content.pack.AddonResourceLocation;
import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import com.ivomens.mcheli.content.source.ContentFormat;

/**
 * One content file as read: where it came from, the format it was written in, and its definition.
 *
 * @param location the file's name qualified by its addon pack
 * @param filePath the path inside the pack
 * @param format   the format it was read from: its file extension, or {@link ContentFormat#BUILTIN}
 * @param author   who made it; an override from another pack that names nobody keeps the original author
 * @param <D>      the definition type
 */
public record ContentEntry<D extends ContentDefinition>(ContentType type, AddonResourceLocation location,
                                                        String filePath, String format, String author,
                                                        D definition) {

    /** The file name without extension, which other content and saved games refer to. */
    public String name() {
        return this.location.getPath();
    }

    public ContentId id() {
        return new ContentId(this.type, name());
    }

    /**
     * True for definitions read from the original {@code .txt} format. A few values are still derived
     * differently for them, as the old parsers did.
     */
    public boolean isLegacyTxt() {
        return ContentFormat.TXT.equals(this.format);
    }

    public ContentEntry<D> withAuthor(String newAuthor) {
        return new ContentEntry<>(this.type, this.location, this.filePath, this.format, newAuthor, this.definition);
    }
}
