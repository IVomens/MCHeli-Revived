package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.core.sim.attitude.AttitudeReport;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Queues a pilot's attitude report. Only the crew (or the UAV operator) may drive the vehicle's attitude. */
public final class AttitudeReportHandler implements ServerMessageHandler<AttitudeReportMessage> {

    @Override
    public void handle(AttitudeReportMessage message, ServerPlayer sender) {
        AttitudeReport report = message.report();
        if (!report.isFinite()) {
            return;
        }
        if (ServerMessageHandler.entityNear(sender, report.vehicleId()) instanceof AircraftEntity vehicle
                && RiddenVehicle.of(sender) == vehicle) {
            vehicle.attitudeControl().receiveReport(report, sender);
        }
    }
}
