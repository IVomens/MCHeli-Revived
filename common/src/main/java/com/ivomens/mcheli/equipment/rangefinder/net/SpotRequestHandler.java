package com.ivomens.mcheli.equipment.rangefinder.net;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.equipment.rangefinder.RangeFinderItem;
import com.ivomens.mcheli.multiplay.Multiplay;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.sound.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Uses the range finder in the sender's main hand: marks the looked-at point, or spots entities for
 * the team at the cost of one use. Nothing happens without a range finder in hand.
 */
public final class SpotRequestHandler implements ServerMessageHandler<SpotRequestMessage> {

    /** Filter bits above the two lowest ones select entity categories that use the configured spot time. */
    private static final int CATEGORY_FILTER_MASK = 0xFC;
    private static final int SHORT_SPOT_TIME = 60;
    private static final float SPOT_CONE_ANGLE = 20.0F;

    @Override
    public void handle(SpotRequestMessage message, ServerPlayer sender) {
        ItemStack held = sender.getMainHandItem();
        if (!(held.getItem() instanceof RangeFinderItem)) {
            return;
        }
        int filter = message.targetFilter();
        boolean success = false;
        if (filter == 0) {
            success = Multiplay.markPoint(sender, sender.getX(), sender.getEyeY(), sender.getZ());
        } else if (held.getDamageValue() < held.getMaxDamage()) {
            if (McheliConfig.combat().rangeFinderConsume()) {
                held.hurtAndBreak(1, sender, EquipmentSlot.MAINHAND);
            }
            int spotTime = (filter & CATEGORY_FILTER_MASK) == 0
                    ? SHORT_SPOT_TIME : McheliConfig.combat().rangeFinderSpotTime();
            success = Multiplay.spotEntity(sender, 0.0F, sender.getX(), sender.getEyeY(), sender.getZ(), filter,
                    McheliConfig.combat().rangeFinderSpotDist(), spotTime, SPOT_CONE_ANGLE);
        }
        sender.playSound(ModSounds.getSound(success ? "pi" : "ng"), 1.0F, 1.0F);
    }
}
