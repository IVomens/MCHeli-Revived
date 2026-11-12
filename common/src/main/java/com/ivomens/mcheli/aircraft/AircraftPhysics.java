package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.core.sim.attitude.RotationLimits;
import com.ivomens.mcheli.core.sim.attitude.StickHandling;
import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.model.HandlingProfile;
import com.ivomens.mcheli.core.sim.port.SimSettings;
import com.ivomens.mcheli.core.sim.port.StepContext;
import com.ivomens.mcheli.core.sim.port.TerrainProbe;
import com.ivomens.mcheli.core.sim.propulsion.CruiseSpeed;
import com.ivomens.mcheli.core.sim.propulsion.PropulsionState;
import com.ivomens.mcheli.core.sim.propulsion.ThrottleLever;
import com.ivomens.mcheli.core.sim.propulsion.ThrottleModel;
import com.ivomens.mcheli.vehicle.component.BodyComponent;
import com.ivomens.mcheli.vehicle.component.ControlKeysComponent;
import com.ivomens.mcheli.vehicle.component.DamageComponent;
import com.ivomens.mcheli.vehicle.component.EngineComponent;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;
import com.ivomens.mcheli.vehicle.world.EntitySimRandom;
import com.ivomens.mcheli.vehicle.world.LevelTerrainProbe;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Connects a vehicle entity to the {@code sim} models: reads the entity into model state, hands the
 * models the world through the ports, and writes the results back. It also owns the propulsion state
 * that only the physics uses: the cruise speed and the reverse lever. Definition values come from the
 * vehicle's {@code VehicleDefinition}, never from the parsed definition.
 */
public final class AircraftPhysics implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final EngineComponent engine;
    private final ControlKeysComponent keys;
    private final BodyComponent body;
    private final DamageComponent damage;
    private final Supplier<SimSettings> settings;
    private final TerrainProbe terrain;
    private final BooleanSupplier engineCrippled;
    private StepContext context;
    private double cruiseSpeed = CruiseSpeed.MINIMUM;
    private float reverseThrust;

    AircraftPhysics(AircraftEntity vehicle, EngineComponent engine, ControlKeysComponent keys, BodyComponent body,
            DamageComponent damage, Supplier<SimSettings> settings) {
        this.vehicle = vehicle;
        this.engine = engine;
        this.keys = keys;
        this.body = body;
        this.damage = damage;
        this.settings = settings;
        this.terrain = new LevelTerrainProbe(vehicle);
        this.engineCrippled = engine::isCrippled;
    }

    /** The settings that change handling, from the vehicle's settings snapshot; nothing is built per call. */
    public SimSettings settings() {
        return this.settings.get();
    }

    /** The definition's stick rates and rotation limits, for the stick-flown attitude models. */
    public StickHandling stickHandling() {
        VehicleSpec info = Objects.requireNonNull(this.vehicle.spec());
        RotationLimits limits = new RotationLimits(info.file().physics().rotationLimits().limited(),
                info.file().physics().rotationLimits().minPitch(), info.file().physics().rotationLimits().maxPitch(),
                info.file().physics().rotationLimits().minRoll(), info.file().physics().rotationLimits().maxRoll());
        return StickHandling.of(info.file().physics().mobility().yaw(), info.file().physics().mobility().pitch(),
                info.file().physics().mobility().roll(), limits);
    }

    public TerrainProbe terrain() {
        return this.terrain;
    }

    /** Terrain, collider and random numbers; the collider is the vehicle's own, made on first use. */
    public StepContext context() {
        if (this.context == null) {
            this.context = new StepContext(this.terrain, this.vehicle.createCollider(),
                    new EntitySimRandom(this.vehicle.getRandom()));
        }
        return this.context;
    }

    /** See {@link CruiseSpeed}: not saved, and only helicopters read it. */
    public double cruiseSpeed() {
        return this.cruiseSpeed;
    }

    public float reverseThrust() {
        return this.reverseThrust;
    }

    public BodyState body() {
        AircraftEntity v = this.vehicle;
        Vec3 motion = v.getDeltaMovement();
        return new BodyState(new Vec3d(motion.x, motion.y, motion.z), this.body.attitude(), v.onGround(), v.isInWater(),
                v.isDestroyed());
    }

    public PropulsionState propulsion(float nozzleAngle) {
        return new PropulsionState(this.engine.throttle(), this.reverseThrust, this.cruiseSpeed,
                nozzleAngle);
    }

    public ControlInput controls(boolean autopilot) {
        ControlKeysComponent keys = this.keys;
        return new ControlInput(keys.throttleUp(), keys.throttleDown(), keys.moveLeft(), keys.moveRight(), autopilot);
    }

    /** The vehicle's handling, typed for its model; built once per content load, not per tick. */
    public <H extends HandlingProfile> H handling(Class<H> type) {
        return type.cast(Objects.requireNonNull(this.vehicle.definition()).handling());
    }

    /**
     * Writes a finished step back. The attitude needs no writing: the collider already put the
     * vehicle at its final attitude before moving it, which is when the legacy code wrote it.
     */
    public void apply(MotionStep step) {
        Vec3d velocity = step.body().velocity();
        this.vehicle.setDeltaMovement(velocity.x(), velocity.y(), velocity.z());
        this.cruiseSpeed = step.propulsion().cruiseSpeed();
    }

    public ThrottleLever throttleLever() {
        return new ThrottleLever(this.engine.throttle(), this.reverseThrust, this.engineCrippled);
    }

    /** Writes the lever back; any positive setting during the step wakes a resting vehicle. */
    public void commit(ThrottleLever lever) {
        this.engine.commit(lever.throttle(), lever.wasPositive());
        this.reverseThrust = lever.reverseThrust();
    }

    /** Server: publishes the throttle to clients. */
    public void syncThrottle() {
        this.engine.publishThrottle(this.engine.throttle());
    }

    /** A burning wreck's engine dies down; every vehicle type shares this. */
    public void spoolDownWreck() {
        if (!this.vehicle.isDestroyed() || !(this.engine.throttle() > 0.0)) {
            return;
        }
        ThrottleLever lever = throttleLever();
        ThrottleModel.spoolDownWreck(lever, !this.terrain.isClearBelow(3, 2), this.damage.isExploded());
        commit(lever);
    }
}
