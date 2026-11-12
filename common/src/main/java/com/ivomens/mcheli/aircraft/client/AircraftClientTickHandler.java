package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.sensor.client.RWRThreatClientTracker;
import com.ivomens.mcheli.vehicle.sensor.IncomingMissiles;
import com.ivomens.mcheli.vehicle.sensor.RWRThreatEvent;
import com.ivomens.mcheli.vehicle.sensor.client.IncomingMissileReader;
import com.ivomens.mcheli.aircraft.Radar;
import com.ivomens.mcheli.aircraft.net.ControlInput;
import com.ivomens.mcheli.aircraft.net.ControlInputBuilder;
import com.ivomens.mcheli.aircraft.net.SwitchSeatMessage;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.client.GpsInputScreen;
import com.ivomens.mcheli.config.JoystickAction;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.client.input.ClientTickHandler;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.sound.client.UiSounds;
import com.ivomens.mcheli.vehicle.client.VehicleControlContext;
import com.ivomens.mcheli.vehicle.client.input.PilotControls;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

/**
 * Key handling shared by the vehicle tick handlers: turns key presses into a player control packet
 * for the server and plays the radar warning tones.
 */
public abstract class AircraftClientTickHandler extends ClientTickHandler {

    /** Config code of the left mouse button. */
    private static final int LEFT_MOUSE_BUTTON = -100;
    /** Config code of the left shift key, which always dismounts. */
    private static final int KEY_LSHIFT = 42;
    /** Ticks between two radar lock and radar search warning tones. */
    private static final int RWR_LOCK_TONE_INTERVAL = 10;
    private static final int RWR_SEARCH_TONE_INTERVAL = 40;
    private static final float RWR_HEAT_MISSILE_PITCH = 1.3F;
    private static final float RWR_RADAR_MISSILE_PITCH = 0.8F;
    /** Controls are resent at least this often even without changes. */
    private static final int KEEP_ALIVE_TICKS = 100;

    protected InputKey KeyUp;
    protected InputKey KeyDown;
    protected InputKey KeyRight;
    protected InputKey KeyLeft;
    protected InputKey KeyUseWeapon;
    protected InputKey KeyReloadWeapon;
    protected InputKey KeySwitchWeapon1;
    protected InputKey KeySwitchWeapon2;
    protected InputKey KeySwWeaponMode;
    protected InputKey KeyUnmount;
    protected InputKey KeyUnmountForce;
    protected InputKey KeyExtra;
    protected InputKey KeyFlare;
    protected InputKey KeyCameraMode;
    protected InputKey KeyFreeLook;
    protected InputKey KeyGUI;
    protected InputKey KeyGearUpDown;
    protected InputKey KeyPutToRack;
    protected InputKey KeyDownFromRack;
    protected InputKey KeyBrake;
    protected InputKey KeyChaff;
    /** Active protection system. */
    protected InputKey KeyAPS;
    protected InputKey KeyECMJammer;
    protected InputKey KeyRadarSwitch;
    protected InputKey KeyAirburstDistReset;
    protected InputKey KeyOpenGPSPanel;
    /** The camera, view, pitch limit and weapon wheel this client shares between its vehicle handlers. */
    protected final VehicleControlContext controls;
    protected boolean isRiding = false;
    protected boolean isBeforeRiding = false;
    private int lastDetachedAimSyncEntityId = Integer.MIN_VALUE;
    private boolean lastDetachedAimSyncActive = false;
    private float lastDetachedAimSyncYaw = 0.0F;
    private float lastDetachedAimSyncPitch = 0.0F;
    private long lastRwrLockSoundTick = 0;
    private long lastRwrScanSoundTick = 0;

    public AircraftClientTickHandler(Minecraft minecraft, KeySettings bindings, VehicleControlContext controls) {
        super(minecraft);
        this.controls = controls;
        this.updateKeybind(bindings);
    }

