package com.ivomens.mcheli.vehicledata;

import com.ivomens.mcheli.util.ModLogger;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Encodes addon values with their keys' sync codecs, and packs them into messages of bounded size. */
final class SyncedValues {

    private SyncedValues() {
    }

    /** The value as an entry of the message; null when it cannot be encoded or is too large. */
    @Nullable
    @SuppressWarnings("unchecked")
    static <T> VehicleDataMessage.Entry encode(Entity vehicle, DataKey<T> key, Object value) {
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = key.syncCodec().orElse(null);
        if (codec == null) {
            return null;
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), vehicle.registryAccess());
        try {
            codec.encode(buffer, (T) value);
            if (buffer.readableBytes() > VehicleDataMessage.MAX_VALUE_BYTES) {
                ModLogger.warn("The value of {} on {} takes {} bytes, more than the {} a synced value may; not synced",
                        key.id(), vehicle, buffer.readableBytes(), VehicleDataMessage.MAX_VALUE_BYTES);
                return null;
            }
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new VehicleDataMessage.Entry(key.id(), false, bytes);
        } catch (RuntimeException e) {
            ModLogger.error("Could not sync the value of " + key.id() + " on " + vehicle, e);
            return null;
        } finally {
            buffer.release();
        }
    }

    /** The value the server sent; null when it cannot be decoded. */
    @Nullable
    static <T> T decode(Entity vehicle, DataKey<T> key, byte[] bytes) {
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = key.syncCodec().orElse(null);
        if (codec == null) {
            return null;
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes),
                vehicle.registryAccess());
        try {
            return codec.decode(buffer);
        } catch (RuntimeException e) {
            ModLogger.error("Could not read the synced value of " + key.id() + " on " + vehicle, e);
            return null;
        }
    }

    /** The entries in as many messages as their number needs. */
    static List<VehicleDataMessage> messages(Entity vehicle, List<VehicleDataMessage.Entry> entries) {
        List<VehicleDataMessage> messages = new ArrayList<>();
        for (int from = 0; from < entries.size(); from += VehicleDataMessage.MAX_ENTRIES) {
            int to = Math.min(entries.size(), from + VehicleDataMessage.MAX_ENTRIES);
            messages.add(new VehicleDataMessage(vehicle.getId(), List.copyOf(entries.subList(from, to))));
        }
        return messages;
    }
}
