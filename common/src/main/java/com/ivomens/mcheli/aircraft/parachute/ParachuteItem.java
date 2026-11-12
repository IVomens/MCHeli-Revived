package com.ivomens.mcheli.aircraft.parachute;

import com.ivomens.mcheli.util.McheliItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/** Opens a parachute above a falling player; consumed on use. */
public class ParachuteItem extends McheliItem {

    public ParachuteItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level world, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide && player.getVehicle() == null && !player.onGround()) {
            double x = player.getX() + 0.5;
            double y = player.getY() + 3.5;
            double z = player.getZ() + 0.5;
            ParachuteEntity entity = new ParachuteEntity(world, x, y, z);
            entity.setYRot(player.getYRot());
            entity.setDeltaMovement(player.getDeltaMovement());
            entity.fallDistance = player.fallDistance;
            player.fallDistance = 0.0F;
            entity.setUser(player);
            entity.setType(ParachuteEntity.TYPE_HANDHELD);
            world.addFreshEntity(entity);
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }
}
