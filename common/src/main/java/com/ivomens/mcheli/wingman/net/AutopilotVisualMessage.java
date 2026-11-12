package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Target heading and throttle of an aircraft flown by the autopilot. The client re-applies them at the
 * end of each client tick (see {@code ClientAutopilotHandler}) so the key-input driven update does not
 * fight the autopilot. {@code NaN} clears the override.
 */
public record AutopilotVisualMessage(int entityId, float targetYaw, float targetThrottle)
        implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, AutopilotVisualMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AutopilotVisualMessage::entityId,
            ByteBufCodecs.FLOAT, AutopilotVisualMessage::targetYaw,
            ByteBufCodecs.FLOAT, AutopilotVisualMessage::targetThrottle,
            AutopilotVisualMessage::new);

    public static final MessageType<AutopilotVisualMessage> TYPE =
            MessageType.clientbound("wingman/autopilot_visual", CODEC);

    /** Ends the autopilot override of an aircraft. */
    public static AutopilotVisualMessage clear(int entityId) {
        return new AutopilotVisualMessage(entityId, Float.NaN, Float.NaN);
    }

    @Override
    public MessageType<AutopilotVisualMessage> messageType() {
        return TYPE;
    }
}
