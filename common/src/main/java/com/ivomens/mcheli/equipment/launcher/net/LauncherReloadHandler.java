package com.ivomens.mcheli.equipment.launcher.net;

import com.ivomens.mcheli.equipment.launcher.LightWeaponItem;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.util.Inventories;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Reloads the empty launcher in the sender's hand from one round in their inventory. */
public final class LauncherReloadHandler implements ServerMessageHandler<LauncherReloadMessage> {

    @Override
    public void handle(LauncherReloadMessage message, ServerPlayer sender) {
        ItemStack held = sender.getMainHandItem();
        // Firing sets the damage to its maximum (empty launcher); reloading resets it.
        if (held.getItem() instanceof LightWeaponItem launcher && held.getDamageValue() > 1
                && Inventories.hasItem(sender, launcher.bullet)) {
            if (!sender.getAbilities().instabuild) {
                Inventories.removeOne(sender, launcher.bullet);
            }
            held.setDamageValue(0);
        }
    }
}
