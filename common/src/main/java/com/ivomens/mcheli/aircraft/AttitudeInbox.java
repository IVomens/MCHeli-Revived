package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.core.sim.attitude.AttitudeReport;
import com.ivomens.mcheli.core.sim.attitude.ReportGate;
import com.ivomens.mcheli.util.ModLogger;

/**
 * The server's side of the attitude reports. The newest report that arrived since the last tick
 * waits here and is applied at the start of the vehicle's tick, so the attitude never changes in the
 * middle of the vehicle's own tick or another entity's.
 */
final class AttitudeInbox {

    private AttitudeReport pending;
    private int pendingReporter;
    private ReportGate gate = ReportGate.OPEN;

    /** Keeps the report unless a newer one from the same player is already waiting. */
    void offer(AttitudeReport report, int reporter) {
        if (this.pending != null && this.pendingReporter == reporter
                && report.sequence() - this.pending.sequence() <= 0) {
            return;
        }
        this.pending = report;
        this.pendingReporter = reporter;
    }

    /** Applies the waiting report if the gate lets it through; a rejected report is only logged. */
    void applyPending(AircraftEntity vehicle, VehicleAttitude<?, ?> attitude) {
        AttitudeReport report = this.pending;
        if (report == null) {
            return;
        }
        this.pending = null;
        long tick = vehicle.level().getGameTime();
        ReportGate.Verdict verdict = this.gate.judge(this.pendingReporter, report, tick, attitude.maxTurnRate());
        if (verdict != ReportGate.Verdict.APPLY) {
            ModLogger.debug("Dropped attitude report {} for vehicle {}: {}", report.sequence(), vehicle.getId(),
                    verdict);
            return;
        }
        this.gate = this.gate.after(this.pendingReporter, report, tick);
        vehicle.rollAngle().set(report.roll());
        if (!attitude.serverOwnsYaw()) {
            vehicle.setYRot(report.yaw());
        }
        vehicle.setXRot(report.pitch());
    }
}
