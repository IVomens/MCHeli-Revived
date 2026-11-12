package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The server accepted a manual reload of the player's aircraft weapons. */
public record ReloadAcceptedMessage(int aircraftId) implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, ReloadAcceptedMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReloadAcceptedMessage::aircraftId,
            ReloadAcceptedMessage::new);

    public static final MessageType<ReloadAcceptedMessage> TYPE =
            MessageType.clientbound("combat/reload_accepted", CODEC);

    @Override
    public MessageType<ReloadAcceptedMessage> messageType() {
        return TYPE;
    }
}