    @Override
    public void updateKeybind(KeySettings bindings) {
        this.KeyUp = new InputKey(bindings.up(), this.controls.pilot()::throttleUp);
        this.KeyDown = new InputKey(bindings.down(), this.controls.pilot()::throttleDown);
        this.KeyRight = new InputKey(bindings.right());
        this.KeyLeft = new InputKey(bindings.left());
        this.KeyUseWeapon = joystickKey(LEFT_MOUSE_BUTTON, JoystickAction.FIRE);
        this.KeySwitchWeapon1 = new InputKey(bindings.switchWeapon1());
        this.KeySwitchWeapon2 = joystickKey(bindings.switchWeapon2(), JoystickAction.NEXT_WEAPON);
        this.KeySwWeaponMode = joystickKey(bindings.switchWeaponMode(), JoystickAction.WEAPON_MODE);
        this.KeyUnmount = new InputKey(bindings.unmount());
        this.KeyUnmountForce = new InputKey(KEY_LSHIFT);
        this.KeyExtra = joystickKey(bindings.extra(), JoystickAction.EXTRA);
        this.KeyFlare = joystickKey(bindings.flare(), JoystickAction.FLARE);
        this.KeyCameraMode = joystickKey(bindings.cameraMode(), JoystickAction.CAMERA_MODE);
        this.KeyFreeLook = joystickKey(bindings.freeLook(), JoystickAction.FREE_LOOK);
        this.KeyGUI = new InputKey(bindings.gui());
        this.KeyGearUpDown = joystickKey(bindings.gearUpDown(), JoystickAction.GEAR);
        this.KeyPutToRack = new InputKey(bindings.putToRack());
        this.KeyDownFromRack = new InputKey(bindings.downFromRack());
        this.KeyBrake = joystickKey(bindings.switchHovering(), JoystickAction.HOVER);
        this.KeyReloadWeapon = joystickKey(bindings.reloadWeapon(), JoystickAction.RELOAD);
        this.KeyChaff = joystickKey(bindings.chaff(), JoystickAction.CHAFF);
        this.KeyAPS = new InputKey(bindings.aps());
        this.KeyECMJammer = new InputKey(bindings.ecmJammer());
        this.KeyRadarSwitch = new InputKey(bindings.radarSwitch());
        this.KeyAirburstDistReset = new InputKey(bindings.airburstDistReset());
        this.KeyOpenGPSPanel = new InputKey(bindings.openGpsPanel());
    }

    /** A key that the joystick button bound to {@code action} also presses. */
    protected final InputKey joystickKey(int code, JoystickAction action) {
        PilotControls pilot = this.controls.pilot();
        return new InputKey(code, () -> pilot.pressed(action));
    }

    /** Hook for controls that also work while a screen is open; none by default. */
    protected void commonPlayerControlInGUI(Player player, AircraftEntity ac, boolean isPilot,
                                            ControlInputBuilder pc) {
    }

    /**
     * Fills {@code pc} from the keys pressed this tick.
     *
     * @return whether the packet should be sent
     */
    public boolean commonPlayerControl(Player player, AircraftEntity ac, boolean isPilot, ControlInputBuilder pc) {
        updateRwr(ac);
        if (isPilot && ac.aim().supportsDetachedTurret() && ac.aim().isDetachedActive()) {
            pc.setDetachedWeaponAim(true);
            pc.setDetachedWeaponAimYaw(ac.aim().detachedYaw());
            pc.setDetachedWeaponAimPitch(ac.aim().detachedPitch());
        }

        boolean send = false;
        if (isPilot && shouldSendDetachedAimSync(ac)) {
            pc.setDetachedWeaponAim(ac.aim().isDetachedActive());
            pc.setDetachedWeaponAimYaw(ac.aim().detachedYaw());
            pc.setDetachedWeaponAimPitch(ac.aim().detachedPitch());
            send = true;
        }

        send |= handleCameraAndModes(player, ac, pc);
        send |= handleSeatControls();

        if (isPilot) {
            send |= handlePilotActions(player, ac, pc);
            send |= handleMovementAndBraking(ac, pc);
        }

        send |= handleCombatSystems(player, ac, pc);

        return send || player.tickCount % KEEP_ALIVE_TICKS == 0;
    }

