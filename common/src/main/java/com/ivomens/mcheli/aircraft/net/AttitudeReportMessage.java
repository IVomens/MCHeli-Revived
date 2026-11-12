package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.core.sim.attitude.AttitudeReport;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The attitude the pilot's client computed. The server queues it and applies it at the start of the
 * vehicle's next tick, dropping late and implausible reports.
 */
public record AttitudeReportMessage(AttitudeReport report) implements ServerboundMessage {

    /** The report itself stays free of Minecraft types, so its codec lives here. */
    private static final StreamCodec<ByteBuf, AttitudeReport> REPORT_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AttitudeReport::vehicleId,
            ByteBufCodecs.VAR_INT, AttitudeReport::sequence,
            ByteBufCodecs.FLOAT, AttitudeReport::yaw,
            ByteBufCodecs.FLOAT, AttitudeReport::pitch,
            ByteBufCodecs.FLOAT, AttitudeReport::roll,
            AttitudeReport::new);

    private static final StreamCodec<ByteBuf, AttitudeReportMessage> CODEC =
            REPORT_CODEC.map(AttitudeReportMessage::new, AttitudeReportMessage::report);

    public static final MessageType<AttitudeReportMessage> TYPE =
            MessageType.serverbound("aircraft/attitude_report", CODEC);

    @Override
    public MessageType<AttitudeReportMessage> messageType() {
        return TYPE;
    }
}
