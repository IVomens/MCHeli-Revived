package com.ivomens.mcheli.tank;

import com.ivomens.mcheli.core.sim.attitude.GroundAttitude;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.port.SimSettings;
import com.ivomens.mcheli.core.sim.propulsion.ReversibleThrottle;
import com.ivomens.mcheli.core.sim.propulsion.ThrottleLever;
import com.ivomens.mcheli.core.sim.propulsion.ThrottleModel;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.aircraft.AircraftPhysics;
import com.ivomens.mcheli.vehicle.component.PilotPreferences;
import com.ivomens.mcheli.vehicle.component.ControlKeysComponent;
import com.ivomens.mcheli.vehicle.component.EngineComponent;
import com.ivomens.mcheli.vehicle.component.FlightModesComponent;
import com.ivomens.mcheli.vehicle.component.PartsComponent;
import com.ivomens.mcheli.vehicle.component.TickOrder;
import com.ivomens.mcheli.vehicle.component.TickPhase;
import com.ivomens.mcheli.vehicle.component.TickContext;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;
import net.minecraft.world.entity.Entity;

/**
 * The tank's throttle: forward and reverse from the keys, the brake, and target drones driving
 * themselves. The server publishes it; a client eases towards it, and corrects its own driver's
 * copy now and then.
 */
@TickOrder(phase = TickPhase.PHYSICS)
final class TankThrottle implements VehicleComponent {

    /** Spool-down per tick, times the definition's throttle step, while nobody drives. */
    private static final double IDLE_SPOOL_DOWN = 0.0025;
    /** How often the driver's own client takes over the synced throttle. */
    private static final int DRIVER_RESYNC_INTERVAL = 200;

    private final TankEntity tank;
    private final EngineComponent engine;
    private final AircraftPhysics physics;
    private final FlightModesComponent modes;
    private final ControlKeysComponent keys;
    private final PartsComponent parts;
    private final PilotPreferences pilot;

    TankThrottle(TankEntity tank, EngineComponent engine, AircraftPhysics physics, FlightModesComponent modes,
            ControlKeysComponent keys, PartsComponent parts, PilotPreferences pilot) {
        this.tank = tank;
        this.engine = engine;
        this.physics = physics;
        this.modes = modes;
        this.keys = keys;
        this.parts = parts;
        this.pilot = pilot;
    }

    @Override
    public void serverTick(TickContext ctx) {
        ThrottleLever lever = drive();
        this.engine.publishThrottle(lever.throttle());
        this.physics.commit(lever);
    }

    @Override
    public void clientTick(TickContext ctx) {
        ThrottleLever lever = drive();
        if (!LocalClient.get().isLocalPlayer(this.tank.getRiddenByEntity())
                || ctx.age() % DRIVER_RESYNC_INTERVAL == 0) {
            ThrottleModel.followSynced(lever, this.engine.syncedThrottle());
        }
        this.physics.commit(lever);
    }

    private ThrottleLever drive() {
        TankEntity t = this.tank;
        if (this.modes.isGunnerMode() && !this.engine.canRun()) {
            this.modes.switchGunnerMode(false);
        }
        VehicleSpec info = t.spec();
        ThrottleLever lever = this.physics.throttleLever();
        ReversibleThrottle.releaseReverse(lever);
        if (this.keys.brake()) {
            ReversibleThrottle.brake(lever, info.file().features().throttleUpDown());
        }
        pilot(lever, info);
        this.physics.commit(lever);
        if (lever.reverseEngaged() && LocalClient.get().isLocalPlayer(t.getRiddenByEntity())) {
            steerWhileReversing();
        }
        ThrottleModel.clampAtZero(lever);
        return lever;
    }

    private void pilot(ThrottleLever lever, VehicleSpec info) {
        TankEntity t = this.tank;
        ControlInput input = this.physics.controls(this.modes.isGunnerMode());
        SimSettings settings = this.physics.settings()
                .withAutoThrottleDown(this.pilot.pilotSettings().tankAutoThrottleDown());
        Entity rider = t.getRiddenByEntity();
        boolean canDrive = this.engine.canRun() && !t.isDestroyed();
        if (canDrive && rider != null && !rider.isRemoved() && this.parts.isCanopyClosed()) {
            ReversibleThrottle.pilot(lever, input, setup(info), settings);
        } else if (canDrive && t.isTargetDrone()) {
            // target drones drive with the throttle key held down
            this.keys.holdThrottleUp(true);
            ReversibleThrottle.pilot(lever, input.withThrottleUp(), setup(info), settings);
        } else {
            ThrottleModel.idleDown(lever, IDLE_SPOOL_DOWN * info.file().features().throttleUpDown());
        }
    }

    /** Tanks have no pivot boost and no reverse limit; {@code throttleDownFactor} sets how fast reverse engages. */
    private ReversibleThrottle.Setup setup(VehicleSpec info) {
        return new ReversibleThrottle.Setup(info.file().features().throttleUpDown(), carrierScaling(info),
                info.file().features().canReverse(),
                info.file().throttleDownFactor(), Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
    }

    /** On a carrier the throttle follows the carrier's speed; tanks multiply left to right. */
    private ReversibleThrottle.CarrierScaling carrierScaling(VehicleSpec info) {
        Entity carrier = this.tank.getVehicle();
        if (carrier == null) {
            return ReversibleThrottle.CarrierScaling.NONE;
        }
        float carrierSpeed = (float) carrier.getDeltaMovement().horizontalDistance();
        float onCarrier = info.file().features().throttleUpDownOnEntity();
        return step -> step * carrierSpeed * onCarrier;
    }

    /**
     * Tracked vehicles can turn while backing up. The hull's heading is the driver's (see
     * {@code GroundAttitude}), so this runs on the driver's client and the attitude report carries it.
     */
    private void steerWhileReversing() {
        TankEntity t = this.tank;
        t.setYRot(GroundAttitude.steerWhileReversing(t.getYRot(), this.physics.controls(false), t.attitudeSetup(),
                t.attitudeControl().terrain()));
    }
}