    /** Whether the detached turret aim changed since it was last sent ({@link #recordDetachedAimSync}). */
    protected boolean shouldSendDetachedAimSync(AircraftEntity ac) {
        if (!ac.aim().supportsDetachedTurret()) {
            return false;
        }

        int entityId = ac.getId();
        boolean active = ac.aim().isDetachedActive();
        float yaw = ac.aim().detachedYaw();
        float pitch = ac.aim().detachedPitch();
        return entityId != lastDetachedAimSyncEntityId ||
                active != lastDetachedAimSyncActive ||
                Float.compare(yaw, lastDetachedAimSyncYaw) != 0 ||
                Float.compare(pitch, lastDetachedAimSyncPitch) != 0;
    }

    protected void recordDetachedAimSync(AircraftEntity ac) {
        lastDetachedAimSyncEntityId = ac.getId();
        lastDetachedAimSyncActive = ac.aim().isDetachedActive();
        lastDetachedAimSyncYaw = ac.aim().detachedYaw();
        lastDetachedAimSyncPitch = ac.aim().detachedPitch();
    }

    private boolean handleCameraAndModes(Player player, AircraftEntity ac, ControlInputBuilder pc) {
        if (KeyRadarSwitch.isKeyDown()) {
            if (ac.spec() != null && ac.spec().file().features().radar().enabled()) {
                boolean nextState = !ac.radar().isOn();
                pc.setSwitchRadar((byte) (nextState ? 1 : 2));
                ac.radar().predictOn(nextState);
                playSound(nextState ? "radar_on" : "radar_off");
                return true;
            }
            playSoundNG();
            return false;
        }

        if (!KeyCameraMode.isKeyDown()) {
            return false;
        }

        if (InputKey.isKeyDown(McheliConfig.keys().freeLook())) {
            pc.setSwitchGunnerStatus(true);
            playSoundOK();
            return true;
        }

        if (ac.searchLight().isFitted() && ac.searchLight().canSwitch(player)) {
            pc.setSwitchSearchLight(true);
            playSoundOK();
            return true;
        }

        int beforeMode = ac.cameras().mode(player);
        ac.cameras().switchMode(player);
        int mode = ac.cameras().mode(player);
        if (mode != beforeMode) {
            pc.setSwitchCameraMode(ControlInput.CameraMode.values()[mode]);
            playSoundOK();
            return true;
        }

        playSoundNG();
        return false;
    }

    /** Free look + GUI key moves to the next seat, free look + extra key to the previous one. */
    protected boolean handleSeatControls() {
        if (!InputKey.isKeyDown(McheliConfig.keys().freeLook())) return false;
        boolean next = KeyGUI.isKeyDown();
        if (!next && !KeyExtra.isKeyDown()) {
            return false;
        }
        Network.sendToServer(new SwitchSeatMessage(next));
        return true;
    }

    private boolean handlePilotActions(Player player, AircraftEntity ac, ControlInputBuilder pc) {
        boolean send = false;

        if (KeyUnmount.isKeyDown()) {
            pc.setUnmount(ControlInput.UnmountAction.UNMOUNT_SELF);
            send = true;
        }

        if (KeyPutToRack.isKeyDown()) {
            ac.racks().tickRideRackStatus();
            if (ac.racks().canRideRack()) {
                pc.setPutDownRack(ControlInput.RackAction.RIDE);
            } else if (ac.racks().canLoadRack()) {
                pc.setPutDownRack(ControlInput.RackAction.MOUNT);
            }
            send = true;
        } else if (KeyDownFromRack.isKeyDown()) {
            if (ac.getVehicle() != null) {
                pc.setUnmount(ControlInput.UnmountAction.UNMOUNT_AIRCRAFT);
            } else if (ac.racks().canUnloadRack()) {
                pc.setPutDownRack(ControlInput.RackAction.UNMOUNT);
            }
            send = true;
        }

        if (KeyGearUpDown.isKeyDown() && Objects.requireNonNull(ac.spec()).parts().haveLandingGear()) {
            if (ac.parts().canFoldLandingGear()) {
                pc.setSwitchGear(ControlInput.GearSwitch.FOLD);
            } else if (ac.parts().canUnfoldLandingGear()) {
                pc.setSwitchGear(ControlInput.GearSwitch.UNFOLD);
            }
            send = true;
        }

        if (KeyFreeLook.isKeyDown() && ac.modes().canSwitchFreeLook()) {
            pc.setSwitchFreeLook((byte) (ac.modes().isFreeLook() ? 2 : 1));
            send = true;
        }

        if (KeyGUI.isKeyDown()) {
            pc.setOpenGui(true);
            send = true;
        }

        return send;
    }

