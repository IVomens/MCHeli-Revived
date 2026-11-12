package com.ivomens.mcheli.multiplay.net;

import com.ivomens.mcheli.config.ServerSettings;
import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server-side settings the client has to respect: whether the camera distance may change, entity
 * markers, PvP, the Stinger lock range, debug bounding boxes, the rotation limits and liveries.
 */
public record ServerSettingsMessage(boolean enableCamDistChange, boolean enableEntityMarker, boolean enablePVP,
                                    double stingerLockRange, Toggles toggles, RotationLimit rotationLimit)
        implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, ServerSettingsMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ServerSettingsMessage::enableCamDistChange,
            ByteBufCodecs.BOOL, ServerSettingsMessage::enableEntityMarker,
            ByteBufCodecs.BOOL, ServerSettingsMessage::enablePVP,
            ByteBufCodecs.DOUBLE, ServerSettingsMessage::stingerLockRange,
            Toggles.CODEC, ServerSettingsMessage::toggles,
            RotationLimit.CODEC, ServerSettingsMessage::rotationLimit,
            ServerSettingsMessage::new);

    public static final MessageType<ServerSettingsMessage> TYPE =
            MessageType.clientbound("multiplay/server_settings", CODEC);

    /** The settings as sent; the rotation limits travel as whole degrees, as they always have. */
    public static ServerSettingsMessage of(ServerSettings settings) {
        return new ServerSettingsMessage(settings.enableCamDistChange(), settings.enableEntityMarker(),
                settings.enablePVP(), settings.stingerLockRange(),
                new Toggles(settings.enableDebugBoundingBox(), settings.enableLiveries()),
                new RotationLimit(settings.enableRotationLimit(), (byte) settings.pitchLimitMax(),
                        (byte) settings.pitchLimitMin(), (byte) settings.rollLimit()));
    }

    /** The settings this message carries. */
    public ServerSettings settings() {
        return new ServerSettings(enableCamDistChange, enableEntityMarker, enablePVP, stingerLockRange,
                toggles.enableDebugBoundingBox(), rotationLimit.enabled(), rotationLimit.pitchMax(),
                rotationLimit.pitchMin(), rotationLimit.roll(), toggles.enableLiveries());
    }

    /** Debug and feature switches. */
    public record Toggles(boolean enableDebugBoundingBox, boolean enableLiveries) {

        static final StreamCodec<ByteBuf, Toggles> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Toggles::enableDebugBoundingBox,
                ByteBufCodecs.BOOL, Toggles::enableLiveries,
                Toggles::new);
    }

    /** The aircraft pitch and roll limits, in degrees, when {@code enabled}. */
    public record RotationLimit(boolean enabled, byte pitchMax, byte pitchMin, byte roll) {

        static final StreamCodec<ByteBuf, RotationLimit> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, RotationLimit::enabled,
                ByteBufCodecs.BYTE, RotationLimit::pitchMax,
                ByteBufCodecs.BYTE, RotationLimit::pitchMin,
                ByteBufCodecs.BYTE, RotationLimit::roll,
                RotationLimit::new);
    }

    @Override
    public MessageType<ServerSettingsMessage> messageType() {
        return TYPE;
    }
}
