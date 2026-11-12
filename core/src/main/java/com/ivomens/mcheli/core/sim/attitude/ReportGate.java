package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Orientation;

/**
 * The server's check on attitude reports from a pilot's client: reports older than the last applied
 * one are dropped, and so are reports that turned the vehicle much faster than its model can.
 * <p>
 * The check is generous on purpose. It compares orientations, not angles, so a loop through the
 * vertical (where yaw jumps by 180 degrees) passes, and it allows for self-levelling and the slack
 * of a client that runs a little ahead. The allowance grows with the ticks since the last applied
 * report, so a client that drifted far (a lag spike, a stale quaternion after boarding) is accepted
 * again within a second or two instead of being locked out.
 *
 * @param reporter    entity id of the player whose reports were applied; another player starts over
 * @param sequence    sequence number of the last applied report
 * @param applied     attitude of the last applied report, or null before the first
 * @param appliedTick game time it was applied at
 */
public record ReportGate(int reporter, int sequence, Attitude applied, long appliedTick) {

    public static final ReportGate OPEN = new ReportGate(Integer.MIN_VALUE, 0, null, 0L);

    /** Reports may turn this many times faster than the model's top rate. */
    private static final float RATE_MARGIN = 2.0F;
    /** Self-levelling, ground settling and recoil, which the model rates leave out; degrees per second. */
    private static final float SETTLE_ALLOWANCE = 180.0F;
    /** Always allowed, whatever the time: rounding, wrap-around and a client one step ahead. */
    private static final float SLACK_DEGREES = 15.0F;
    private static final float TICKS_PER_SECOND = 20.0F;

    public enum Verdict {
        APPLY,
        STALE,
        TOO_FAST
    }

    /**
     * @param reporter    entity id of the sending player
     * @param tick        current game time
     * @param maxTurnRate the vehicle model's top turn rate, degrees per second
     */
    public Verdict judge(int reporter, AttitudeReport report, long tick, float maxTurnRate) {
        if (reporter != this.reporter || this.applied == null) {
            return Verdict.APPLY;
        }
        // compared by difference, so the sequence may wrap around
        if (report.sequence() - this.sequence <= 0) {
            return Verdict.STALE;
        }
        float elapsedSeconds = Math.max(1L, tick - this.appliedTick) / TICKS_PER_SECOND;
        float allowed = (maxTurnRate * RATE_MARGIN + SETTLE_ALLOWANCE) * elapsedSeconds + SLACK_DEGREES;
        float turned = Orientation.of(this.applied).degreesTo(Orientation.of(report.attitude()));
        return turned > allowed ? Verdict.TOO_FAST : Verdict.APPLY;
    }

    public ReportGate after(int newReporter, AttitudeReport report, long tick) {
        return new ReportGate(newReporter, report.sequence(), report.attitude(), tick);
    }
}
