package com.ivomens.mcheli.api.vehicle.data;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * The keys addons declared for their vehicle data ({@link VehicleDataKey}). Reached through
 * {@code McheliApi.get().vehicleData()}.
 *
 * <p>Saved values whose id no key declares (an addon that was removed, or not loaded yet) are kept as they were
 * and saved again, so removing an addon for a while loses nothing. A key declared after a vehicle loaded still
 * reads that vehicle's saved value the first time it is asked for.
 *
 * <p><b>Side and thread.</b> Either side, any thread. Declare keys while your mod is constructed (in a static
 * field initializer, for example), so they exist before the first vehicle loads or a client receives synced
 * values.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface VehicleDataKeys {

    /**
     * Declares a key.
     *
     * @param id           unique id of the value, in the addon's namespace
     * @param codec        how the value is saved with the vehicle
     * @param syncCodec    how the value is sent to clients; null keeps it on the server
     * @param defaultValue what a vehicle without the value has; never null
     * @throws IllegalArgumentException when a key with this id was declared already
     */
    <T> VehicleDataKey<T> declare(ResourceLocation id, Codec<T> codec,
            @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> syncCodec, T defaultValue);

    /** The key declared with this id; empty when there is none. */
    Optional<VehicleDataKey<?>> key(ResourceLocation id);

    /** Every declared key, in the order they were declared. A copy. */
    List<VehicleDataKey<?>> keys();
}
