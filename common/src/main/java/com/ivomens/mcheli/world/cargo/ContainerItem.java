package com.ivomens.mcheli.world.cargo;

import com.ivomens.mcheli.util.McheliItem;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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

/** Places a cargo container on the block the player looks at, facing away from the player. */
public class ContainerItem extends McheliItem {

    private static final double REACH = 5.0;

    public ContainerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 eye = player.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(REACH)), ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        if (this.isEyeInsideEntity(level, player, eye)) {
            return InteractionResultHolder.fail(stack);
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.success(stack);
        }

        BlockPos pos = hit.getBlockPos();
        CargoContainerEntity container = new CargoContainerEntity(level, pos.getX() + 0.5F, pos.getY() + 1.0F, pos.getZ() + 0.5F);
        container.setYRot(((Mth.floor(player.getYRot() * 4.0F / 360.0F + 0.5) & 3) - 1) * 90);
        if (!level.noCollision(container, container.getBoundingBox().inflate(-0.1))) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            level.addFreshEntity(container);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }

    /** Whether the player's eyes are inside an entity they could otherwise place the container through. */
    private boolean isEyeInsideEntity(Level level, Player player, Vec3 eye) {
        Vec3 view = player.getViewVector(1.0F);
        List<Entity> nearby = level.getEntities(player,
                player.getBoundingBox().expandTowards(view.x * REACH, view.y * REACH, view.z * REACH).inflate(1.0), Entity::isPickable);
        for (Entity entity : nearby) {
            if (entity.getBoundingBox().inflate(entity.getPickRadius()).contains(eye)) {
                return true;
            }
        }
        return false;
    }
}
