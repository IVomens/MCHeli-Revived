package com.ivomens.mcheli.ship.client;

import com.ivomens.mcheli.aircraft.net.ControlInputBuilder;
import com.ivomens.mcheli.aircraft.net.ShipControlMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.plane.client.AbstractPlaneClientTickHandler;
import com.ivomens.mcheli.ship.ShipEntity;
import com.ivomens.mcheli.vehicle.client.VehicleControlContext;
import net.minecraft.client.Minecraft;

public class ShipClientTickHandler extends AbstractPlaneClientTickHandler<ShipEntity> {

    public ShipClientTickHandler(Minecraft minecraft, KeySettings bindings, VehicleControlContext controls) {
        super(minecraft, bindings, ShipEntity.class, controls);
    }

    @Override
    protected void sendControlPacket(ControlInputBuilder control) {
        Network.sendToServer(new ShipControlMessage(control.build()));
    }
}
