package com.ivomens.mcheli.core.content.schema;

import java.util.Optional;
import java.util.function.Function;

/**
 * A content file naming another, such as a vehicle's weapon or a seat's HUD. Reading only keeps the name;
 * the link step resolves it once the files it can name are loaded, and reports a name nothing answers to.
 *
 * @param name the other file's name without extension
 * @param <D>  what the name refers to
 */
public record Ref<D>(String name) {

    /** A name in lower case, compared without regard to case as file names are. */
    public static <D> ValueCodec<Ref<D>> lowerCase() {
        return Codecs.LOWER_TEXT.map(Ref::new, Ref::name);
    }

    /** The named content, if {@code lookup} knows it. */
    public <T extends D> Optional<T> resolve(Function<String, T> lookup) {
        return Optional.ofNullable(lookup.apply(this.name));
    }
}
