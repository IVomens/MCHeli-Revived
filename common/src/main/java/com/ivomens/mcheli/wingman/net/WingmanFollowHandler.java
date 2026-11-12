package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Adds an aircraft to the formation of the aircraft the sender rides, while slots are free. */
public final class WingmanFollowHandler implements ServerMessageHandler<WingmanFollowMessage> {

    @Override
    public void handle(WingmanFollowMessage message, ServerPlayer sender) {
        Entity leader = sender.getVehicle();
        if (leader == null || sender.serverLevel().getEntity(message.wingmanId()) == null) {
            return;
        }
        WingmanService wingmen = WingmanService.of(sender);
        int slot = wingmen.roster().countFollowing(leader);
        if (slot < WingmanConfig.get().maxWingmen()) {
            wingmen.access().follow(sender, message.wingmanId(), leader, slot);
        }
    }
}
