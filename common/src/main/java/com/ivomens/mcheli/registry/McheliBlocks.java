package com.ivomens.mcheli.registry;

import net.minecraft.world.level.block.Block;

/** MCHeli's blocks. {@code McheliMod} registers them. */
public final class McheliBlocks {

    public static final RegistryRef<Block> DRAFTING_TABLE = RegistryRef.block("drafting_table");
    public static final RegistryRef<Block> LIT_DRAFTING_TABLE = RegistryRef.block("lit_drafting_table");
    public static final RegistryRef<Block> CHARGER = RegistryRef.block("charger");

    private McheliBlocks() {
    }
}
