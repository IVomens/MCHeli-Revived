package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;

import javax.annotation.Nullable;

/**
 * A program flying the vehicle instead of a pilot, today the wingman autopilot. It does not press keys
 * or move the stick: it sets the throttle, the attitude and, on the ground, the motion directly, the
 * way it always has. This component is where those writes happen, in two named phases of the vehicle
 * tick:
 * <ol>
 *   <li><b>Take the controls</b>, the very first step of the vehicle tick, before a resting vehicle
 *       may skip the rest. The program decided before the level's entities started to tick, from the
 *       world as it was then; this applies that decision ({@link #queue}). A vehicle whose chunk did
 *       not tick gets it after the level instead ({@link #takeControls}), as if it had ticked.</li>
 *   <li><b>Hold</b>, once every entity of the level has moved: corrections that need where the leader
 *       and the targets ended up, and those that undo what the physics step did to the attitude on
 *       a runway or a helipad ({@link #hold}).</li>
 * </ol>
 * Server only; the client sees the result through the usual sync.
 */
public final class ScriptedPilotComponent implements VehicleComponent {

    /** Changes a program makes to the vehicle. */
    @FunctionalInterface
    public interface Script {
        void applyTo(AircraftEntity vehicle);
    }

    private final AircraftEntity vehicle;
    private @Nullable Script pending;

    public ScriptedPilotComponent(AircraftEntity vehicle) {
        this.vehicle = vehicle;
    }

    /** The controls for this tick, applied when the vehicle ticks. Replaces any not taken yet. */
    public void queue(Script controls) {
        this.pending = controls;
    }

    /** Applies the queued controls, once. Runs first in the vehicle tick. */
    public void takeControls() {
        Script controls = this.pending;
        if (controls != null) {
            this.pending = null;
            controls.applyTo(this.vehicle);
        }
    }

    @Override
    public void describe(StateSink state) {
        state.put("controls queued", this.pending != null);
    }

    /** Applies corrections now, after the level's entities have moved. */
    public void hold(Script corrections) {
        corrections.applyTo(this.vehicle);
    }
}
