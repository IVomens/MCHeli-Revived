package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.fabric.extension.RepairableItem;
import com.ivomens.mcheli.platform.GameplayHooks;
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
 * Fabric has none of NeoForge's per-block, per-item and explosion hooks, so most of these do what plain
 * vanilla does; for every block and item MCHeli ships that is also what NeoForge does. The exception
 * is {@link #noRepair}, which the {@code RepairableItem} mixins honour in the crafting grid and the
 * grindstone, the two places NeoForge checks it.
 */
public class FabricGameplayHooks implements GameplayHooks {

    /** Fabric has no explosion event for other mods to edit the hit entities. */
    @Override
    public void onExplosionDetonate(Level level, Explosion explosion, List<Entity> entities, double diameter) {
    }

    /** Fabric has no fluid placement event. */
    @Override
    public BlockState fluidPlaceBlock(LevelAccessor level, BlockPos pos, BlockPos liquidPos, BlockState state) {
        return state;
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return Math.max(state.getBlock().getExplosionResistance(), state.getFluidState().getExplosionResistance());
    }

    @Override
    public float getBlockExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return state.getBlock().getExplosionResistance();
    }

    /** No Fabric entity can opt in; NeoForge's default, which none of MCHeli's entities change, is also false. */
    @Override
    public boolean canRiderInteract(Entity vehicle) {
        return false;
    }

    @Override
    public Item.Properties noRepair(Item.Properties properties) {
        ((RepairableItem.Properties) properties).mcheli$setNoRepair();
        return properties;
    }

    /** None of MCHeli's items uses this NeoForge hook, and Fabric has no item that could. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return InteractionResult.PASS;
    }
}
