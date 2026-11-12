package com.ivomens.mcheli.plane.client;

import com.ivomens.mcheli.aircraft.net.ControlInputBuilder;
import com.ivomens.mcheli.aircraft.net.PlaneControlMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.vehicle.client.VehicleControlContext;
import net.minecraft.client.Minecraft;

public class PlaneClientTickHandler extends AbstractPlaneClientTickHandler<PlaneEntity> {

    public PlaneClientTickHandler(Minecraft minecraft, KeySettings bindings, VehicleControlContext controls) {
        super(minecraft, bindings, PlaneEntity.class, controls);
    }

    @Override
    protected void sendControlPacket(ControlInputBuilder control) {
        Network.sendToServer(new PlaneControlMessage(control.build()));
    }
}
