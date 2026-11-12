package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.net.ParachuteMessage;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.client.input.ClientTickHandler;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.vehicle.client.VehicleControlContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Key handling for passengers in a seat: gunner free look and jumping out (parachute or rope). */
public class SeatClientTickHandler extends ClientTickHandler {

    private InputKey keyParachuting;
    private InputKey keyFreeLook;
    private InputKey[] keys;
    private final VehicleControlContext controls;
    private boolean isRiding;

    public SeatClientTickHandler(Minecraft minecraft, KeySettings bindings, VehicleControlContext controls) {
        super(minecraft);
        this.controls = controls;
        this.updateKeybind(bindings);
    }

    @Override
    public void updateKeybind(KeySettings bindings) {
        this.keyParachuting = new InputKey(bindings.switchHovering());
        this.keyFreeLook = new InputKey(bindings.freeLook());
        this.keys = new InputKey[]{this.keyParachuting, this.keyFreeLook};
    }

    @Override
    public void onTick(boolean inGUI) {
        for (InputKey key : this.keys) {
            key.update();
        }

        boolean wasRiding = this.isRiding;
        Player player = this.mc.player;
        AircraftEntity aircraft = null;
        if (player != null && player.getVehicle() instanceof SeatEntity seat) {
            aircraft = seat.getParent();
            if (aircraft == null || aircraft.spec() == null) {
                return;
            }
            if (!inGUI && !aircraft.isDestroyed()) {
                this.handleKeys(player, aircraft);
            }
            this.isRiding = true;
        } else {
            this.isRiding = false;
        }

        if (wasRiding == this.isRiding) {
            return;
        }
        if (this.isRiding) {
            this.controls.camera().useVehicleDistance(aircraft.cameras().thirdPersonDistance());
        } else {
            if (player == null || !(player.getVehicle() instanceof AircraftEntity)) {
                this.controls.camera().restoreThirdPersonDistance();
            }
            this.controls.view().useIfConfigured(player);
        }
    }

    private void handleKeys(Player player, AircraftEntity aircraft) {
        if (this.keyFreeLook.isKeyDown() && aircraft.modes().canSwitchGunnerFreeLook(player)) {
            aircraft.modes().toggleGunnerFreeLook();
        }
        if (this.keyParachuting.isKeyDown()) {
            if (aircraft.crewExit().canParachute(player) || aircraft.ropes().canRepell()) {
                Network.sendToServer(new ParachuteMessage());
            } else {
                playSoundNG();
            }
        }
    }
}
