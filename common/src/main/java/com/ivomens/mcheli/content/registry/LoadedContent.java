package com.ivomens.mcheli.content.registry;

import com.ivomens.mcheli.core.content.schema.ContentDefinition;

/**
 * A content file as read, with the run-time value its feature built from it.
 *
 * @param <D> the definition type
 * @param <T> the run-time value
 */
public record LoadedContent<D extends ContentDefinition, T>(ContentEntry<D> entry, T value) {

    public String name() {
        return this.entry.name();
    }

    LoadedContent<D, T> withAuthor(String author) {
        return new LoadedContent<>(this.entry.withAuthor(author), this.value);
    }
}
