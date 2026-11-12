package com.ivomens.mcheli.equipment.gltd;

import com.ivomens.mcheli.util.McheliItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GltdItem extends McheliItem {

    private static final double REACH = 5.0;

    public GltdItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 reach = player.getViewVector(1.0F).scale(REACH);
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(reach), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        if (isEyeInsideEntity(level, player, eye, reach)) {
            return InteractionResultHolder.fail(stack);
        }

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            GltdEntity gltd = new GltdEntity(level, pos.getX() + 0.5F, pos.getY() + 1.0F, pos.getZ() + 0.5F);
            gltd.setYRot(player.getYRot());
            if (!level.noCollision(gltd, gltd.getBoundingBox().inflate(-0.1))) {
                return InteractionResultHolder.fail(stack);
            }
            if (!level.isClientSide) {
                level.addFreshEntity(gltd);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Placing from inside another entity (a vehicle, a mob in your face) would trap the tripod in it. */
    private static boolean isEyeInsideEntity(Level level, Player player, Vec3 eye, Vec3 reach) {
        List<Entity> nearby = level.getEntities(player,
                player.getBoundingBox().expandTowards(reach.x, reach.y, reach.z).inflate(1.0));
        for (Entity entity : nearby) {
            if (entity.isPickable() && entity.getBoundingBox().inflate(entity.getPickRadius()).contains(eye)) {
                return true;
            }
        }
        return false;
    }
}
