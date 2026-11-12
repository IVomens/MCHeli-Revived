package com.ivomens.mcheli.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.NotNull;

/**
 * The fuel fluid, behaving like NeoForge's {@code BaseFlowingFluid} with the properties MCHeli gives it
 * there: flows three blocks to find a slope, loses two levels per block, never forms new sources, ticks
 * every five game ticks and has an explosion resistance of 1.
 */
public abstract class FuelFluid extends FlowingFluid {

    private static final int SLOPE_FIND_DISTANCE = 3;
    private static final int LEVEL_DECREASE_PER_BLOCK = 2;
    private static final int TICK_DELAY = 5;
    private static final float EXPLOSION_RESISTANCE = 1.0F;

    @Override
    public @NotNull Fluid getFlowing() {
        return FabricFluids.MCH_FUEL_FLOWING.get();
    }

    @Override
    public @NotNull Fluid getSource() {
        return FabricFluids.MCH_FUEL.get();
    }

    @Override
    public @NotNull Item getBucket() {
        return FabricFluids.MCH_FUEL_BUCKET.get();
    }

    @Override
    protected boolean canConvertToSource(@NotNull Level level) {
        return false;
    }

    @Override
    protected void beforeDestroyingBlock(@NotNull LevelAccessor level, @NotNull BlockPos pos, BlockState state) {
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Block.dropResources(state, level, pos, blockEntity);
    }

    @Override
    protected int getSlopeFindDistance(@NotNull LevelReader level) {
        return SLOPE_FIND_DISTANCE;
    }

    @Override
    protected int getDropOff(@NotNull LevelReader level) {
        return LEVEL_DECREASE_PER_BLOCK;
    }

    @Override
    protected boolean canBeReplacedWith(@NotNull FluidState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
                                        @NotNull Fluid fluid, @NotNull Direction direction) {
        return direction == Direction.DOWN && !isSame(fluid);
    }

    @Override
    public int getTickDelay(@NotNull LevelReader level) {
        return TICK_DELAY;
    }

    @Override
    protected float getExplosionResistance() {
        return EXPLOSION_RESISTANCE;
    }

    @Override
    protected @NotNull BlockState createLegacyBlock(@NotNull FluidState state) {
        return FabricFluids.MCH_FUEL_BLOCK.get().defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    public boolean isSame(@NotNull Fluid fluid) {
        return fluid == getSource() || fluid == getFlowing();
    }

    public static final class Source extends FuelFluid {

        @Override
        public int getAmount(@NotNull FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(@NotNull FluidState state) {
            return true;
        }
    }

    public static final class Flowing extends FuelFluid {

        public Flowing() {
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(@NotNull StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(@NotNull FluidState state) {
            return false;
        }
    }
}
