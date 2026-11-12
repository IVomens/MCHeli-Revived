package com.ivomens.mcheli.equipment.gltd.net;

import com.ivomens.mcheli.equipment.gltd.GltdEntity;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Fires the designator laser. */
public final class GltdFireHandler implements ServerMessageHandler<GltdFireMessage> {

    @Override
    public void handle(GltdFireMessage message, ServerPlayer sender) {
        if (sender.getVehicle() instanceof GltdEntity gltd) {
            gltd.useCurrentWeapon(0, 0);
        }
    }
}
