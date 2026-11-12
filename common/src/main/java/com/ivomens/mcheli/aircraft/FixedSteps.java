package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.core.sim.attitude.AttitudeModel;
import com.ivomens.mcheli.core.sim.attitude.AttitudeState;
import com.ivomens.mcheli.core.sim.attitude.FixedStepClock;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.core.sim.math.Orientation;

/**
 * The pilot client's fixed-step attitude: the last two substeps, the clock between them, and what
 * was last drawn. The vehicle shows an attitude between the two substeps, so a 144 Hz screen sees a
 * smooth turn although the model only steps at 60 Hz; the report sends the newest substep, which is
 * at most one step ahead of the frame (see {@link FixedStepClock}).
 *
 * @param <M> the model's memory
 */
final class FixedSteps<M> {

    private AttitudeState<M> previous;
    private AttitudeState<M> current;
    private FixedStepClock clock = FixedStepClock.START;
    private Attitude shown;
    private Orientation shownOrientation;

    FixedSteps(AttitudeState<M> start) {
        this.previous = start;
        this.current = start;
        this.shown = start.attitude();
        this.shownOrientation = start.orientation();
    }

    AttitudeState<M> current() {
        return this.current;
    }

    Orientation shownOrientation() {
        return this.shownOrientation;
    }

    /**
     * Keeps what changed the vehicle outside the model since the last frame (ground settling and
     * reverse steering in the client tick, a correction): both substeps move by the same amount, so
     * the change stays and the turn in progress is not lost.
     */
    void follow(Attitude onVehicle) {
        if (onVehicle.equals(this.shown)) {
            return;
        }
        this.previous = this.previous.withAttitude(moved(this.previous.attitude(), this.shown, onVehicle));
        this.current = this.current.withAttitude(moved(this.current.attitude(), this.shown, onVehicle));
        this.shown = onVehicle;
    }

    FixedStepClock.Frame advance(float frameSeconds) {
        FixedStepClock.Frame frame = this.clock.advance(frameSeconds);
        this.clock = frame.clock();
        return frame;
    }

    void push(AttitudeState<M> next) {
        this.previous = this.current;
        this.current = next;
    }

    void startTick(AttitudeModel<?, M> model) {
        this.previous = this.previous.withMemory(model.startTick(this.previous.memory()));
        this.current = this.current.withMemory(model.startTick(this.current.memory()));
    }

    /**
     * The attitude {@code fraction} of the way from the previous substep to the current one. With
     * quaternions on, it is read from the blended orientation, which stays smooth through the
     * vertical where the angles jump.
     */
    Attitude shown(float fraction, boolean quaternion) {
        Orientation from = this.previous.orientation();
        Orientation to = this.current.orientation();
        if (fraction >= 1.0F) {
            // exactly on the newest step: show it as it is, not as read back from a blend
            this.shownOrientation = to;
            this.shown = this.current.attitude();
        } else if (quaternion && from != null && to != null) {
            this.shownOrientation = from.towards(to, fraction);
            this.shown = this.shownOrientation.toAttitude();
        } else {
            this.shownOrientation = to;
            this.shown = blend(this.previous.attitude(), this.current.attitude(), fraction);
        }
        return this.shown;
    }

    private static Attitude blend(Attitude from, Attitude to, float fraction) {
        return new Attitude(from.yaw() + LegacyMath.wrapDegrees(to.yaw() - from.yaw()) * fraction,
                from.pitch() + (to.pitch() - from.pitch()) * fraction,
                from.roll() + LegacyMath.wrapDegrees(to.roll() - from.roll()) * fraction);
    }

    private static Attitude moved(Attitude attitude, Attitude from, Attitude to) {
        return new Attitude(attitude.yaw() + (to.yaw() - from.yaw()), attitude.pitch() + (to.pitch() - from.pitch()),
                attitude.roll() + (to.roll() - from.roll()));
    }
}
