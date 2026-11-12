package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.assist.AltitudeHold;
import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.control.ThrottleCommand;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.port.SimSettings;
import com.ivomens.mcheli.core.sim.propulsion.RotorThrottle;
import com.ivomens.mcheli.core.sim.propulsion.ThrottleLever;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The altitude hold flies the real helicopter law: {@link RotorThrottle} moves the throttle by the
 * keys the hold presses (with the legacy auto throttle on, as it is by default) and
 * {@link HelicopterModel} moves the airframe.
 */
class AltitudeHoldFlightTest {

    private static final RotorcraftHandling HANDLING = new RotorcraftHandling(SimFakes.GRAVITY, 1.0F, 0.0F, false);
    private static final SimSettings AUTO_THROTTLE = new SimSettings(true, true, false, SimSettings.TiltLimit.NONE);
    private static final float KEY_STEP = 1.0F;

    private final HelicopterModel model = new HelicopterModel();
    private final SimFakes.RecordingCollider collider = new SimFakes.RecordingCollider();
    private BodyState body = SimFakes.airborne(Vec3d.ZERO);
    private double throttle;
    private double altitude;
    private double climb;

    @Test
    void theThrottleForAClimbRateHoldsThatClimbRate() {
        this.throttle = HelicopterModel.throttleForClimb(SimFakes.GRAVITY.air(), 0.1);
        for (int tick = 0; tick < 400; tick++) {
            tick(ThrottleCommand.NONE, new SimSettings(false, true, false, SimSettings.TiltLimit.NONE));
        }
        assertEquals(0.1, this.climb, 1.0e-6);
        assertEquals(0.04 / 0.055, HelicopterModel.throttleForClimb(-0.04F, 0.0), 1.0e-6,
                "hovering takes 0.73 throttle at the usual gravity");
    }

    @Test
    void withoutTheHoldTheLegacyAutoThrottleSinks() {
        climbFor(60);
        double released = this.altitude;
        for (int tick = 0; tick < 300; tick++) {
            tick(ThrottleCommand.NONE, AUTO_THROTTLE);
        }
        assertTrue(this.altitude < released - 20.0, "sank from " + released + " to " + this.altitude);
    }

    @Test
    void theHoldKeepsTheAltitudeItCapturedWhenTheThrottleWasReleased() {
        climbFor(60);
        AltitudeHold hold = new AltitudeHold();
        double highest = Double.NEGATIVE_INFINITY;
        double worstLate = 0.0;
        for (int tick = 0; tick < 600; tick++) {
            ThrottleCommand keys = hold.update(new AltitudeHold.Reading(true, this.altitude, this.climb, this.throttle,
                    SimFakes.GRAVITY.air(), KEY_STEP));
            tick(keys, AUTO_THROTTLE);
            highest = Math.max(highest, this.altitude);
            if (tick >= 300) {
                worstLate = Math.max(worstLate, Math.abs(this.altitude - hold.target()));
            }
        }
        assertTrue(worstLate < 0.5, "drifts " + worstLate + " blocks from " + hold.target());
        assertTrue(highest - hold.target() < 3.0, "overshoots to " + highest + " past " + hold.target());
        assertTrue(Math.abs(this.climb) < 0.02, "still climbing at " + this.climb);
    }

    @Test
    void theHoldLetsGoWhenItIsNotEngagedAndCapturesAgainAfterwards() {
        AltitudeHold hold = new AltitudeHold();
        assertEquals(ThrottleCommand.NONE, hold.update(new AltitudeHold.Reading(false, 10.0, 0.0, 0.5, -0.04F, 1.0F)));
        assertTrue(Double.isNaN(hold.target()));
        hold.update(new AltitudeHold.Reading(true, 10.0, 0.1, 0.5, -0.04F, 1.0F));
        assertEquals(10.8, hold.target(), 1.0e-9, "aims a little ahead of a climbing helicopter");
        hold.update(new AltitudeHold.Reading(false, 12.0, 0.0, 0.5, -0.04F, 1.0F));
        hold.update(new AltitudeHold.Reading(true, 20.0, 0.0, 0.5, -0.04F, 1.0F));
        assertEquals(20.0, hold.target());
    }

    @Test
    void belowTheHoverThrottleItPressesUpAndFarAboveItPressesDown() {
        AltitudeHold hold = new AltitudeHold();
        assertEquals(ThrottleCommand.UP, hold.update(new AltitudeHold.Reading(true, 10.0, 0.0, 0.5, -0.04F, 1.0F)));
        AltitudeHold other = new AltitudeHold();
        assertEquals(ThrottleCommand.DOWN, other.update(new AltitudeHold.Reading(true, 10.0, 0.0, 1.0, -0.04F, 1.0F)));
    }

    private void climbFor(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            tick(ThrottleCommand.UP, AUTO_THROTTLE);
        }
    }

    private void tick(ThrottleCommand keys, SimSettings settings) {
        ControlInput input = new ControlInput(keys.up(), keys.down(), false, false, false);
        ThrottleLever lever = new ThrottleLever(this.throttle, 0.0F, () -> false);
        RotorThrottle.pilot(lever, input, KEY_STEP, settings);
        this.throttle = Math.clamp(lever.throttle(), 0.0, 1.0);
        MotionStep step = this.model.step(this.body, SimFakes.throttle(this.throttle), input, HANDLING,
                SimFakes.context(SimFakes.FixedTerrain.OPEN_SKY, this.collider));
        this.climb = this.collider.last().velocity().y();
        this.altitude += this.climb;
        this.body = step.body();
    }
}
