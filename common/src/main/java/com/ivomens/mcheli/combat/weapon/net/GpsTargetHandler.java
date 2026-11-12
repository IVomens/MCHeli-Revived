package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.combat.weapon.GPSPosition;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Stores the sender's GPS target; positions that are not finite are ignored. */
public final class GpsTargetHandler implements ServerMessageHandler<GpsTargetMessage> {

    @Override
    public void handle(GpsTargetMessage message, ServerPlayer sender) {
        Vec3 pos = message.pos();
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) {
            return;
        }
        GPSPosition gpsPosition = new GPSPosition(pos.x, pos.y, pos.z);
        gpsPosition.setIsActive(message.active());
        gpsPosition.setOwner(sender);
        GPSPosition.currentGPSPositions.put(sender.getId(), gpsPosition);
    }
}