    private boolean handleMovementAndBraking(AircraftEntity ac, ControlInputBuilder pc) {
        if (ac.ropes().isRepelling()) {
            pc.setThrottleDown(ac.keys().holdThrottleDown(false));
            pc.setThrottleUp(ac.keys().holdThrottleUp(false));
            pc.setMoveRight(ac.keys().holdMoveRight(false));
            pc.setMoveLeft(ac.keys().holdMoveLeft(false));
            return false;
        }

        if (ac.keys().hasBrake() && KeyBrake.isKeyPress()) {
            pc.setThrottleDown(ac.keys().holdThrottleDown(false));
            pc.setThrottleUp(ac.keys().holdThrottleUp(false));

            double distSq = ac.distanceToSqr(ac.xo, ac.getY(), ac.zo);
            if (ac.engine().throttle() <= 0.03 && distSq < 0.01) {
                pc.setMoveRight(ac.keys().holdMoveRight(false));
                pc.setMoveLeft(ac.keys().holdMoveLeft(false));
            }
            pc.setUseBrake(true);
            return KeyBrake.isKeyDown();
        }

        boolean inputChanged = changed(KeyUp) || changed(KeyDown) || changed(KeyRight) || changed(KeyLeft);

        pc.setThrottleDown(ac.keys().holdThrottleDown(KeyDown.isKeyPress()));
        pc.setThrottleUp(ac.keys().holdThrottleUp(KeyUp.isKeyPress()));
        pc.setMoveRight(ac.keys().holdMoveRight(KeyRight.isKeyPress()));
        pc.setMoveLeft(ac.keys().holdMoveLeft(KeyLeft.isKeyPress()));

        return inputChanged || KeyBrake.isKeyUp();
    }

    private static boolean changed(InputKey key) {
        return key.isKeyDown() || key.isKeyUp();
    }

    private boolean handleCombatSystems(Player player, AircraftEntity ac, ControlInputBuilder pc) {
        if (ac.isDestroyed()) {
            return false;
        }
        // Countermeasures belong to the pilot and the first gunner.
        boolean send = ac.seats().seatIdOf(player) <= 1 && handleCountermeasures(ac, pc);
        send |= handleWeapons(player, ac, pc);
        return send;
    }

    /** Flares, chaff, active protection and the ECM jammer; each refuses with a beep when it cannot run. */
    private boolean handleCountermeasures(AircraftEntity ac, ControlInputBuilder pc) {
        boolean send = false;
        if (KeyFlare.isKeyDown()) {
            if (ac.countermeasures().canUseFlare() && ac.countermeasures().useFlare(ac.countermeasures().currentFlareType())) {
                pc.setUseFlareType((byte) ac.countermeasures().currentFlareType());
                ac.countermeasures().nextFlareType();
                send = true;
            } else {
                playSoundNG();
            }
        }

        if (KeyChaff.isKeyDown()) {
            if (ac.countermeasures().canUseChaff() && ac.countermeasures().useChaff()) {
                pc.setUseChaff(true);
                send = true;
            } else {
                playSoundNG();
            }
        }

        if (KeyAPS.isKeyDown()) {
            if (ac.countermeasures().canUseAPS() && ac.countermeasures().useAPS()) {
                pc.setUseAPS(true);
                send = true;
            } else {
                playSoundNG();
            }
        }

        if (KeyECMJammer.isKeyDown()) {
            if (ac.spec() != null && ac.spec().file().features().ecm().enabled() && ac.countermeasures().useECMJammer()) {
                pc.setUseECMJammer(true);
                send = true;
            } else {
                playSoundNG();
            }
        }
        return send;
    }

