package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** Contents of the wingman panel: free aircraft nearby, the current wingmen and the formation settings. */
public record WingmanPanelMessage(List<NearbyAircraft> nearby, List<WingmanView> wingmen, FormationSettings formation)
        implements ClientboundMessage {

    private static final StreamCodec<ByteBuf, WingmanPanelMessage> CODEC = StreamCodec.composite(
            WingmanCodecs.listOf(NearbyAircraft.CODEC), WingmanPanelMessage::nearby,
            WingmanCodecs.listOf(WingmanView.CODEC), WingmanPanelMessage::wingmen,
            FormationSettings.CODEC, WingmanPanelMessage::formation,
            WingmanPanelMessage::new);

    public static final MessageType<WingmanPanelMessage> TYPE = MessageType.clientbound("wingman/panel", CODEC);

    public WingmanPanelMessage {
        nearby = List.copyOf(nearby);
        wingmen = List.copyOf(wingmen);
    }

    @Override
    public MessageType<WingmanPanelMessage> messageType() {
        return TYPE;
    }
}
