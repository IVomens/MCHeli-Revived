package com.ivomens.mcheli.helicopter;

import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.model.KeyImpulse;
import com.ivomens.mcheli.core.sim.port.SimSettings;
import com.ivomens.mcheli.core.sim.propulsion.RotorThrottle;
import com.ivomens.mcheli.core.sim.propulsion.ThrottleLever;
import com.ivomens.mcheli.core.sim.propulsion.ThrottleModel;
import com.ivomens.mcheli.aircraft.AircraftPhysics;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.vehicle.component.PilotPreferences;
import com.ivomens.mcheli.vehicle.component.EngineComponent;
import com.ivomens.mcheli.vehicle.component.FlightModesComponent;
import com.ivomens.mcheli.vehicle.component.PartsComponent;
import com.ivomens.mcheli.vehicle.component.TickContext;
import com.ivomens.mcheli.vehicle.component.TickOrder;
import com.ivomens.mcheli.vehicle.component.TickPhase;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;
import net.minecraft.world.phys.Vec3;

/**
 * The collective and the strafe keys: the throttle follows the keys (or holds a hover), the side
 * keys push the helicopter sideways, and the rotor spins up with the throttle. The server publishes
 * the throttle; a client eases towards it for helicopters its own player does not fly.
 */
@TickOrder(phase = TickPhase.PHYSICS)
final class RotorControls implements VehicleComponent {

    /** Spool-down per tick while nobody flies the rotor. */
    private static final double IDLE_SPOOL_DOWN = 0.00125;
    /** Strafe push per tick, as a share of the definition's speed. */
    private static final float HOVER_STRAFE = 0.01F;
    private static final float FLIGHT_STRAFE = 0.02F;
    /** Push per tick when taxiing with folded blades. */
    private static final float FOLDED_TAXI_PUSH = 0.03F;

    private final HelicopterEntity heli;
    private final RotorBladesComponent blades;
    private final EngineComponent engine;
    private final FlightModesComponent modes;
    private final AircraftPhysics physics;
    private final PartsComponent parts;
    private final PilotPreferences pilot;

    RotorControls(HelicopterEntity heli, RotorBladesComponent blades, EngineComponent engine,
            FlightModesComponent modes, AircraftPhysics physics, PartsComponent parts, PilotPreferences pilot) {
        this.heli = heli;
        this.blades = blades;
        this.engine = engine;
        this.modes = modes;
        this.physics = physics;
        this.parts = parts;
        this.pilot = pilot;
    }

    @Override
    public void serverTick(TickContext ctx) {
        ThrottleLever lever = fly(true);
        this.engine.publishThrottle(lever.throttle());
        finish(lever);
    }

    @Override
    public void clientTick(TickContext ctx) {
        ThrottleLever lever = fly(false);
        if (!LocalClient.get().isLocalPlayer(this.heli.getRiddenByEntity())) {
            // other players' helicopters ease towards the synced throttle
            RotorThrottle.followSynced(lever, this.engine.syncedThrottle());
        }
        finish(lever);
    }

    private ThrottleLever fly(boolean onServer) {
        HelicopterEntity h = this.heli;
        if (this.modes.isHoveringMode() && !this.engine.canRun(true)) {
            this.modes.switchHoveringMode(false);
        }
        if (this.modes.isGunnerMode() && !this.engine.canRun()) {
            this.modes.switchGunnerMode(false);
        }

        ThrottleLever lever = this.physics.throttleLever();
        ControlInput input = this.physics.controls(this.modes.isHovering());
        if (canFly()) {
            pilot(lever, input, onServer);
        } else {
            ThrottleModel.idleDown(lever, IDLE_SPOOL_DOWN);
            if (onServer && this.blades.areStowed() && h.onGround() && !h.isDestroyed()) {
                // with folded blades the throttle keys roll the helicopter along the ground
                strafe(input.withoutSideKeys(), FOLDED_TAXI_PUSH);
            }
        }
        return lever;
    }

    private void finish(ThrottleLever lever) {
        ThrottleModel.clampAtZero(lever);
        this.physics.commit(lever);
        this.blades.spin();
    }

    private boolean canFly() {
        HelicopterEntity h = this.heli;
        return !h.isDestroyed() && (h.getRiddenByEntity() != null || this.modes.isHoveringMode())
                && this.blades.canLift() && this.parts.isCanopyClosed() && this.engine.canRun(true);
    }

    /** Strafing only moves the server's copy; clients receive the motion. */
    private void pilot(ThrottleLever lever, ControlInput input, boolean onServer) {
        HelicopterEntity h = this.heli;
        float speed = h.spec().speed();
        if (this.modes.isHovering()) {
            RotorThrottle.hover(lever);
            if (onServer) {
                strafe(input, HOVER_STRAFE * speed);
            }
            return;
        }
        // a client only settles the throttle of the helicopter its own player flies
        boolean mayAutoSettle = onServer || LocalClient.get().isLocalPlayer(h.getRiddenByEntity());
        SimSettings settings = this.physics.settings()
                .withAutoThrottleDown(mayAutoSettle && this.pilot.pilotSettings().heliAutoThrottleDown());
        RotorThrottle.pilot(lever, input, h.spec().file().features().throttleUpDown(), settings);
        if (onServer) {
            strafe(input.withoutThrottleKeys(), FLIGHT_STRAFE * speed);
        }
    }

    private void strafe(ControlInput keys, double strength) {
        HelicopterEntity h = this.heli;
        Vec3 motion = h.getDeltaMovement();
        Vec3d velocity = new Vec3d(motion.x, motion.y, motion.z);
        Vec3d pushed = KeyImpulse.push(velocity, h.getYRot(), keys, strength);
        if (pushed != velocity) {
            h.setDeltaMovement(pushed.x(), pushed.y(), pushed.z());
        }
    }
}
