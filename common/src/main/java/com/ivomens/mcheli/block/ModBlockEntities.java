package com.ivomens.mcheli.block;

import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.registry.McheliBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * Block entity and menu types of the drafting table and the charger. The blocks themselves are
 * created in the block registry event (which runs before these registries).
 */
public final class ModBlockEntities {

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<DraftingTableBlockEntity>> DRAFTING_TABLE =
            Services.REGISTRY.register(Registries.BLOCK_ENTITY_TYPE, "drafting_table",
                    () -> BlockEntityType.Builder.of(DraftingTableBlockEntity::new,
                            McheliBlocks.DRAFTING_TABLE.get(), McheliBlocks.LIT_DRAFTING_TABLE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ChargerBlockEntity>> CHARGER =
            Services.REGISTRY.register(Registries.BLOCK_ENTITY_TYPE, "charger",
                    () -> BlockEntityType.Builder.of(ChargerBlockEntity::new, McheliBlocks.CHARGER.get()).build(null));

    public static final Supplier<MenuType<DraftingTableMenu>> DRAFTING_TABLE_MENU =
            Services.REGISTRY.register(Registries.MENU, "drafting_table", () -> Services.MENUS.createMenuType(
                    (id, inv, buf) -> new DraftingTableMenu(id, inv, buf.readBlockPos())));

    private ModBlockEntities() {}

    /** Loading the class registers the block entity and menu types. */
    public static void init() {
    }
}
