package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.core.wingman.brain.AttackMode;
import com.ivomens.mcheli.wingman.roster.Wingman;
import net.minecraft.server.level.ServerPlayer;

/** Switches a wingman between attacking on its own and holding fire; clears any manual target. */
public final class WingmanAttackModeHandler implements ServerMessageHandler<WingmanAttackModeMessage> {

    @Override
    public void handle(WingmanAttackModeMessage message, ServerPlayer sender) {
        Wingman wingman = WingmanService.of(sender).access().existing(sender, message.wingmanId());
        if (wingman != null) {
            wingman.attack().set(message.autoAttack() ? AttackMode.AUTO : AttackMode.NONE, null);
        }
    }
}
