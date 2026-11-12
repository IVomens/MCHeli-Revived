package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.core.sim.attitude.AttitudeReport;
import com.ivomens.mcheli.core.sim.attitude.FixedStepClock;
import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiderLook;
import com.ivomens.mcheli.aircraft.net.AttitudeReportHandler;
import com.ivomens.mcheli.aircraft.net.AttitudeReportMessage;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.server.level.ServerPlayer;

/**
 * Plays the pilot's client for attitude: each tick it makes the calls
 * {@code MouseInputHandler.handleRenderTickPre} would make for the frames of one tick, then hands
 * the report to the server through {@link AttitudeReportMessage}, as {@code AircraftEntity.syncRotation}
 * does on the client. The server applies it at the start of the vehicle's tick.
 * <p>
 * There is no client entity here, so the calls run on the server entity. While they run, the
 * movement keys on the entity are the client's own (tank and car steering read them there).
 * Afterwards everything only the client copy would have
 * touched ({@code xRotO}, {@code yRotO}, {@code prevRotationRoll} and the keys) is put back before
 * the report is delivered, so the server sees what a real client sends.
 */
final class AttitudeDriver {

    /** One tick, as the frame time a 20 FPS client measures. */
    static final float FRAME_SECONDS = 0.05F;

    private AttitudeDriver() {
    }

    /**
     * The client the harness plays.
     *
     * @param perTick      render frames per game tick
     * @param frameSeconds the frame time the client measures
     * @param fixedSteps   whether {@code FixedStepAttitude} is on
     */
    record ClientFrames(String suffix, int perTick, float frameSeconds, boolean fixedSteps) {

        /** The legacy per-frame attitude at one frame per tick, which the original recordings use. */
        static final ClientFrames TWENTY_FPS = new ClientFrames("", 1, FRAME_SECONDS, false);
        /** Fixed substeps at 60 FPS: one 1/60 s substep per frame, three per tick. */
        static final ClientFrames SIXTY_FPS = new ClientFrames("_60fps", 3, FixedStepClock.STEP_SECONDS, true);
    }

    static void apply(AircraftEntity vehicle, ServerPlayer pilot, PilotInput input, ClientFrames frames) {
        // MouseInputHandler only steers a live vehicle the player rides directly
        if (pilot.getVehicle() != vehicle || !vehicle.attitudeControl().acceptsStick()) {
            return;
        }
        float pitchBefore = vehicle.xRotO;
        float yawBefore = vehicle.yRotO;
        float rollBefore = vehicle.rollAngle().previous();
        boolean[] serverKeys = {vehicle.keys().throttleUp(), vehicle.keys().throttleDown(), vehicle.keys().moveLeft(),
                vehicle.keys().moveRight()};

        holdClientKeys(vehicle, input);
        for (int frame = 0; frame < frames.perTick(); frame++) {
            turnLikeTheClient(vehicle, pilot, input, frames.frameSeconds());
        }

        AttitudeReport report = vehicle.attitudeControl().takeReport();
        vehicle.xRotO = pitchBefore;
        vehicle.yRotO = yawBefore;
        vehicle.rollAngle().setPrevious(rollBefore);
        vehicle.keys().holdThrottleUp(serverKeys[0]);
        vehicle.keys().holdThrottleDown(serverKeys[1]);
        vehicle.keys().holdMoveLeft(serverKeys[2]);
        vehicle.keys().holdMoveRight(serverKeys[3]);
        if (report != null) {
            new AttitudeReportHandler().handle(new AttitudeReportMessage(report), pilot);
        }
    }

    /** The key fields as AircraftClientTickHandler.handleMovementAndBraking sets them on the client entity. */
    private static void holdClientKeys(AircraftEntity vehicle, PilotInput input) {
        boolean brake = vehicle.keys().hasBrake() && input.holds(PilotInput.Key.BRAKE);
        vehicle.keys().holdThrottleUp(!brake && input.holds(PilotInput.Key.THROTTLE_UP));
        vehicle.keys().holdThrottleDown(!brake && input.holds(PilotInput.Key.THROTTLE_DOWN));
        vehicle.keys().holdMoveLeft(input.holds(PilotInput.Key.LEFT));
        vehicle.keys().holdMoveRight(input.holds(PilotInput.Key.RIGHT));
    }

    /** The branches of handleAircraftMouseControl and handleVehicleMouseControl, for the pilot. */
    private static void turnLikeTheClient(AircraftEntity vehicle, ServerPlayer pilot, PilotInput input,
                                          float frameSeconds) {
        boolean detachedAim = vehicle.spec() != null && vehicle.weapons().aiming().hasIndependentAim(pilot);
        if (vehicle instanceof GroundVehicleEntity) {
            if (!locksMountedView(vehicle, pilot, detachedAim)) {
                steer(vehicle, pilot, input, frameSeconds);
            }
            return;
        }
        if (vehicle instanceof TankEntity && (detachedAim || locksMountedView(vehicle, pilot, false))) {
            vehicle.attitudeControl().advance(AttitudeInput.CENTRED, frameSeconds);
            return;
        }
        steer(vehicle, pilot, input, frameSeconds);
    }

    /** MouseInputHandler.shouldLockMountedView: ground vehicles without free look keep the view fixed. */
    private static boolean locksMountedView(AircraftEntity vehicle, ServerPlayer pilot, boolean detachedAim) {
        if (vehicle.modes().canSwitchFreeLook() || vehicle.modes().usesGunnerView(pilot)) {
            return false;
        }
        if (vehicle instanceof GroundVehicleEntity) {
            return vehicle.weapons().currentId(pilot) < 0;
        }
        return !detachedAim;
    }

    /**
     * The stick is held still, so the mouse delta (which only turns the rider's head) is zero. A rudder
     * only comes from the handling assists; without one the mouse's own call is made.
     */
    private static void steer(AircraftEntity vehicle, ServerPlayer pilot, PilotInput input, float frameSeconds) {
        if (input.rudder() == 0.0F) {
            vehicle.attitudeControl().steerByMouse(pilot, RiderLook.STILL, input.stickX(), input.stickY(),
                    frameSeconds);
            return;
        }
        vehicle.attitudeControl().steer(pilot, RiderLook.STILL, new AttitudeInput(input.stickX(), input.stickY(),
                vehicle.attitudeControl().freeLook(), input.rudder()), frameSeconds);
    }
}
