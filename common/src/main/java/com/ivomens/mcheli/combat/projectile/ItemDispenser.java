package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.combat.weapon.DummyEntityPlayer;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A container dropped by a dispenser weapon: on impact it uses the definition's item (water bucket,
 * bone meal...) on the blocks of a half-sphere around the impact point, as the shooting player.
 */
final class ItemDispenser implements RoleBehaviour {

    private final ProjectileEntity container;

    ItemDispenser(ProjectileEntity container) {
        this.container = container;
    }

    @Override
    public boolean onImpact(HitResult hit) {
        if (this.container.level().isClientSide) {
            return true;
        }
        WeaponSpec info = this.container.spec();
        Player player = this.shootingPlayer();
        Item dispensed = info != null ? ContentBindings.dispensedItem(info.id()).orElse(null) : null;
        if (hit != null && dispensed != null && player != null && !player.isRemoved()) {
            this.dispenseAround(Warhead.blockPos(hit), info, dispensed, player);
        }
        this.container.discard();
        return true;
    }

    @Nullable
    private Player shootingPlayer() {
        if (this.container.shooter() instanceof Player player) {
            return player;
        }
        return this.container.platform() instanceof Player player ? player : null;
    }

    /**
     * Uses the item on every block of the upper half-sphere of radius {@code dispenseRange - 1}: always
     * in the inner part, on every other block on average further out.
     */
    private void dispenseAround(BlockPos center, WeaponSpec info, Item item, Player player) {
        Player dummyPlayer = new DummyEntityPlayer(this.container.level(), player);
        dummyPlayer.setXRot(90.0F);
        ItemStack stack = new ItemStack(item);
        if (info.definition().dispenseItem().meta() > 0 && stack.isDamageableItem()) {
            stack.setDamageValue(info.definition().dispenseItem().meta());
        }
        dummyPlayer.setItemInHand(InteractionHand.MAIN_HAND, stack);
        int radius = info.definition().dispenseItem().range() - 1;
        int radiusSq = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSq = x * x + y * y + z * z;
                    if (distSq > radiusSq) continue;
                    if (distSq <= 0.5 * radiusSq || this.container.getRandom().nextInt(2) == 0) {
                        this.useItemOn(center.offset(x, y, z), item, stack, dummyPlayer);
                    }
                }
            }
        }
    }

    private void useItemOn(BlockPos pos, Item item, ItemStack stack, Player dummyPlayer) {
        Level level = this.container.level();
        if (!level.hasChunkAt(pos)) return;
        dummyPlayer.setPos(pos.getX() + 0.5, pos.getY() + 2.5, pos.getZ() + 0.5);
        dummyPlayer.setYRot(this.container.getRandom().nextInt(360));
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        if (item == Items.WATER_BUCKET) {
            if (McheliConfig.combat().collisionDestroyBlock()) {
                extinguish(level, pos, state);
            }
            return;
        }
        if (dummyPlayer.getMainHandItem().isEmpty()) {
            dummyPlayer.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        }
        ItemStack held = dummyPlayer.getMainHandItem();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        UseOnContext ctx = new UseOnContext(level, dummyPlayer, InteractionHand.MAIN_HAND, held, hit);
        if (Services.GAMEPLAY.onItemUseFirst(held, ctx) == InteractionResult.PASS && held.useOn(ctx) == InteractionResult.PASS) {
            held.use(level, dummyPlayer, InteractionHand.MAIN_HAND);
        }
    }

    /** Water puts out fire and turns lava source blocks into obsidian, shallow lava into cobblestone. */
    private static void extinguish(Level level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.FIRE)) {
            level.removeBlock(pos, false);
            return;
        }
        if (!state.getFluidState().is(FluidTags.LAVA) || !state.hasProperty(LiquidBlock.LEVEL)) {
            return;
        }
        int lavaLevel = state.getValue(LiquidBlock.LEVEL);
        if (lavaLevel == 0) {
            level.setBlockAndUpdate(pos, Services.GAMEPLAY.fluidPlaceBlock(level, pos, pos, Blocks.OBSIDIAN.defaultBlockState()));
        } else if (lavaLevel <= 4) {
            level.setBlockAndUpdate(pos, Services.GAMEPLAY.fluidPlaceBlock(level, pos, pos, Blocks.COBBLESTONE.defaultBlockState()));
        }
    }
}
