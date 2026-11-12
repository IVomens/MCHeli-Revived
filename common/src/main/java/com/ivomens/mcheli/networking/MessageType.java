package com.ivomens.mcheli.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The id, direction and codec of one message record. Ids are {@code mcheli:<feature>/<name>}. They
 * are not saved anywhere, so renaming one only needs a {@link Network#PROTOCOL} bump.
 */
public record MessageType<M extends Message>(CustomPacketPayload.Type<M> payloadType, Direction direction,
                                             StreamCodec<RegistryFriendlyByteBuf, M> codec) {

    private static final String NAMESPACE = "mcheli";

    public enum Direction { SERVERBOUND, CLIENTBOUND }

    public static <M extends ServerboundMessage> MessageType<M> serverbound(
            String name, StreamCodec<? super RegistryFriendlyByteBuf, M> codec) {
        return new MessageType<>(payloadType(name), Direction.SERVERBOUND, codec.cast());
    }

    public static <M extends ClientboundMessage> MessageType<M> clientbound(
            String name, StreamCodec<? super RegistryFriendlyByteBuf, M> codec) {
        return new MessageType<>(payloadType(name), Direction.CLIENTBOUND, codec.cast());
    }

    public ResourceLocation id() {
        return payloadType.id();
    }

    private static <M extends Message> CustomPacketPayload.Type<M> payloadType(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NAMESPACE, name));
    }
}
