package com.ivomens.mcheli.combat.explosion.net;

import com.ivomens.mcheli.combat.explosion.ExplosionEffectSender;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Remembers whether the sender's client draws explosions from their blocks. */
public final class ExplosionDetailHandler implements ServerMessageHandler<ExplosionDetailMessage> {

    @Override
    public void handle(ExplosionDetailMessage message, ServerPlayer sender) {
        ExplosionEffectSender.of(sender.server).setBlockDetail(sender, message.blocks());
    }
}
