package com.ivomens.mcheli.core.content.schema;

/**
 * An immutable content definition: a weapon, a throwable, a vehicle or a HUD program.
 * Definitions hold what the file says, in the units the game uses (ticks, not seconds). Values the game
 * derives from other values at run time are not part of them.
 */
public interface ContentDefinition {

    /** The file name without its extension; some defaults are derived from it. */
    String name();

    /** Who made the file, or an empty string. */
    default String author() {
        return "";
    }
}
