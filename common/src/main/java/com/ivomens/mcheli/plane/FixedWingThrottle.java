package com.ivomens.mcheli.plane;

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
 * The throttle of planes and ships: forward and limited reverse thrust from the keys, target drones
 * flying themselves, and spooling down with nobody at the controls. The server publishes it; a
 * client eases towards it for planes its own player does not fly.
 */
@TickOrder(phase = TickPhase.PHYSICS)
final class FixedWingThrottle implements VehicleComponent {

    /** Spool-down per tick, times the definition's throttle step, while nobody flies the plane. */
    private static final double IDLE_SPOOL_DOWN = 0.0025;
    /** The reverse lever goes no further than this. */
    private static final float MAX_REVERSE_THRUST = 0.6F;

    private final AbstractPlaneEntity plane;
    private final EngineComponent engine;
    private final AircraftPhysics physics;
    private final FlightModesComponent modes;
    private final ControlKeysComponent keys;
    private final PartsComponent parts;
    private final PilotPreferences pilot;
    private final FoldingWings wings;

    FixedWingThrottle(AbstractPlaneEntity plane, EngineComponent engine, AircraftPhysics physics,
            FlightModesComponent modes, ControlKeysComponent keys, PartsComponent parts, PilotPreferences pilot,
            FoldingWings wings) {
        this.plane = plane;
        this.engine = engine;
        this.physics = physics;
        this.modes = modes;
        this.keys = keys;
        this.parts = parts;
        this.pilot = pilot;
        this.wings = wings;
    }

    @Override
    public void serverTick(TickContext ctx) {
        ThrottleLever lever = fly();
        this.engine.publishThrottle(lever.throttle());
        this.physics.commit(lever);
    }

    @Override
    public void clientTick(TickContext ctx) {
        ThrottleLever lever = fly();
        if (!LocalClient.get().isLocalPlayer(this.plane.getRiddenByEntity())) {
            // other players' planes ease towards the synced throttle
            ThrottleModel.followSynced(lever, this.engine.syncedThrottle());
        }
        this.physics.commit(lever);
    }

    private ThrottleLever fly() {
        AbstractPlaneEntity p = this.plane;
        if (this.modes.isGunnerMode() && !this.engine.canRun()) {
            this.modes.switchGunnerMode(false);
        }
        ThrottleLever lever = this.physics.throttleLever();
        ReversibleThrottle.releaseReverse(lever);
        ControlInput input = this.physics.controls(this.modes.isGunnerMode());
        SimSettings settings = this.physics.settings()
                .withAutoThrottleDown(this.pilot.pilotSettings().planeAutoThrottleDown());
        Entity rider = p.getRiddenByEntity();
        boolean canFly = this.engine.canRun() && !p.isDestroyed();
        if (canFly && rider != null && !rider.isRemoved() && this.parts.isCanopyClosed() && this.wings.canFly()) {
            ReversibleThrottle.pilot(lever, input, setup(), settings);
        } else if (canFly && p.isTargetDrone()) {
            // target drones fly with the throttle key held down
            this.keys.holdThrottleUp(true);
            ReversibleThrottle.pilot(lever, input.withThrottleUp(), setup(), settings);
        } else {
            ThrottleModel.idleDown(lever, IDLE_SPOOL_DOWN * p.spec().file().features().throttleUpDown());
        }
        ThrottleModel.clampAtZero(lever);
        return lever;
    }

    private ReversibleThrottle.Setup setup() {
        VehicleSpec info = this.plane.spec();
        return new ReversibleThrottle.Setup(info.file().features().throttleUpDown(), carrierScaling(info),
                info.file().features().canReverse(), 1.0F,
                MAX_REVERSE_THRUST, info.file().physics().pivotTurnThrottle());
    }

    /** On a carrier the throttle follows the carrier's speed; planes group the carrier terms first. */
    private ReversibleThrottle.CarrierScaling carrierScaling(VehicleSpec info) {
        Entity carrier = this.plane.getVehicle();
        if (carrier == null) {
            return ReversibleThrottle.CarrierScaling.NONE;
        }
        float carrierSpeed = (float) carrier.getDeltaMovement().horizontalDistance();
        float onCarrier = info.file().features().throttleUpDownOnEntity();
        return step -> step * (carrierSpeed * onCarrier);
    }
}
