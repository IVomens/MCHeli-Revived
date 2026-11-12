package com.ivomens.mcheli.equipment.launcher.net;

import com.ivomens.mcheli.equipment.launcher.LightWeaponItem;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.combat.weapon.FireRequest;
import com.ivomens.mcheli.combat.weapon.HandheldShooter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Fires the loaded launcher in the sender's hand. A launch position further than
 * {@link #MAX_POS_OFFSET} from the sender's eyes, or not finite, is replaced by the eyes.
 */
public final class LauncherFireHandler implements ServerMessageHandler<LauncherFireMessage> {

    private static final double MAX_POS_OFFSET = 16.0;

    private final HandheldArsenal arsenal = new HandheldArsenal();

    @Override
    public void handle(LauncherFireMessage message, ServerPlayer sender) {
        ItemStack held = sender.getMainHandItem();
        if (!(held.getItem() instanceof LightWeaponItem) || held.getDamageValue() >= held.getMaxDamage()) {
            return;
        }
        String weaponName = LightWeaponItem.getLightWeaponName(held);
        WeaponMount weapon = this.arsenal.mountFor(sender, weaponName);
        if (weapon == null) {
            return;
        }
        Vec3 eyes = new Vec3(sender.getX(), sender.getEyeY(), sender.getZ());
        Vec3 pos = message.pos();
        Vec3 offset = pos.subtract(eyes);
        if (!Double.isFinite(offset.x + offset.y + offset.z) || offset.lengthSqr() > MAX_POS_OFFSET * MAX_POS_OFFSET) {
            pos = eyes;
        }

        weapon.fireOnce(new HandheldShooter(sender), pos, sender.getYRot(), sender.getXRot(),
                FireRequest.locked(message.targetId(), message.mode()));

        if (!sender.getAbilities().instabuild) {
            // An empty launcher is one at full damage; single-shot launchers are used up.
            if (held.getMaxDamage() == 1) {
                held.shrink(1);
            } else if (held.getMaxDamage() > 1) {
                held.setDamageValue(held.getMaxDamage());
            }
        }
    }
}
