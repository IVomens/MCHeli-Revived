package com.ivomens.mcheli.equipment.wrench.client;

import com.ivomens.mcheli.equipment.wrench.WrenchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** How the local player's wrench behaves while it repairs: it keeps repairing and holds still. */
public final class WrenchUse {

    private WrenchUse() {
    }

    /**
     * Damaging the wrench replaces the held stack, which ends the vanilla use; restarts it so repairing
     * continues. Called at the start of the player's tick.
     */
    public static void keepRepairing(Player player) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!stack.isEmpty() && stack.getItem() instanceof WrenchItem
                && player.getUseItemRemainingTicks() > 0 && player.getUseItem() != stack) {
            int damage = stack.getDamageValue();
            if (damage > 0 && damage <= stack.getMaxDamage()) {
                player.startUsingItem(InteractionHand.MAIN_HAND);
            }
        }
    }

    /** Keeps the held wrench from bobbing while it is being used. Called every frame. */
    public static void holdStill(Player player) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof WrenchItem && player.getUseItemRemainingTicks() > 0) {
            ItemInHandRenderer renderer = Minecraft.getInstance().gameRenderer.itemInHandRenderer;
            renderer.mainHandHeight = 1.0F;
            renderer.oMainHandHeight = 1.0F;
        }
    }
}
