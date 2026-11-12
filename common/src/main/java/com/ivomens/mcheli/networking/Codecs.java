package com.ivomens.mcheli.networking;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Field codecs for message records. Every string, list and array gets an explicit bound: the
 * encoder refuses to write more and the decoder refuses to read more, so a client cannot make the
 * server allocate more than the bound.
 */
public final class Codecs {

    /** A position as three doubles. */
    public static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new);

    private Codecs() {
    }

    /** A UTF-8 string of at most {@code maxLength} characters. */
    public static StreamCodec<ByteBuf, String> utf(int maxLength) {
        return ByteBufCodecs.stringUtf8(maxLength);
    }

    /** A byte array of at most {@code maxBytes} bytes. */
    public static StreamCodec<ByteBuf, byte[]> bytes(int maxBytes) {
        return ByteBufCodecs.byteArray(maxBytes);
    }

    /** A list of at most {@code maxSize} elements; the decoded list is immutable. */
    public static <B extends ByteBuf, V> StreamCodec<B, List<V>> list(StreamCodec<? super B, V> element, int maxSize) {
        return ByteBufCodecs.<B, V, List<V>>collection(ArrayList::new, element, maxSize)
                .map(List::copyOf, list -> list);
    }

    /** An enum constant sent as its ordinal; an ordinal outside the enum fails decoding. */
    public static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumOf(Class<E> type) {
        E[] values = type.getEnumConstants();
        return ByteBufCodecs.VAR_INT.map(ordinal -> {
            if (ordinal < 0 || ordinal >= values.length) {
                throw new DecoderException(type.getSimpleName() + " ordinal out of range: " + ordinal);
            }
            return values[ordinal];
        }, Enum::ordinal);
    }
}
