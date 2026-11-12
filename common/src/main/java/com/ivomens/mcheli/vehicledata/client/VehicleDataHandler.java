package com.ivomens.mcheli.vehicledata.client;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.networking.ClientMessageHandler;
import com.ivomens.mcheli.vehicledata.VehicleDataMessage;
import com.ivomens.mcheli.vehicledata.VehicleDataStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/** Hands the addon values the server sent to the client's copy of the vehicle. */
public final class VehicleDataHandler implements ClientMessageHandler<VehicleDataMessage> {

    @Override
    public void handle(VehicleDataMessage message) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level != null ? level.getEntity(message.entityId()) : null;
        if (entity instanceof Vehicle vehicle && vehicle.data() instanceof VehicleDataStore store) {
            store.receive(message.entries());
        }
    }
}
