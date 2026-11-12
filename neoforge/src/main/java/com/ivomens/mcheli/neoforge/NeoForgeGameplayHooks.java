package com.ivomens.mcheli.neoforge;

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
import net.neoforged.neoforge.event.EventHooks;

import java.util.List;

public class NeoForgeGameplayHooks implements GameplayHooks {

    @Override
    public void onExplosionDetonate(Level level, Explosion explosion, List<Entity> entities, double diameter) {
        EventHooks.onExplosionDetonate(level, explosion, entities, diameter);
    }

    @Override
    public BlockState fluidPlaceBlock(LevelAccessor level, BlockPos pos, BlockPos liquidPos, BlockState state) {
        return EventHooks.fireFluidPlaceBlockEvent(level, pos, liquidPos, state);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return Math.max(state.getExplosionResistance(level, pos, explosion),
                state.getFluidState().getExplosionResistance(level, pos, explosion));
    }

    @Override
    public float getBlockExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return state.getExplosionResistance(level, pos, explosion);
    }

    @Override
    public boolean canRiderInteract(Entity vehicle) {
        return vehicle.canRiderInteract();
    }

    @Override
    public Item.Properties noRepair(Item.Properties properties) {
        return properties.setNoRepair();
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return stack.onItemUseFirst(context);
    }
}
