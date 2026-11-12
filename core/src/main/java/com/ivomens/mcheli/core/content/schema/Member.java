package com.ivomens.mcheli.core.content.schema;

import java.util.List;

/**
 * One key of a YAML section: a setting, a nested section, or a second name for a setting declared
 * elsewhere in the file.
 *
 * @param <S> the definition record of the section the key belongs to
 */
public sealed interface Member<S> permits Field, Section, KeyAlias {

    /** The YAML key the emitter writes. */
    String key();

    /** Older or shorter spellings the YAML reader also accepts. */
    List<String> aliases();
}
