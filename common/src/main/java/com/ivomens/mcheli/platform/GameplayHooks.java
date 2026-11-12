package com.ivomens.mcheli.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Loader events and per-block or per-item extension points that our own copies of vanilla logic fire.
 * Each method says what plain vanilla does instead.
 */
public interface GameplayHooks {

    /** Lets other mods edit the entity list of an explosion before it deals damage. Vanilla: nothing. */
    void onExplosionDetonate(Level level, Explosion explosion, List<Entity> entities, double diameter);

    /**
     * The block a fluid at {@code liquidPos} turns {@code pos} into (lava doused with water, for
     * example), after other mods have had a say. Vanilla: {@code state}.
     */
    BlockState fluidPlaceBlock(LevelAccessor level, BlockPos pos, BlockPos liquidPos, BlockState state);

    /**
     * Explosion resistance of the block or its fluid, whichever is higher. Vanilla: the higher of the
     * block's and the fluid's fixed resistance.
     */
    float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion);

    /** Explosion resistance of the block alone, ignoring its fluid. Vanilla: the block's fixed resistance. */
    float getBlockExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion);

    /** Whether a rider may target the entity it rides (with the crosshair, for example). Vanilla: false. */
    boolean canRiderInteract(Entity vehicle);

    /**
     * Marks the item as not repairable in the crafting grid, the anvil or the grindstone. Vanilla: every
     * damageable item can be repaired, so this returns {@code properties} unchanged.
     */
    Item.Properties noRepair(Item.Properties properties);

    /** Item use that runs before the clicked block is activated. Vanilla: {@code PASS}. */
    InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context);
}
