package com.ivomens.mcheli.core.content.def.hud;

import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.TxtTuple;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * One HUD command: its YAML key, the schema of its arguments (the keys of its YAML map, with their codecs
 * and defaults) and the TXT keys that spell it, each with the order of its values. Both readers and the
 * emitter look commands up in {@link HudCommands}.
 *
 * @param key       the YAML key the emitter writes, such as {@code DrawRectangle}
 * @param aliases   other YAML spellings of the key
 * @param type      the command's record
 * @param arguments the keys of the command's YAML map; a command without keys ignores its YAML value
 * @param txt       the TXT spellings
 * @param <O>       the command's record
 */
public record HudCommand<O extends HudOp>(String key, List<String> aliases, Class<O> type,
                                          EntrySchema<O> arguments, List<TxtForm<O>> txt) {

    /**
     * One TXT key of a command and the meaning of its comma separated values. A line whose values do not
     * fit is ignored, as the legacy parser did.
     *
     * @param key    the lower-case TXT key
     * @param values the meaning of each value
     */
    public record TxtForm<O extends HudOp>(String key, TxtTuple<O> values) {
    }

    public HudCommand {
        aliases = List.copyOf(aliases);
        txt = List.copyOf(txt);
    }

    /** A command spelled {@code key} in YAML, with no TXT spelling yet. */
    static <O extends HudOp> HudCommand<O> of(String key, Class<O> type, EntrySchema<O> arguments) {
        return new HudCommand<>(key, List.of(), type, arguments, List.of());
    }

    /** The same command with other YAML spellings of its key. */
    HudCommand<O> alias(String... names) {
        return new HudCommand<>(this.key, List.of(names), this.type, this.arguments, this.txt);
    }

    /** The same command with a TXT key, whose values {@code layout} lays out over the argument keys. */
    HudCommand<O> txt(String txtKey, UnaryOperator<TxtTuple<O>> layout) {
        List<TxtForm<O>> forms = new ArrayList<>(this.txt);
        forms.add(new TxtForm<>(txtKey, layout.apply(TxtTuple.of(this.arguments))));
        return new HudCommand<>(this.key, this.aliases, this.type, this.arguments, forms);
    }

    /** Whether the command takes no arguments, such as {@code Exit}. */
    public boolean takesNoArguments() {
        return this.arguments.root().members().isEmpty();
    }
}
