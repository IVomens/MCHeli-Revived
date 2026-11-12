package com.ivomens.mcheli.plane.client;

import com.ivomens.mcheli.aircraft.net.ControlInput;
import com.ivomens.mcheli.aircraft.net.ControlInputBuilder;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.plane.AbstractPlaneEntity;
import com.ivomens.mcheli.vehicle.client.VehicleControlContext;
import com.ivomens.mcheli.vehicle.client.VehicleControlTickHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Controls shared by planes and ships: camera positions, VTOL, folding wings and the ejection seat. */
public abstract class AbstractPlaneClientTickHandler<T extends AbstractPlaneEntity>
        extends VehicleControlTickHandler<T, ControlInputBuilder> {

    private static final int RADAR_SPEED = 10;

    /** Shares its key with hovering on helicopters. */
    private InputKey keyEjectSeat;

    protected AbstractPlaneClientTickHandler(Minecraft minecraft, KeySettings bindings, Class<T> planeType,
                                             VehicleControlContext controls) {
        super(minecraft, bindings, planeType, controls);
    }

    @Override
    protected InputKey[] createTypeKeys() {
        keyEjectSeat = new InputKey(McheliConfig.keys().switchHovering());
        return new InputKey[] {keyEjectSeat, KeyGearUpDown, KeyPutToRack, KeyDownFromRack};
    }

    @Override
    protected ControlInputBuilder createControlData() {
        return new ControlInputBuilder();
    }

    @Override
    protected int radarSpeed() {
        return RADAR_SPEED;
    }

    @Override
    protected void onStartRiding(T plane) {
        super.onStartRiding(plane);
        snapViewToVehicle(plane);
    }

    @Override
    protected boolean handleTypeControls(Player player, T plane, boolean isPilot, ControlInputBuilder control) {
        boolean send = false;
        if (isPilot) {
            if (keySwitchMode.isKeyDown()) {
                send |= cycleGunnerModeAndCameras(player, plane, control);
            }
            if (KeyExtra.isKeyDown()) {
                send |= toggleVtol(plane, control);
            }
        } else if (keySwitchMode.isKeyDown()) {
            send |= switchPassengerGunnerMode(player, plane);
        }

        if (keyZoom.isKeyDown()) {
            send |= handleZoomKey(player, plane, isPilot, control);
        }

        if (keyEjectSeat.isKeyDown() && plane.ejectionSeats().canEjectSeat(player)) {
            control.setEjectSeat(true);
            send = true;
        }
        return send;
    }

    private boolean toggleVtol(T plane, ControlInputBuilder control) {
        if (!plane.nozzles().canSwitch()) {
            playSoundNG();
            return false;
        }
        boolean vtol = plane.nozzles().isVertical();
        control.setSwitchVtol(vtol ? ControlInput.VtolSwitch.VTOL_OFF :
                ControlInput.VtolSwitch.VTOL_ON);
        plane.nozzles().switchTo(!vtol);
        return true;
    }

    /** Zooms the gunner or UAV camera; otherwise the pilot uses the key for the hatch or folding wings. */
    private boolean handleZoomKey(Player player, T plane, boolean isPilot, ControlInputBuilder control) {
        boolean haveHatch = plane.spec().parts().haveHatch();
        if (plane.modes().usesGunnerView(player)
                || (plane.isUAV() && !haveHatch && !plane.planeSpec().kindParts().haveWing())) {
            zoomCamera(plane);
            return false;
        }
        if (!isPilot) {
            return false;
        }
        if (haveHatch) {
            return requestHatchToggle(plane, control);
        }
        // the hatch switch doubles as the wing switch; UNFOLD folds the wing on the server
        if (plane.wings().canFold()) {
            control.setSwitchHatch(ControlInput.HatchSwitch.UNFOLD);
            return true;
        }
        if (plane.wings().canUnfold()) {
            control.setSwitchHatch(ControlInput.HatchSwitch.FOLD);
            return true;
        }
        return false;
    }
}
