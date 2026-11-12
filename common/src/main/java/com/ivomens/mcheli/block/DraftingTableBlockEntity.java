package com.ivomens.mcheli.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Drafting table block entity: carries no data, it exists for the table's block entity renderer. */
public class DraftingTableBlockEntity extends BlockEntity {

    public DraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRAFTING_TABLE.get(), pos, state);
    }
}
