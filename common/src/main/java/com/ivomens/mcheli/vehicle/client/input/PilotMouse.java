package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.RiderLook;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.config.HandlingSettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.core.input.StickFilter;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.AbstractPlaneEntity;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.util.math.Rotations;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The mouse of a player who drives the vehicle: it steers through the vehicle's attitude control, aims a
 * turret that aims on its own, or is locked to the vehicle's view. For planes and helicopters with the
 * quaternion camera it also moves the free look head offset.
 */
final class PilotMouse {

    /** Vanilla's degrees per mouse unit in {@code Entity.turn}. */
    private static final float TURN_DEGREES_PER_UNIT = 0.15F;

    private final MouseStick stick;
    private final DetachedTurretAim turretAim;
    private final RiderView view;
    private final RenderFramePlacement placement;
    private final PilotControls controls;
    private final StickKeyReader stickKeys;
    /** Whether the joystick moved the stick last frame. */
    private boolean joystickSteered;
    /** Free look head offset relative to the airframe, in degrees. */
    private float freeLookYaw;
    private float freeLookPitch;

    PilotMouse(MouseStick stick, DetachedTurretAim turretAim, RiderView view, RenderFramePlacement placement,
               PilotControls controls, StickKeyReader stickKeys) {
        this.stick = stick;
        this.turretAim = turretAim;
        this.view = view;
        this.placement = placement;
        this.controls = controls;
        this.stickKeys = stickKeys;
    }

    /** A helicopter, plane, ship or tank, flown directly or through a UAV station. */
    void steerAircraft(AircraftEntity aircraft, Player player, boolean stickMode, float partialTicks,
                       float deltaSeconds) {
        SeatInfo seatInfo = aircraft.seats().info(player);
        boolean fixRot = false;
        float fixYaw = 0.0F;
        float fixPitch = 0.0F;
        if (seatInfo != null && seatInfo.fixRot && aircraft.modes().usesGunnerView(player) &&
                aircraft.modes().isGunnerLookMode(player)) {
            fixRot = true;
            fixYaw = seatInfo.fixYaw;
            fixPitch = seatInfo.fixPitch;
            this.stick.zero();
            this.turretAim.clear(aircraft);
        } else if (aircraft.isPilot(player)) {
            VehicleParts.CameraPosition cameraPosition = aircraft.cameras().positionForLocalPlayer();
            if (cameraPosition != null) {
                fixYaw = cameraPosition.yaw();
                fixPitch = cameraPosition.pitch();
            }
        }

        if (DetachedTurretAim.isUsed(aircraft, player)) {
            this.stick.turn(player);
            this.turretAim.slew(aircraft, player, deltaSeconds);
            holdTankLevel(aircraft, partialTicks, deltaSeconds, player, stickMode);
            return;
        }
        if (SeatMouse.locksView(aircraft, player)) {
            this.turretAim.clear(aircraft);
            RiderView.lockToVehicle(aircraft, player);
            holdTankLevel(aircraft, partialTicks, deltaSeconds, player, stickMode);
            return;
        }

        this.turretAim.clear(aircraft);
        steer(aircraft, player, new RiderLook(fixRot, fixYaw, fixPitch, this.stick.smoothedDeltaX(),
                this.stick.smoothedPitchDelta()), partialTicks, deltaSeconds);
        this.stick.dampUnlessHeld(stickMode);
        this.view.updateRoll(aircraft, player);
        updateFreeLookOffset(aircraft);
    }

    /** The view does not steer: a tank's hull keeps levelling itself, and the view follows the frame. */
    private void holdTankLevel(AircraftEntity aircraft, float partialTicks, float deltaSeconds, Player player,
                               boolean stickMode) {
        if (aircraft instanceof TankEntity) {
            aircraft.attitudeControl().advance(AttitudeInput.CENTRED, deltaSeconds);
        }
        this.placement.place(aircraft, partialTicks, player);
        this.stick.dampUnlessHeld(stickMode);
        this.view.updateRoll(aircraft, player);
    }

