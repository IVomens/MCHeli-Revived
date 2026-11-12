package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Releases every wingman of the aircraft the sender rides. */
public final class WingmanStopAllHandler implements ServerMessageHandler<WingmanStopAllMessage> {

    @Override
    public void handle(WingmanStopAllMessage message, ServerPlayer sender) {
        Entity leader = sender.getVehicle();
        if (leader != null) {
            WingmanService.of(sender).roster().releaseFollowersOf(leader);
        }
    }
}
