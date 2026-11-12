package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import com.ivomens.mcheli.vehicle.settings.ClientPreferences;
import com.ivomens.mcheli.vehicle.settings.PilotSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Client-side settings that change how the server drives the sender's aircraft: whether dismounting
 * takes everyone out, automatic throttle-down per vehicle type, and whether shaders are on.
 */
public record ClientSettingsMessage(boolean dismountAll, boolean heliAutoThrottleDown, boolean planeAutoThrottleDown,
                                    boolean tankAutoThrottleDown, boolean shaderSupport) implements ServerboundMessage {

    private static final StreamCodec<ByteBuf, ClientSettingsMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ClientSettingsMessage::dismountAll,
            ByteBufCodecs.BOOL, ClientSettingsMessage::heliAutoThrottleDown,
            ByteBufCodecs.BOOL, ClientSettingsMessage::planeAutoThrottleDown,
            ByteBufCodecs.BOOL, ClientSettingsMessage::tankAutoThrottleDown,
            ByteBufCodecs.BOOL, ClientSettingsMessage::shaderSupport,
            ClientSettingsMessage::new);

    public static final MessageType<ClientSettingsMessage> TYPE =
            MessageType.serverbound("aircraft/client_settings", CODEC);

    /** The settings of this client, from its preferences, as it sends them when it boards an aircraft. */
    public static ClientSettingsMessage of(ClientPreferences preferences) {
        PilotSettings settings = preferences.pilot();
        return new ClientSettingsMessage(settings.dismountAll(), settings.heliAutoThrottleDown(),
                settings.planeAutoThrottleDown(), settings.tankAutoThrottleDown(), preferences.shaderSupport());
    }

    @Override
    public MessageType<ClientSettingsMessage> messageType() {
        return TYPE;
    }
}
