package com.ivomens.mcheli.config;

import java.util.List;
import java.util.function.Predicate;

/** One piece of a config file as it is written: fixed text, an option, or pieces written on a condition. */
public sealed interface Layout {

    /** A line written as is: a header, a comment or an empty line. */
    record Text(String line) implements Layout {
    }

    /** An option's comment lines, then its {@code key = value} lines. Only these keys are read back. */
    record Entry(Option<?> option) implements Layout {
    }

    /** Pieces written only when the values pass the condition. */
    record When(Predicate<ConfigValues> condition, List<Layout> then) implements Layout {

        public When {
            then = List.copyOf(then);
        }
    }

    static Text text(String line) {
        return new Text(line);
    }

    static Text blank() {
        return new Text("");
    }

    static Entry entry(Option<?> option) {
        return new Entry(option);
    }
}