    /** A ground vehicle driven directly: it never rolls the view. */
    void steerGroundVehicle(GroundVehicleEntity vehicle, Player player, float partialTicks, float deltaSeconds) {
        if (SeatMouse.locksView(vehicle, player)) {
            this.turretAim.clear(vehicle);
            RiderView.lockToVehicle(vehicle, player);
            this.placement.place(vehicle, partialTicks, player);
            this.view.level();
            return;
        }

        if (DetachedTurretAim.isUsed(vehicle, player)) {
            this.stick.turn(player);
            this.turretAim.slew(vehicle, player, deltaSeconds);
            this.placement.place(vehicle, partialTicks, player);
        } else {
            this.turretAim.clear(vehicle);
            steer(vehicle, player, new RiderLook(false, 0.0F, 0.0F, this.stick.smoothedDeltaX(),
                    this.stick.smoothedPitchDelta()), partialTicks, deltaSeconds);
        }
        this.view.level();
    }

    /**
     * Hands the stick to the vehicle's attitude control, or turns the player if the vehicle has no
     * definition. The stick is the mouse's, with whatever the pilot's other controls add
     * ({@link PilotControls}); without any of them it is the mouse's alone.
     */
    private void steer(AircraftEntity aircraft, Player player, RiderLook look, float partialTicks,
                       float deltaSeconds) {
        if (aircraft.spec() == null) {
            this.stick.turn(player);
        } else {
            HandlingSettings handling = McheliConfig.handling();
            followJoystick();
            float[] mouse = {(float) this.stick.rawStickX(), (float) this.stick.rawStickY()};
            aircraft.attitudeControl().steer(player, look, this.controls.stick(aircraft, mouse,
                    aircraft.attitudeControl().freeLook(), this.stickKeys.read(handling), deltaSeconds, handling),
                    deltaSeconds);
            recentre(aircraft, handling, deltaSeconds);
        }
        this.placement.place(aircraft, partialTicks, player);
    }

    /**
     * The mouse's stick shows where the joystick holds its axes, so the HUD shows the joystick; when
     * the joystick lets go (unplugged, switched off) the stick is centred instead of staying deflected.
     */
    private void followJoystick() {
        JoystickInput joystick = this.controls.joystick();
        boolean steers = this.controls.joystickSteers();
        if (joystick.drivesRoll()) {
            this.stick.placeX(joystick.roll() * PilotControls.FULL_STICK);
        }
        if (joystick.drivesPitch()) {
            this.stick.placeY(joystick.pitch() * PilotControls.FULL_STICK);
        }
        if (this.joystickSteered && !steers) {
            this.stick.centreStick();
        }
        this.joystickSteered = steers;
    }

    /** The optional return to centre of a mouse stick the pilot leaves alone. */
    private void recentre(AircraftEntity aircraft, HandlingSettings handling, float deltaSeconds) {
        double rate = aircraft instanceof HelicopterEntity ? handling.centringHeli()
                : aircraft instanceof AbstractPlaneEntity ? handling.centringPlane() : 0.0;
        if (rate > 0.0 && !this.stick.moved() && !this.joystickSteered) {
            this.stick.scaleStick(StickFilter.recentre(rate, deltaSeconds));
        }
    }

    /**
     * Quaternion free look for planes and helicopters (experimental rotation only). The mouse moves a
     * yaw/pitch head offset relative to the airframe which the camera applies on top of the aircraft
     * orientation. The offset has no roll, so the horizon cannot tumble while panning, and pitch stops just
     * short of straight up to stay clear of the singularity.
     */
    private void updateFreeLookOffset(AircraftEntity ac) {
        if (!McheliConfig.flight().quaternionRotation()
                || (!(ac instanceof PlaneEntity) && !(ac instanceof HelicopterEntity))
                || !ac.modes().isFreeLook()) {
            // Entering free look again starts looking straight ahead.
            ac.cameras().setFreeLookOffset(null);
            this.freeLookYaw = 0.0F;
            this.freeLookPitch = 0.0F;
            return;
        }
        float yawDelta = (float) ((this.stick.deltaX() + this.stick.prevDeltaX()) / 2.0) * TURN_DEGREES_PER_UNIT;
        float pitchDelta = -(float) ((this.stick.deltaY() + this.stick.prevDeltaY()) / 2.0) * TURN_DEGREES_PER_UNIT;
        this.freeLookYaw = Mth.wrapDegrees(this.freeLookYaw + yawDelta);
        this.freeLookPitch = Mth.clamp(this.freeLookPitch + pitchDelta, -89.0F, 89.0F);
        ac.cameras().setFreeLookOffset(Rotations.worldOrientation(this.freeLookYaw, this.freeLookPitch, 0.0F));
    }
}
