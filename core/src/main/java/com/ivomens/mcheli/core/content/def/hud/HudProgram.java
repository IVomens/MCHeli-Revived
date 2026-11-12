package com.ivomens.mcheli.core.content.def.hud;

import com.ivomens.mcheli.core.content.schema.ContentDefinition;

import java.util.List;

/**
 * A HUD script as its file writes it, in either format: a list of commands, where an {@code If} holds the
 * commands it guards. Formulas keep their source text; they are normalised and compiled only when the HUD
 * is built for drawing, so the emitter writes them back unchanged.
 *
 * @param name            the file name without extension, which {@code Call} and seats refer to
 * @param ignoreAutoScale draw at the design size even when the player auto-scales the HUD
 * @param ops             the commands, in file order
 * @param formatVersion   {@link #FORMAT_GATES} for TXT and for YAML without a {@code SchemaVersion}, whose blocks
 *                        do not nest; {@link #FORMAT_NESTED} for YAML with {@code SchemaVersion: 2}
 */
public record HudProgram(String name, boolean ignoreAutoScale, List<HudOp> ops, int formatVersion)
        implements ContentDefinition {

    /** Blocks are gates: they cannot nest and have no {@code Else}. Every HUD file before format 2. */
    public static final int FORMAT_GATES = 1;
    /** Blocks nest and may have an {@code Else}. */
    public static final int FORMAT_NESTED = 2;

    public HudProgram {
        ops = List.copyOf(ops);
        if (formatVersion != FORMAT_GATES && formatVersion != FORMAT_NESTED) {
            throw new IllegalArgumentException("HUD format " + formatVersion + " does not exist");
        }
    }

    /** A program in the first format. */
    public HudProgram(String name, boolean ignoreAutoScale, List<HudOp> ops) {
        this(name, ignoreAutoScale, ops, FORMAT_GATES);
    }

    /** A HUD that draws nothing, for seats without one. */
    public static HudProgram empty(String name) {
        return new HudProgram(name, false, List.of());
    }

    public boolean nestsBlocks() {
        return this.formatVersion == FORMAT_NESTED;
    }
}
