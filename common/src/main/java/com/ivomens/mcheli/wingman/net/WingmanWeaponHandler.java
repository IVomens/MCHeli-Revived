package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.roster.Wingman;
import net.minecraft.server.level.ServerPlayer;

/** Sets the weapon type a wingman prefers; an empty type allows any weapon. */
public final class WingmanWeaponHandler implements ServerMessageHandler<WingmanWeaponMessage> {

    @Override
    public void handle(WingmanWeaponMessage message, ServerPlayer sender) {
        Wingman wingman = WingmanService.of(sender).access().existing(sender, message.wingmanId());
        if (wingman != null) {
            wingman.attack().setWeaponType(message.weaponType().isEmpty() ? null : message.weaponType());
        }
    }
}
