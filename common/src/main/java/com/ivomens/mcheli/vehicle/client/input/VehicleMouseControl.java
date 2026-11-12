package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.ship.ShipEntity;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import com.ivomens.mcheli.vehicle.client.camera.VehicleCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * The mouse while riding: every rendered frame it reads the raw cursor movement and steers with it, as a
 * stick for aircraft, as free look or a turret aim in a seat. While it turns the player itself, vanilla
 * mouse look is switched off ({@link #isControllingMouse}).
 *
 * <p>Which of the three rides applies is decided here; the steering itself is in {@link PilotMouse}
 * (pilots of aircraft and ground vehicles) and {@link SeatMouse} (passenger seats). One per client;
 * only the render thread touches it.
 */
public final class VehicleMouseControl {

    private final Minecraft mc;
    private final MouseStick stick = new MouseStick();
    private final FrameClock clock = new FrameClock();
    private final DetachedTurretAim turretAim = new DetachedTurretAim();
    private final RiderView view;
    private final RenderFramePlacement placement;
    private final PilotMouse pilot;
    private final SeatMouse seat;
    private final Predicate<Entity> viewsThroughStandIn;
    private final PilotControls controls;
    private boolean wasRiding;
    private double lastCursorX;
    private double lastCursorY;
    private boolean cursorValid;
    /** True while MCHeli turned the player itself this frame; vanilla mouse look is then suppressed. */
    private boolean controllingMouse;

    /**
     * @param viewsThroughStandIn whether a ridden entity other than a vehicle (a laser designator, a UAV
     *                            station) shows its view through the camera stand-in
     * @param controls            the pilot's other controls: the joystick, the stick keys, the assists
     */
    public VehicleMouseControl(Minecraft mc, VehicleCamera camera, Predicate<Entity> viewsThroughStandIn,
                               PilotControls controls) {
        this.mc = mc;
        this.view = new RiderView(camera);
        this.placement = new RenderFramePlacement(camera);
        this.controls = controls;
        this.pilot = new PilotMouse(this.stick, this.turretAim, this.view, this.placement, controls,
                new StickKeyReader(mc));
        this.seat = new SeatMouse(this.stick, this.view, this.placement);
        this.viewsThroughStandIn = viewsThroughStandIn;
    }

    /** Whether vanilla mouse look must be suppressed because MCHeli drives the rotation. */
    public boolean isControllingMouse() {
        return this.controllingMouse;
    }

    /** The mouse read as a stick; the HUD shows its deflection. */
    public MouseStick stick() {
        return this.stick;
    }

    /** One rendered frame, before the level renders. */
    public void onFrame(Player player, float partialTicks) {
        this.controllingMouse = false;
        AircraftEntity aircraft = controlledAircraft(player);
        GroundVehicleEntity vehicle = player.getVehicle() instanceof GroundVehicleEntity ground ? ground : null;
        boolean stickMode = isStickMode(aircraft);

        this.controls.pollJoystick(McheliConfig.joystick());
        float deltaSeconds = this.clock.nextFrame();
        if (this.clock.skipped()) {
            this.cursorValid = false;
        }

        if (aircraft != null && aircraft.attitudeControl().acceptsStick()) {
            boardOnce(aircraft, player);
            readMouse(stickMode, partialTicks);
            this.pilot.steerAircraft(aircraft, player, stickMode, partialTicks, deltaSeconds);
        } else if (vehicle != null && vehicle.attitudeControl().acceptsStick()) {
            boardOnce(vehicle, player);
            readMouse(false, partialTicks);
            this.pilot.steerGroundVehicle(vehicle, player, partialTicks, deltaSeconds);
        } else {
            if (vehicle != null) {
                this.placement.place(vehicle, partialTicks, player);
            }
            seatOrIdle(player, stickMode, partialTicks);
        }

        if (aircraft != null) {
            RiderView.updateRiderLook(aircraft, player);
        } else if (vehicle != null) {
            RiderView.updateRiderLook(vehicle, player);
        }
        AircraftEntity viewed = aircraft != null ? aircraft : vehicle;
        if (viewed != null || this.viewsThroughStandIn.test(player.getVehicle())) {
            this.view.turnViewDummy(viewed, player);
        }
        this.lastCursorX = this.mc.mouseHandler.xpos();
        this.lastCursorY = this.mc.mouseHandler.ypos();
        this.cursorValid = this.mc.mouseHandler.isMouseGrabbed() && this.mc.screen == null;
    }

    private void boardOnce(AircraftEntity vehicle, Player player) {
        if (!this.wasRiding) {
            vehicle.pilotBoarding().boarded(player);
        }
        this.wasRiding = true;
    }

    private void seatOrIdle(Player player, boolean stickMode, float partialTicks) {
        AircraftEntity parent = this.seat.parentOf(player);
        if (parent != null) {
            readMouse(stickMode, partialTicks);
            this.seat.steer(parent, player, partialTicks);
        } else if (this.wasRiding) {
            this.view.level();
            this.wasRiding = false;
            this.stick.centreStick();
            this.turretAim.reset();
        }
    }

    /** Reads the cursor movement since the last frame into the stick; MCHeli now turns the player. */
    private void readMouse(boolean stickMode, float partialTicks) {
        this.controllingMouse = true;
        double rawX = this.cursorValid ? this.mc.mouseHandler.xpos() - this.lastCursorX : 0.0;
        double rawY = this.cursorValid ? -(this.mc.mouseHandler.ypos() - this.lastCursorY) : 0.0;
        this.stick.read(this.mc, rawX, rawY, stickMode, partialTicks, RiddenVehicle.of(this.mc.player) != null);
    }

    private static boolean isStickMode(@Nullable AircraftEntity ac) {
        return switch (ac) {
            case PlaneEntity p -> McheliConfig.controls().stickModePlane();
            case HelicopterEntity h -> McheliConfig.controls().stickModeHeli();
            case null, default -> false;
        };
    }

    /** The aircraft the player flies directly or through a UAV station; ground vehicles are not included. */
    @Nullable
    private static AircraftEntity controlledAircraft(Player player) {
        return switch (player.getVehicle()) {
            case HelicopterEntity heli -> heli;
            case PlaneEntity plane -> plane;
            case ShipEntity ship -> ship;
            case TankEntity tank -> tank;
            case IUavStation uav -> uav.getControlled();
            case null, default -> null;
        };
    }
}
