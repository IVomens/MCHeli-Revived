package com.ivomens.mcheli.vehicle.client;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.net.GroundVehicleControlMessage;
import com.ivomens.mcheli.aircraft.net.VehicleControlInputBuilder;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Controls for ground vehicles (mounted guns and similar), which always look through the vehicle camera. */
public class VehicleClientTickHandler extends VehicleControlTickHandler<GroundVehicleEntity, VehicleControlInputBuilder> {

    private static final int RADAR_SPEED = 5;

    public VehicleClientTickHandler(Minecraft minecraft, KeySettings bindings, VehicleControlContext controls) {
        super(minecraft, bindings, GroundVehicleEntity.class, controls);
    }

    @Override
    protected InputKey[] createTypeKeys() {
        return new InputKey[0];
    }

    @Override
    protected VehicleControlInputBuilder createControlData() {
        return new VehicleControlInputBuilder();
    }

    @Override
    protected void sendControlPacket(VehicleControlInputBuilder control) {
        Network.sendToServer(new GroundVehicleControlMessage(control.build(), control.buildExtras()));
    }

    @Override
    protected int radarSpeed() {
        return RADAR_SPEED;
    }

    @Override
    protected boolean canBeControlledFromUavStation() {
        return false;
    }

    @Override
    protected void applyPitchLimit(Player player, GroundVehicleEntity vehicle) {
        VehicleSpec info = vehicle.groundVehicleSpec();
        if (info != null) {
            this.controls.pitchLimit().limit(info.file().physics().rotationLimits().minPitch(),
                    info.file().physics().rotationLimits().maxPitch(), player);
        }
    }

    @Override
    protected boolean showsPlayerView(Player player, GroundVehicleEntity vehicle, boolean isPilot) {
        return false;
    }

    @Override
    protected boolean handleTypeControls(Player player, GroundVehicleEntity vehicle, boolean isPilot,
                                         VehicleControlInputBuilder control) {
        boolean send = false;
        if (KeyExtra.isKeyDown()) {
            if (requestUnhitchTowChain(vehicle, control)) {
                send = true;
            } else {
                playSoundNG();
            }
        }

        if (keyZoom.isKeyDown()) {
            if (vehicle.cameras().canZoom()) {
                zoomCamera(vehicle);
            } else if (vehicle.spec().parts().haveHatch()) {
                if (requestHatchToggle(vehicle, control)) {
                    send = true;
                } else {
                    playSoundNG();
                }
            }
        }
        return send;
    }
}
