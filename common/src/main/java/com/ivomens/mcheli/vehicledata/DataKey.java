package com.ivomens.mcheli.vehicledata;

import com.ivomens.mcheli.api.vehicle.data.VehicleDataKey;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;

/** A declared {@link VehicleDataKey}. Keys are unique by id, so two keys are the same key only if identical. */
final class DataKey<T> implements VehicleDataKey<T> {

    private final ResourceLocation id;
    private final Codec<T> codec;
    @Nullable
    private final StreamCodec<? super RegistryFriendlyByteBuf, T> syncCodec;
    private final T defaultValue;

    DataKey(ResourceLocation id, Codec<T> codec, @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> syncCodec,
            T defaultValue) {
        this.id = id;
        this.codec = codec;
        this.syncCodec = syncCodec;
        this.defaultValue = defaultValue;
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public Codec<T> codec() {
        return this.codec;
    }

    @Override
    public Optional<StreamCodec<? super RegistryFriendlyByteBuf, T>> syncCodec() {
        return Optional.ofNullable(this.syncCodec);
    }

    @Override
    public boolean isSynced() {
        return this.syncCodec != null;
    }

    @Override
    public T defaultValue() {
        return this.defaultValue;
    }

    @Override
    public String toString() {
        return "VehicleDataKey[" + this.id + "]";
    }
}
