package com.ivomens.mcheli.vehicle.client;

import com.ivomens.mcheli.aircraft.client.AircraftClientTickHandler;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.aircraft.net.ControlInput;
import com.ivomens.mcheli.aircraft.net.ControlInputBuilder;
import com.ivomens.mcheli.aircraft.net.VehicleControlInputBuilder;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.config.JoystickAction;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.vehicle.PilotFreeLook;
import com.ivomens.mcheli.vehicle.client.camera.ViewEntityDummy;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Client control loop shared by all ridable vehicle types.
 * <p>
 * Each tick it polls the tracked keys, finds the vehicle of type {@code T} the player controls (as pilot, from a
 * gunner seat or through a UAV station), drives the camera and sends a control packet when anything changed.
 * Subclasses add their type-specific keys and actions.
 *
 * @param <T> the vehicle entity this handler controls
 * @param <D> the control data its packet carries
 */
public abstract class VehicleControlTickHandler<T extends AircraftEntity, D extends ControlInputBuilder>
        extends AircraftClientTickHandler {

    private static final float ZOOM_SOUND_VOLUME = 0.5F;
    /** Height above the vehicle origin the camera starts at when boarding. */
    private static final double BOARDING_VIEW_HEIGHT = 0.5;

    private final Class<T> vehicleType;
    protected InputKey keySwitchMode;
    protected InputKey keyZoom;
    private InputKey[] trackedKeys;

    protected VehicleControlTickHandler(Minecraft minecraft, KeySettings bindings, Class<T> vehicleType,
                                        VehicleControlContext controls) {
        super(minecraft, bindings, controls);
        this.vehicleType = vehicleType;
    }

    /**
     * Called from the superclass constructor, so subclass state must not be read here before
     * {@link #createTypeKeys()} assigns it.
     */
    @Override
    public final void updateKeybind(KeySettings bindings) {
        super.updateKeybind(bindings);
        keySwitchMode = new InputKey(bindings.switchMode());
        keyZoom = joystickKey(bindings.zoom(), JoystickAction.ZOOM);

        InputKey[] commonKeys = {
                KeyUp, KeyDown, KeyRight, KeyLeft,
                keySwitchMode, keyZoom, KeyCameraMode, KeyFreeLook, KeyGUI, KeyExtra,
                KeyUseWeapon, KeyReloadWeapon, KeySwWeaponMode, KeySwitchWeapon1, KeySwitchWeapon2,
                KeyUnmount, KeyUnmountForce,
                KeyFlare, KeyChaff, KeyAPS, KeyECMJammer, KeyRadarSwitch, KeyAirburstDistReset, KeyOpenGPSPanel
        };
        InputKey[] typeKeys = createTypeKeys();
        trackedKeys = new InputKey[commonKeys.length + typeKeys.length];
        System.arraycopy(commonKeys, 0, trackedKeys, 0, commonKeys.length);
        System.arraycopy(typeKeys, 0, trackedKeys, commonKeys.length, typeKeys.length);
    }

    /**
     * Creates the keys only this vehicle type uses and returns every extra key (new or inherited) that must be
     * polled each tick on top of the common set. Keys not polled never report a press.
     */
    protected abstract InputKey[] createTypeKeys();

    protected abstract D createControlData();

    protected abstract void sendControlPacket(D control);

    /**
     * Handles the keys specific to this vehicle type. Every branch must run (no short-circuiting) so all pressed
     * keys are honoured in the same tick.
     *
     * @return true if the control packet must be sent
     */
    protected abstract boolean handleTypeControls(Player player, T vehicle, boolean isPilot, D control);

    /** Rotation speed passed to {@link AircraftEntity#updateRadar(int)}. */
    protected abstract int radarSpeed();

    @Override
    public final void onTick(boolean inGUI) {
        for (InputKey key : trackedKeys) {
            key.update();
        }

        isBeforeRiding = isRiding;
        Player player = mc.player;
        Entity ridden = player != null ? player.getVehicle() : null;
        T vehicle = findControlledVehicle(ridden);
        isRiding = vehicle != null && vehicle.spec() != null;
        if (isRiding) {
            tickControlled(player, vehicle, !(ridden instanceof SeatEntity), inGUI);
        }

        if (!isBeforeRiding && isRiding) {
            onStartRiding(vehicle);
        } else if (isBeforeRiding && !isRiding) {
            onStopRiding(player);
        }
    }

    @Nullable
    private T findControlledVehicle(@Nullable Entity ridden) {
        Entity candidate = ridden;
        if (!vehicleType.isInstance(ridden)) {
            if (ridden instanceof SeatEntity seat) {
                candidate = seat.getParent();
            } else if (ridden instanceof IUavStation station && canBeControlledFromUavStation()) {
                candidate = station.getControlled();
            }
        }
        return vehicleType.isInstance(candidate) ? vehicleType.cast(candidate) : null;
    }

    protected boolean canBeControlledFromUavStation() {
        return true;
    }

    private void tickControlled(Player player, T vehicle, boolean isPilot, boolean inGUI) {
        applyPitchLimit(player, vehicle);
        vehicle.cameras().rotate(player.getYRot(), player.getXRot());
        vehicle.radar().sweep(radarSpeed());

        this.controls.camera().followCamera(mc.level, vehicle.cameras().camera());
        ViewEntityDummy view = this.controls.camera().viewDummy(mc.level);
        if (!inGUI && !vehicle.isDestroyed()) {
            playerControl(player, vehicle, isPilot);
        }

        if (showsPlayerView(player, vehicle, isPilot)) {
            this.controls.view().useIfConfigured(player);
            // passengers without a weapon keep their hand visible
            if (isPilot || vehicle.weapons().currentId(player) >= 0) {
                this.controls.view().hideHeldItem(player.getMainHandItem());
            }
        } else {
            this.controls.view().useIfConfigured(view);
            this.controls.view().hideHeldItem(player.getMainHandItem());
        }
    }

    /** Gunners are held to their seat's pitch range. */
    protected void applyPitchLimit(Player player, T vehicle) {
        if (!vehicle.modes().usesGunnerView(player)) {
            return;
        }
        SeatInfo seatInfo = vehicle.seats().info(player);
        if (seatInfo != null) {
            this.controls.pitchLimit().limit(seatInfo.minPitch, seatInfo.maxPitch, player);
        }
    }

    /** Whether the player looks through their own eyes instead of the vehicle camera. */
    protected boolean showsPlayerView(Player player, T vehicle, boolean isPilot) {
        return (!isPilot || vehicle.cameras().pilotUsesOwnEyes()) && !vehicle.modes().usesGunnerView(player) &&
                vehicle.cameras().cameraId() <= 0;
    }

    protected void onStartRiding(T vehicle) {
        this.controls.camera().useVehicleDistance(vehicle.cameras().thirdPersonDistance());
    }

    /** Moves the camera entity onto the vehicle so the first frame after boarding does not lag behind. */
    protected final void snapViewToVehicle(T vehicle) {
        this.controls.camera().viewDummy(mc.level)
                .setPos(vehicle.getX(), vehicle.getY() + BOARDING_VIEW_HEIGHT, vehicle.getZ());
    }

    private void onStopRiding(Player player) {
        this.controls.camera().restoreThirdPersonDistance();
        this.controls.camera().setRoll(0.0F);
        this.controls.view().showHeldItem();
        this.controls.view().useIfConfigured(player);
    }

    private void playerControl(Player player, T vehicle, boolean isPilot) {
        D control = createControlData();
        boolean send = commonPlayerControl(player, vehicle, isPilot, control);
        send |= PilotFreeLook.handle(vehicle, isPilot, KeyFreeLook.isKeyDown(), control);
        send |= handleTypeControls(player, vehicle, isPilot, control);
        if (send) {
            sendControlPacket(control);
            recordDetachedAimSync(vehicle);
        }
    }

    /** Toggles the pilot's gunner mode. */
    protected final void toggleGunnerMode(Player player, T vehicle, D control) {
        boolean gunnerMode = vehicle.modes().usesGunnerView(player);
        control.setSwitchMode(gunnerMode ? ControlInput.ModeSwitch.GUNNER_OFF :
                ControlInput.ModeSwitch.GUNNER_ON);
        vehicle.modes().switchGunnerMode(!gunnerMode);
    }

    /** Gunner mode switch for a passenger seat. */
    protected final boolean switchPassengerGunnerMode(Player player, T vehicle) {
        if (!vehicle.modes().canSwitchGunnerModeOtherSeat(player)) {
            playSoundNG();
            return false;
        }
        vehicle.modes().toggleGunnerModeOtherSeat();
        return true;
    }

    /**
     * Pilot mode key for vehicles with extra camera positions: cycles gunner mode and the camera positions in
     * the order pilot view, gunner mode, camera 1..n.
     */
    protected final boolean cycleGunnerModeAndCameras(Player player, T vehicle, D control) {
        if (vehicle.modes().usesGunnerView(player) && vehicle.cameras().canSwitchPosition()) {
            control.setSwitchMode(ControlInput.ModeSwitch.GUNNER_OFF);
            vehicle.modes().switchGunnerMode(false);
            vehicle.cameras().setCameraId(1);
            return true;
        }
        if (vehicle.cameras().cameraId() > 0) {
            int next = vehicle.cameras().cameraId() + 1;
            vehicle.cameras().setCameraId(next >= vehicle.cameras().positionCount() ? 0 : next);
            return false;
        }
        if (vehicle.modes().canSwitchGunnerMode()) {
            toggleGunnerMode(player, vehicle, control);
            vehicle.cameras().setCameraId(0);
            return true;
        }
        if (vehicle.cameras().canSwitchPosition()) {
            vehicle.cameras().setCameraId(1);
        } else {
            playSoundNG();
        }
        return false;
    }

    protected final void zoomCamera(T vehicle) {
        vehicle.cameras().zoom();
        playSound("zoom", ZOOM_SOUND_VOLUME, 1.0F);
    }

    /**
     * Requests opening or closing the hatch, whichever is possible right now.
     *
     * @return false if the hatch is still moving
     */
    protected final boolean requestHatchToggle(T vehicle, D control) {
        // UNFOLD sets the hatch part's status on, which is what canUnfoldHatch() waits for
        if (vehicle.parts().canUnfoldHatch()) {
            control.setSwitchHatch(ControlInput.HatchSwitch.UNFOLD);
            return true;
        }
        if (vehicle.parts().canFoldHatch()) {
            control.setSwitchHatch(ControlInput.HatchSwitch.FOLD);
            return true;
        }
        return false;
    }

    /**
     * Requests releasing the tow chain attached to the vehicle, if any.
     *
     * @return false if nothing is being towed
     */
    protected final boolean requestUnhitchTowChain(T vehicle, VehicleControlInputBuilder control) {
        if (vehicle.tow().towChain() == null) {
            return false;
        }
        playSoundOK();
        control.setUnhitchChainId(EntityIds.of(vehicle.tow().towChain().entity()));
        return true;
    }
}
