package com.ivomens.mcheli.equipment.gltd.net;

import com.ivomens.mcheli.equipment.gltd.GltdEntity;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Gets the operator off the designator. */
public final class GltdDismountHandler implements ServerMessageHandler<GltdDismountMessage> {

    @Override
    public void handle(GltdDismountMessage message, ServerPlayer sender) {
        if (sender.getVehicle() instanceof GltdEntity gltd) {
            Entity operator = gltd.getRiddenByEntity();
            if (operator != null) {
                operator.stopRiding();
            }
        }
    }
}
