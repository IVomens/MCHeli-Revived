package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.core.sim.attitude.AttitudeContext;
import com.ivomens.mcheli.core.sim.attitude.AttitudeModel;
import com.ivomens.mcheli.core.sim.attitude.AttitudeReport;
import com.ivomens.mcheli.core.sim.attitude.AttitudeState;
import com.ivomens.mcheli.core.sim.attitude.AttitudeTerrain;
import com.ivomens.mcheli.core.sim.attitude.FixedStepClock;
import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Orientation;
import com.ivomens.mcheli.aircraft.net.AttitudeReportMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.util.EntityIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * Owns a vehicle's attitude memory and runs its {@link AttitudeModel} where the pilot is: on the
 * pilot's client, which reports the result to the server.
 * <p>
 * {@link #step} runs the model once on the vehicle's current attitude with the given step length.
 * That is the legacy per-frame mode, and the golden harness runs it once per tick at 0.05 s.
 * {@link #frame} runs fixed {@link FixedStepClock#STEP_SECONDS} substeps instead and draws the
 * airframe between the last two, so the handling no longer depends on the frame rate.
 *
 * @param <S> the model's setup
 * @param <M> the model's memory
 */
public final class VehicleAttitude<S, M> {

    /** The stick does not turn a vehicle in its first ticks, while it lands on its wheels. */
    private static final int SETTLE_IN_TICKS = 30;

    private final AircraftEntity vehicle;
    private final AttitudeModel<S, M> model;
    private final Supplier<S> setup;
    private final AttitudeInbox inbox = new AttitudeInbox();
    private BiConsumer<Entity, RiderLook> riderTurn;
    private DoubleConsumer afterFrame = frameSeconds -> {
    };
    private M memory;
    /** The quaternion mode's kept orientation; see {@link AttitudeState#orientation()}. */
    private Orientation orientation;
    /** One sample per tick: the vehicle only moves in its tick. */
    private AttitudeTerrain terrain;
    private FixedSteps<M> fixedSteps;
    private boolean unreported;
    private int sequence;

    public VehicleAttitude(AircraftEntity vehicle, AttitudeModel<S, M> model, Supplier<S> setup) {
        this.vehicle = vehicle;
        this.model = model;
        this.setup = setup;
        this.memory = model.initialMemory();
        this.riderTurn = (rider, look) -> look.turnWithAirframe(vehicle, rider);
    }

    /** How the rider's view follows the stick; by default it turns with the airframe. */
    public VehicleAttitude<S, M> turnRiderWith(BiConsumer<Entity, RiderLook> turn) {
        this.riderTurn = turn;
        return this;
    }

    /** Runs after every frame the pilot flies, with the frame's length in seconds. */
    public VehicleAttitude<S, M> afterEachFrame(DoubleConsumer listener) {
        this.afterFrame = listener;
        return this;
    }

    /** Someone flies the vehicle and it is still in one piece. */
    public boolean acceptsStick() {
        AircraftEntity v = this.vehicle;
        return !v.isRemoved() && v.getRiddenByEntity() != null && !v.isDestroyed();
    }

    /**
     * One frame of the pilot's mouse: turns the airframe by the stick, then the rider's view with it.
     * Runs on the piloting client, which reports the result to the server.
     */
    public void steerByMouse(Entity rider, RiderLook look, float stickX, float stickY, float frameSeconds) {
        steer(rider, look, new AttitudeInput(stickX, stickY, this.vehicle.modes().isFreeLook()), frameSeconds);
    }

    /** The vehicle's free look state, which centres the mouse's stick while the pilot looks around. */
    public boolean freeLook() {
        return this.vehicle.modes().isFreeLook();
    }

    /**
     * One frame of a stick from any source (the mouse, a joystick, the stick keys, the assists): turns
     * the airframe by it, then the rider's view with it.
     */
    public void steer(Entity rider, RiderLook look, AttitudeInput stick, float frameSeconds) {
        advance(stick, frameSeconds);
        this.riderTurn.accept(rider, look);
    }

    /**
     * Turns the airframe by one frame without touching the rider's view: at fixed substeps, or once
     * with the frame time when {@code FixedStepAttitude} is off.
     */
    public void advance(AttitudeInput stick, float frameSeconds) {
        AircraftEntity v = this.vehicle;
        ControlInput keys = v.physics().controls(false);
        if (v.settings().client().fixedStepAttitude()) {
            frame(stick, keys, frameSeconds);
        } else {
            step(stick, keys, frameSeconds);
        }
        // the pilot's own vehicle is drawn where the attitude left it, not between the last two ticks
        v.rollAngle().setPrevious(v.roll());
        v.xRotO = v.getXRot();
        if (v.getVehicle() == null) {
            v.yRotO = v.getYRot();
        }
        this.afterFrame.accept(frameSeconds);
    }

    /** Server: queues a pilot's report for the start of the next tick. */
    public void receiveReport(AttitudeReport report, ServerPlayer sender) {
        this.inbox.offer(report, sender.getId());
    }

    /** Client: sends the pilot's attitude to the server when it changed. */
    public void reportToServer() {
        if (this.vehicle.getRiddenByEntity() == null) {
            return;
        }
        AttitudeReport report = takeReport();
        if (report != null) {
            Network.sendToServer(new AttitudeReportMessage(report));
        }
    }

    /** Recoil rocks the hull once per tick here; the pilot's own client rocks it every frame instead. */
    public void rockWithRecoil() {
        AircraftEntity v = this.vehicle;
        if (!EntityIds.same(LocalClient.get().player(), v.getRiddenByEntity())) {
            Attitude rocked = v.weapons().recoil().apply(v.body().attitude(), 1.0F);
            v.setXRot(rocked.pitch());
            v.rollAngle().set(rocked.roll());
        }
    }

    /** The legacy mode: one step of {@code dtSeconds} from the vehicle's current attitude. */
    public void step(AttitudeInput mouse, ControlInput keys, float dtSeconds) {
        this.fixedSteps = null;
        Attitude before = this.vehicle.body().attitude();
        AttitudeState<M> after = this.model.integrate(new AttitudeState<>(before, this.orientation, this.memory),
                mouse, keys, this.setup.get(), context(), dtSeconds);
        this.memory = after.memory();
        this.orientation = after.orientation();
        show(after.attitude());
        noteTurn(before, this.vehicle.body().attitude());
    }

    /** One render frame in fixed-step mode: runs the whole substeps the frame completes. */
    public void frame(AttitudeInput mouse, ControlInput keys, float frameSeconds) {
        if (this.fixedSteps == null) {
            this.fixedSteps = new FixedSteps<>(new AttitudeState<>(this.vehicle.body().attitude(), this.orientation,
                    this.memory));
        }
        FixedSteps<M> steps = this.fixedSteps;
        steps.follow(this.vehicle.body().attitude());
        FixedStepClock.Frame frame = steps.advance(frameSeconds);
        S stepSetup = this.setup.get();
        AttitudeContext stepContext = context();
        for (int i = 0; i < frame.steps(); i++) {
            AttitudeState<M> next = this.model.integrate(steps.current(), mouse, keys, stepSetup, stepContext,
                    FixedStepClock.STEP_SECONDS);
            noteTurn(steps.current().attitude(), next.attitude());
            steps.push(next);
        }
        this.memory = steps.current().memory();
        this.orientation = steps.current().orientation();
        show(steps.shown(frame.fraction(), stepContext.settings().quaternionAttitude()));
    }

    /**
     * At the start of the vehicle's tick: a report from the pilot's client is applied (here and
     * nowhere else), per-tick memory resets, and the vehicle may move.
     */
    public void startTick() {
        this.inbox.applyPending(this.vehicle, this);
        this.terrain = null;
        this.memory = this.model.startTick(this.memory);
        if (this.fixedSteps != null) {
            this.fixedSteps.startTick(this.model);
        }
    }

    /** At the end of the vehicle's tick, after it moved. */
    public void endTick() {
        this.terrain = null;
    }

    /** Forgets what the model remembered, for a new pilot. The kept orientation stays, as it always did. */
    public void resetMemory() {
        this.memory = this.model.initialMemory();
        this.fixedSteps = null;
    }

    /** The report for this tick, or null when the attitude has not changed since the last one. */
    public AttitudeReport takeReport() {
        if (!this.unreported) {
            return null;
        }
        this.unreported = false;
        Attitude attitude = this.fixedSteps != null ? this.fixedSteps.current().attitude() : this.vehicle.body().attitude();
        return AttitudeReport.of(this.vehicle.getId(), ++this.sequence, attitude);
    }

    /** The orientation a quaternion camera should follow: the drawn one in fixed-step mode. */
    public Orientation cameraOrientation() {
        return this.fixedSteps != null ? this.fixedSteps.shownOrientation() : this.orientation;
    }

    /** The model's top turn rate for the current setup, degrees per second. */
    public float maxTurnRate() {
        return this.model.maxTurnRate(this.setup.get());
    }

    public boolean serverOwnsYaw() {
        return this.model.serverOwnsYaw();
    }

    public S setup() {
        return this.setup.get();
    }

    public AttitudeTerrain terrain() {
        if (this.terrain == null) {
            this.terrain = AttitudeTerrain.sample(this.vehicle.physics().terrain());
        }
        return this.terrain;
    }

    private AttitudeContext context() {
        AircraftEntity v = this.vehicle;
        return new AttitudeContext(terrain(), v.physics().settings(), v.getVehicle() != null,
                v.lifecycle().age() >= SETTLE_IN_TICKS, v.isDestroyed());
    }

    private void show(Attitude attitude) {
        this.vehicle.setYRot(attitude.yaw());
        this.vehicle.setXRot(attitude.pitch());
        this.vehicle.rollAngle().set(attitude.roll());
    }

    /**
     * Marks a turn for reporting. The legacy test is kept as it was, operator precedence included:
     * on a carrier a yaw change alone is not reported, since the carrier holds the yaw.
     */
    private void noteTurn(Attitude before, Attitude after) {
        boolean free = this.vehicle.getVehicle() == null;
        if (free && before.yaw() != after.yaw() || before.pitch() != after.pitch() || before.roll() != after.roll()) {
            this.unreported = true;
        }
    }
}
