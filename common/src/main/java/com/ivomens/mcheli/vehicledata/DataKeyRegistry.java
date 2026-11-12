package com.ivomens.mcheli.vehicledata;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.vehicle.data.VehicleDataKey;
import com.ivomens.mcheli.api.vehicle.data.VehicleDataKeys;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The keys addons declared, by id. One for the whole game, owned by the API service; keys are declared while
 * mods are constructed, but may come later, from any thread.
 */
public final class DataKeyRegistry implements VehicleDataKeys {

    private final Map<ResourceLocation, DataKey<?>> keys = new LinkedHashMap<>();
    /** Whether any declared key is synced; until one is, nothing about addon data goes over the network. */
    private volatile boolean anySynced;

    /** The registry of the running API. */
    static DataKeyRegistry get() {
        return (DataKeyRegistry) McheliApi.get().vehicleData();
    }

    @Override
    public synchronized <T> VehicleDataKey<T> declare(ResourceLocation id, Codec<T> codec,
            @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> syncCodec, T defaultValue) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(defaultValue, "defaultValue");
        if (this.keys.containsKey(id)) {
            throw new IllegalArgumentException("A vehicle data key " + id + " was declared already");
        }
        DataKey<T> key = new DataKey<>(id, codec, syncCodec, defaultValue);
        this.keys.put(id, key);
        this.anySynced |= key.isSynced();
        return key;
    }

    @Override
    public synchronized Optional<VehicleDataKey<?>> key(ResourceLocation id) {
        return Optional.ofNullable(this.keys.get(id));
    }

    @Override
    public synchronized List<VehicleDataKey<?>> keys() {
        return List.copyOf(this.keys.values());
    }

    /** The declared key with this id, or null. */
    @Nullable
    synchronized DataKey<?> find(ResourceLocation id) {
        return this.keys.get(id);
    }

    /** Whether some key is synced; false means there is never anything to send. */
    boolean anySynced() {
        return this.anySynced;
    }

    /** The key as a registry declared it; refuses an addon's own implementation of the interface. */
    static <T> DataKey<T> declared(VehicleDataKey<T> key) {
        if (key instanceof DataKey<T> own) {
            return own;
        }
        throw new IllegalArgumentException(key + " was not declared with McheliApi.vehicleData()");
    }
}
