package com.ivomens.mcheli.tank.client;

import com.ivomens.mcheli.aircraft.net.ControlInputBuilder;
import com.ivomens.mcheli.aircraft.net.TankControlMessage;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.client.VehicleControlContext;
import com.ivomens.mcheli.vehicle.client.VehicleControlTickHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class TankClientTickHandler extends VehicleControlTickHandler<TankEntity, ControlInputBuilder> {

    private static final int RADAR_SPEED = 10;

    public TankClientTickHandler(Minecraft minecraft, KeySettings bindings, VehicleControlContext controls) {
        super(minecraft, bindings, TankEntity.class, controls);
    }

    @Override
    protected InputKey[] createTypeKeys() {
        return new InputKey[] {KeyGearUpDown, KeyBrake, KeyPutToRack, KeyDownFromRack};
    }

    @Override
    protected ControlInputBuilder createControlData() {
        return new ControlInputBuilder();
    }

    @Override
    protected void sendControlPacket(ControlInputBuilder control) {
        Network.sendToServer(new TankControlMessage(control.build()));
    }

    @Override
    protected int radarSpeed() {
        return RADAR_SPEED;
    }

    @Override
    protected void onStartRiding(TankEntity tank) {
        super.onStartRiding(tank);
        snapViewToVehicle(tank);
    }

    @Override
    protected boolean handleTypeControls(Player player, TankEntity tank, boolean isPilot,
                                         ControlInputBuilder control) {
        boolean send = false;
        if (keySwitchMode.isKeyDown()) {
            send |= isPilot ? cycleGunnerModeAndCameras(player, tank, control) :
                    switchPassengerGunnerMode(player, tank);
        }

        if (keyZoom.isKeyDown()) {
            boolean haveHatch = tank.spec().parts().haveHatch();
            if (tank.modes().usesGunnerView(player) || (tank.isUAV() && !haveHatch)) {
                zoomCamera(tank);
            } else if (isPilot && haveHatch) {
                send |= requestHatchToggle(tank, control);
            }
        }
        return send;
    }
}