    /** Reloading, firing and switching weapons, the airburst distance and the GPS panel. */
    private boolean handleWeapons(Player player, AircraftEntity ac, ControlInputBuilder pc) {
        boolean send = false;
        if (!ac.ammoSupply().isReloading()) {
            if (KeyReloadWeapon.isKeyDown()) {
                pc.setReload(true);
                send = true;
            }

            int wheel = this.controls.weaponWheel().pending();
            if (wheel == 0 && !KeySwitchWeapon1.isKeyDown() && !KeySwitchWeapon2.isKeyDown()) {
                if (KeySwWeaponMode.isKeyDown()) {
                    ac.weapons().current(player).switchMode();
                } else if (KeyUseWeapon.isKeyPress() && ac.weapons().prepareFire(player)) {
                    var weapon = ac.weapons().current(player);
                    pc.setUseWeapon(true);
                    pc.setFireRequest(weapon.lastRequest());
                    pc.setUseWeaponPos(ac.xo, ac.yo, ac.zo);
                    pc.setUseWeaponUserYaw(ac.aim().userYaw(player));
                    pc.setUseWeaponUserPitch(ac.aim().userPitch(player));
                    send = true;
                }
            } else if (wheel != 0 || KeySwitchWeapon1.isKeyDown() || KeySwitchWeapon2.isKeyDown()) {
                pc.setSwitchWeapon((byte) ac.weapons().nextId(player, wheel > 0 ? -1 : 1));
                this.controls.weaponWheel().consume();
                ac.weapons().select(player, pc.switchWeapon());
                send = true;
            }
        }

        if (KeyAirburstDistReset.isKeyDown()) {
            WeaponGroup ws = ac.weapons().current(player);
            if (ws != null && ws.spec() != null && ws.spec().explosion().canAirburst()) {
                ac.weapons().aiming().resetAirburstDistance(player, ws.currentMount());
            }
        }

        if (KeyOpenGPSPanel.isKeyDown() && this.mc.screen == null) {
            this.mc.setScreen(new GpsInputScreen(player));
        }

        return send;
    }

    /**
     * Radar warning tones: fast for a lock or an active missile, slow for a search radar. A set that tells
     * seekers apart sounds a missile higher for a heat seeker and lower for a radar missile.
     */
    protected void updateRwr(AircraftEntity ac) {
        if (ac == null || ac.spec() == null || !ac.spec().file().features().rwr().enabled()) return;

        boolean beingLocked = false;
        boolean beingScanned = false;
        for (RWRThreatEvent threat : RWRThreatClientTracker.getEvents(ac.getId())) {
            if (threat.threatMode() == RWRThreatEvent.MODE_STT || threat.threatMode() == RWRThreatEvent.MODE_MSL_ACTIVE) {
                beingLocked = true;
            } else if (threat.threatMode() == RWRThreatEvent.MODE_SEARCH) {
                beingScanned = true;
            }
        }

        long now = ac.level().getGameTime();
        if (beingLocked) {
            if (now - lastRwrLockSoundTick >= RWR_LOCK_TONE_INTERVAL) {
                UiSounds.play("locked", 1.0F, lockTonePitch(IncomingMissileReader.read(ac)));
                lastRwrLockSoundTick = now;
            }
        } else if (beingScanned && now - lastRwrScanSoundTick >= RWR_SEARCH_TONE_INTERVAL) {
            // McHeli played "alert_radar", which never shipped; a high alert chirp stands in for it.
            UiSounds.play("alert", 0.6F, RWR_SEARCH_TONE_PITCH);
            lastRwrScanSoundTick = now;
        }
    }

    /** Higher than the missile alert's 1.0 and 0.75, so a search reads differently from a launch. */
    private static final float RWR_SEARCH_TONE_PITCH = 1.6F;

    /** The lock tone's pitch: a radar missile takes precedence, as chaff is the rarer decoy to remember. */
    private static float lockTonePitch(IncomingMissiles incoming) {
        if (incoming.radar() > 0) {
            return RWR_RADAR_MISSILE_PITCH;
        }
        return incoming.heat() > 0 ? RWR_HEAT_MISSILE_PITCH : 1.0F;
    }
}
