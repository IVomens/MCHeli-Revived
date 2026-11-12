package com.ivomens.mcheli.equipment.launcher.net;

import com.ivomens.mcheli.equipment.launcher.LightWeaponItem;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** The launcher sight's night vision: turned on only while the sender holds a launcher. */
public final class NightVisionHandler implements ServerMessageHandler<NightVisionMessage> {

    private static final int NIGHT_VISION_TICKS = 255;

    @Override
    public void handle(NightVisionMessage message, ServerPlayer sender) {
        if (!message.on()) {
            sender.removeEffect(MobEffects.NIGHT_VISION);
        } else if (LightWeaponItem.isHeld(sender)) {
            sender.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, false, false));
        }
    }
}
