package com.ivomens.mcheli.helicopter.client;

import com.ivomens.mcheli.aircraft.net.ControlInput;
import com.ivomens.mcheli.aircraft.net.HelicopterControlMessage;
import com.ivomens.mcheli.aircraft.net.VehicleControlInputBuilder;
import com.ivomens.mcheli.aircraft.net.VehicleExtras;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.config.JoystickAction;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.vehicle.client.VehicleControlContext;
import com.ivomens.mcheli.vehicle.client.VehicleControlTickHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class HelicopterClientTickHandler extends VehicleControlTickHandler<HelicopterEntity, VehicleControlInputBuilder> {

    private static final int RADAR_SPEED = 5;

    private InputKey keySwitchHovering;

    public HelicopterClientTickHandler(Minecraft minecraft, KeySettings bindings, VehicleControlContext controls) {
        super(minecraft, bindings, HelicopterEntity.class, controls);
    }

    @Override
    protected InputKey[] createTypeKeys() {
        keySwitchHovering = joystickKey(McheliConfig.keys().switchHovering(), JoystickAction.HOVER);
        return new InputKey[] {keySwitchHovering, KeyGearUpDown, KeyPutToRack, KeyDownFromRack};
    }

    @Override
    protected VehicleControlInputBuilder createControlData() {
        return new VehicleControlInputBuilder();
    }

    @Override
    protected void sendControlPacket(VehicleControlInputBuilder control) {
        Network.sendToServer(new HelicopterControlMessage(control.build(), control.buildExtras()));
    }

    @Override
    protected int radarSpeed() {
        return RADAR_SPEED;
    }

    @Override
    protected boolean handleTypeControls(Player player, HelicopterEntity heli, boolean isPilot,
                                         VehicleControlInputBuilder control) {
        boolean send = false;
        if (isPilot) {
            if (KeyExtra.isKeyDown()) {
                send |= unhitchOrToggleBlades(heli, control);
            }
            if (keySwitchHovering.isKeyDown()) {
                send |= toggleHovering(heli, control);
            } else if (keySwitchMode.isKeyDown()) {
                send |= toggleGunnerModeIfAllowed(player, heli, control);
            }
        } else if (keySwitchMode.isKeyDown()) {
            send |= switchPassengerGunnerMode(player, heli);
        }

        if (keyZoom.isKeyDown()) {
            send |= handleZoomKey(player, heli, isPilot, control);
        }
        return send;
    }

    private boolean unhitchOrToggleBlades(HelicopterEntity heli, VehicleControlInputBuilder control) {
        if (requestUnhitchTowChain(heli, control)) {
            return true;
        }
        if (!heli.blades().canSwitch()) {
            playSoundNG();
            return false;
        }

        if (heli.blades().isFolded()) {
            heli.blades().unfold();
            control.setBladeStatus(VehicleExtras.BladeStatus.UNFOLD);
        } else {
            heli.blades().fold();
            control.setBladeStatus(VehicleExtras.BladeStatus.FOLD);
        }
        playSoundOK();
        return true;
    }

    private boolean toggleHovering(HelicopterEntity heli, VehicleControlInputBuilder control) {
        if (!heli.modes().canSwitchHoveringMode()) {
            playSoundNG();
            return false;
        }
        boolean hovering = heli.modes().isHoveringMode();
        control.setSwitchMode(hovering ? ControlInput.ModeSwitch.HOVERING_OFF :
                ControlInput.ModeSwitch.HOVERING_ON);
        heli.modes().switchHoveringMode(!hovering);
        return true;
    }

    private boolean toggleGunnerModeIfAllowed(Player player, HelicopterEntity heli, VehicleControlInputBuilder control) {
        if (!heli.modes().canSwitchGunnerMode()) {
            playSoundNG();
            return false;
        }
        toggleGunnerMode(player, heli, control);
        return true;
    }

    /** Zooms the gunner or UAV camera; otherwise the pilot uses the key to open and close the hatch. */
    private boolean handleZoomKey(Player player, HelicopterEntity heli, boolean isPilot,
                                  VehicleControlInputBuilder control) {
        boolean haveHatch = heli.spec().parts().haveHatch();
        if (heli.modes().usesGunnerView(player) || (heli.isUAV() && !haveHatch)) {
            zoomCamera(heli);
            return false;
        }
        if (!isPilot || !haveHatch) {
            return false;
        }
        if (requestHatchToggle(heli, control)) {
            return true;
        }
        playSoundNG();
        return false;
    }
}
