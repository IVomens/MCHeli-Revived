package com.ivomens.mcheli.core.content.schema;

/** What a legacy TXT key does when the reader meets it. */
public sealed interface TxtKey {

    /** Sets one field, spelled by the field's TXT codec. */
    record SetsField(Field<?, ?> field) implements TxtKey {
    }

    /**
     * Runs a legacy rule: a key that sets several values, adds to a list, or changes another value as a
     * side effect, in line order.
     */
    record AppliesRule(TxtRule rule) implements TxtKey {
    }

    /** A key the game no longer reads; reported once per load and skipped. */
    record Unsupported() implements TxtKey {
    }

    /** A legacy TXT line that cannot be described as one field. */
    @FunctionalInterface
    interface TxtRule {
        void apply(Values values, String text, ReadContext context);
    }
}
