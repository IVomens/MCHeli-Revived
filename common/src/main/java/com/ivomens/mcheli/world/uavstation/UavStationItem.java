package com.ivomens.mcheli.world.uavstation;

import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.ivomens.mcheli.util.McheliItem;
import com.ivomens.mcheli.core.math.Degrees;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/** Places a UAV station on the block the player looks at, facing the player. */
public class UavStationItem extends McheliItem {

    private static final double REACH = 5.0;

    public final IUavStation.StationType type;

    public UavStationItem(Item.Properties properties, IUavStation.StationType type) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        BlockHitResult hit = getPlayerPOVHitResult(world, player, ClipContext.Fluid.ANY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }

        // placing from inside another entity would trap the station in it
        Vec3 reach = player.getViewVector(1.0F).scale(REACH);
        AABB checkArea = player.getBoundingBox().expandTowards(reach.x, reach.y, reach.z).inflate(1.0);
        Vec3 eye = player.getEyePosition(1.0F);
        boolean obstructed = world.getEntities(player, checkArea).stream()
                .anyMatch(entity -> entity.isPickable() &&
                        entity.getBoundingBox().inflate(entity.getPickRadius()).contains(eye));
        if (obstructed) {
            return InteractionResultHolder.fail(stack);
        }

        BlockPos pos = hit.getBlockPos();
        UavStationEntity uavStation = new UavStationEntity(world);
        uavStation.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        uavStation.setType(this.type);

        // snap to the nearest cardinal direction, facing the player
        int yaw = (int) (Degrees.wrap360(player.getYRot()) + 45.0);
        uavStation.setYRot((float) (yaw / 90 * 90 - 180));
        uavStation.initUavPosition();

        if (!world.noCollision(uavStation, uavStation.getBoundingBox().inflate(-0.1))) {
            return InteractionResultHolder.fail(stack);
        }

        if (!world.isClientSide) {
            world.addFreshEntity(uavStation);
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
    }
}
