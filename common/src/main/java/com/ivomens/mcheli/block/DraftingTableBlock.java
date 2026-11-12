package com.ivomens.mcheli.block;

import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.registry.McheliBlocks;
import com.ivomens.mcheli.core.math.Degrees;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Drafting table: right click opens the crafting screen for aircraft and MCHeli items, sneak +
 * right click toggles its lamp (switching between the unlit and the lit block). Facing has eight
 * directions; the table is drawn by its block entity renderer.
 */
public class DraftingTableBlock extends BaseEntityBlock {

    public static final PropertyDirection8 DIRECTION8 = PropertyDirection8.create("direction8");
    public static final MapCodec<DraftingTableBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            propertiesCodec(),
            Codec.BOOL.fieldOf("lit").forGetter(b -> b.isLighting)
    ).apply(i, DraftingTableBlock::new));

    private final boolean isLighting;

    public DraftingTableBlock(BlockBehaviour.Properties properties, boolean isOn) {
        super(properties);
        this.isLighting = isOn;
        this.registerDefaultState(this.stateDefinition.any().setValue(DIRECTION8, EnumDirection8.NORTH));
    }

    public static BlockBehaviour.Properties defaultProperties(boolean isOn) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL)
                .strength(0.2F).noOcclusion().pushReaction(PushReaction.DESTROY);
        return isOn ? p.lightLevel(s -> 15) : p;
    }

    public boolean isLighting() {
        return isLighting;
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(DIRECTION8);
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            Block toggled = this.isLighting ? McheliBlocks.DRAFTING_TABLE.get() : McheliBlocks.LIT_DRAFTING_TABLE.get();
            level.setBlock(pos, toggled.defaultBlockState().setValue(DIRECTION8, state.getValue(DIRECTION8)), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, 0.5F);
        } else if (player instanceof ServerPlayer serverPlayer) {
            BlockPos tablePos = pos.immutable();
            Services.MENUS.openMenu(serverPlayer, new SimpleMenuProvider((id, inventory, p) -> new DraftingTableMenu(id, inventory, tablePos),
                    Component.translatable("block.mcheli.drafting_table")), buf -> buf.writeBlockPos(tablePos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new DraftingTableBlockEntity(pos, state);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() : context.getRotation();
        return this.defaultBlockState().setValue(DIRECTION8, EnumDirection8.fromAngle(Degrees.wrap360(yaw)));
    }

    @Override
    public @NotNull ItemStack getCloneItemStack(@NotNull LevelReader level, @NotNull BlockPos pos, @NotNull BlockState state) {
        return new ItemStack(McheliBlocks.DRAFTING_TABLE.get());
    }

    @Override
    protected boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        return true;
    }

    @Override
    protected float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return 1.0F;
    }
}
