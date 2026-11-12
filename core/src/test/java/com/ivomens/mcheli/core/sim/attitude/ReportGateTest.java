package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.math.Attitude;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportGateTest {

    private static final int PILOT = 7;
    private static final float HELICOPTER_RATE = 154.0F;

    private static AttitudeReport report(int sequence, float yaw, float pitch, float roll) {
        return new AttitudeReport(1, sequence, yaw, pitch, roll);
    }

    private static ReportGate appliedAt(AttitudeReport report, long tick) {
        return ReportGate.OPEN.after(PILOT, report, tick);
    }

    @Test
    void theFirstReportIsAlwaysApplied() {
        assertEquals(ReportGate.Verdict.APPLY,
                ReportGate.OPEN.judge(PILOT, report(5, 170.0F, 60.0F, -120.0F), 100L, HELICOPTER_RATE));
    }

    @Test
    void anOlderReportIsDropped() {
        ReportGate gate = appliedAt(report(10, 0.0F, 0.0F, 0.0F), 100L);
        assertEquals(ReportGate.Verdict.STALE, gate.judge(PILOT, report(9, 1.0F, 0.0F, 0.0F), 101L, HELICOPTER_RATE));
        assertEquals(ReportGate.Verdict.STALE, gate.judge(PILOT, report(10, 1.0F, 0.0F, 0.0F), 101L, HELICOPTER_RATE));
    }

    @Test
    void aFastButPossibleTurnPasses() {
        // full stick on every axis for one tick, with margin to spare
        ReportGate gate = appliedAt(report(1, 0.0F, 0.0F, 0.0F), 100L);
        assertEquals(ReportGate.Verdict.APPLY,
                gate.judge(PILOT, report(2, 2.0F, -2.0F, 6.0F), 101L, HELICOPTER_RATE));
    }

    @Test
    void anImpossibleSnapIsRejected() {
        ReportGate gate = appliedAt(report(1, 0.0F, 0.0F, 0.0F), 100L);
        assertEquals(ReportGate.Verdict.TOO_FAST,
                gate.judge(PILOT, report(2, 120.0F, 0.0F, 0.0F), 101L, HELICOPTER_RATE));
    }

    @Test
    void theSameTurnPassesOnceEnoughTicksWentBy() {
        ReportGate gate = appliedAt(report(1, 0.0F, 0.0F, 0.0F), 100L);
        assertEquals(ReportGate.Verdict.APPLY,
                gate.judge(PILOT, report(2, 120.0F, 0.0F, 0.0F), 110L, HELICOPTER_RATE));
    }

    @Test
    void aLoopThroughTheVerticalIsNotAJump() {
        // just past straight up, yaw and roll flip by 180 degrees while the airframe barely moves
        ReportGate gate = appliedAt(report(1, 10.0F, -89.5F, 0.0F), 100L);
        Attitude overTheTop = new Attitude(190.0F, -89.5F, 180.0F);
        assertEquals(ReportGate.Verdict.APPLY, gate.judge(PILOT,
                report(2, overTheTop.yaw(), overTheTop.pitch(), overTheTop.roll()), 101L, HELICOPTER_RATE));
    }

    @Test
    void anotherPilotStartsOver() {
        ReportGate gate = appliedAt(report(50, 0.0F, 0.0F, 0.0F), 100L);
        assertEquals(ReportGate.Verdict.APPLY, gate.judge(PILOT + 1, report(1, 90.0F, 0.0F, 0.0F), 101L, 0.0F));
    }
}
