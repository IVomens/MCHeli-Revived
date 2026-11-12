package com.ivomens.mcheli.equipment.gltd.net;

import com.ivomens.mcheli.equipment.gltd.GltdEntity;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Switches the designator camera mode. */
public final class GltdCameraModeHandler implements ServerMessageHandler<GltdCameraModeMessage> {

    @Override
    public void handle(GltdCameraModeMessage message, ServerPlayer sender) {
        if (sender.getVehicle() instanceof GltdEntity gltd && message.mode() >= 0) {
            gltd.camera.setMode(0, message.mode());
        }
    }
}
