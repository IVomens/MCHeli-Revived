package com.ivomens.mcheli.api.vehicle.data;

import com.ivomens.mcheli.api.McheliApi;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The name and type of one value an addon keeps on vehicles: how it is saved, whether and how it is synced to
 * clients, and what it is when a vehicle has none. Declare each key once, usually in a {@code static final}
 * field, and use it to read and write the value ({@link VehicleData}, {@link MutableVehicleData}):
 *
 * <pre>{@code
 * public static final VehicleDataKey<Integer> KILLS = VehicleDataKey.of(
 *         ResourceLocation.fromNamespaceAndPath("mymod", "kills"), Codec.INT, ByteBufCodecs.VAR_INT, 0);
 * }</pre>
 *
 * <p>The id's namespace should be the addon's mod id; two keys can never share an id. Values are saved under
 * the id with {@link #codec()}. With a sync codec, the server also sends every change to the players that see
 * the vehicle, and a client reads the value from its copy of the vehicle; without one, clients only ever see
 * {@link #defaultValue()}. Values should be immutable (records, strings, numbers, immutable lists): MCHeli
 * only notices a change made with {@link MutableVehicleData#set}.
 *
 * <p><b>Side and thread.</b> Declaring works on either side and from any thread; declare the same keys on
 * the server and the client, so the client can read what the server syncs. A key object is immutable.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface VehicleDataKey<T> {

    /**
     * Declares a key: the same as {@code McheliApi.get().vehicleData().declare(...)}.
     *
     * @param id           unique id of the value, in the addon's namespace
     * @param codec        how the value is saved with the vehicle
     * @param syncCodec    how the value is sent to clients; null keeps it on the server
     * @param defaultValue what a vehicle without the value has; never null
     * @throws IllegalArgumentException when a key with this id was declared already
     */
    static <T> VehicleDataKey<T> of(ResourceLocation id, Codec<T> codec,
            @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> syncCodec, T defaultValue) {
        return McheliApi.get().vehicleData().declare(id, codec, syncCodec, defaultValue);
    }

    /** Declares a key whose value stays on the server; see {@link #of(ResourceLocation, Codec, StreamCodec, Object)}. */
    static <T> VehicleDataKey<T> of(ResourceLocation id, Codec<T> codec, T defaultValue) {
        return of(id, codec, null, defaultValue);
    }

    /** The unique id; also the name the value is saved under. */
    ResourceLocation id();

    /** How the value is saved. */
    Codec<T> codec();

    /** How the value is sent to clients; empty when it is not synced. */
    Optional<StreamCodec<? super RegistryFriendlyByteBuf, T>> syncCodec();

    /** Whether the value is synced to the clients that see the vehicle. */
    default boolean isSynced() {
        return syncCodec().isPresent();
    }

    /** The value of a vehicle that has none; never null. */
    T defaultValue();
}
