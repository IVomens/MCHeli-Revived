package com.ivomens.mcheli.extension;

import com.ivomens.mcheli.api.vehicle.extension.BehaviourFactory;
import com.ivomens.mcheli.api.vehicle.extension.BehaviourTiming;
import com.ivomens.mcheli.api.vehicle.extension.SettingsDecoder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * A behaviour type a mod registered.
 *
 * @param <S> its decoded settings
 */
public record BehaviourType<S>(ResourceLocation id, BehaviourTiming timing, SettingsDecoder<S> decoder,
                               BehaviourFactory<S> factory) {

    /**
     * The type with the settings of one vehicle file.
     *
     * @param yaml the settings as the file spells them, written back by the emitter
     * @throws RuntimeException when the mod's decoder rejects the settings
     */
    public ConfiguredBehaviour<S> configure(CompoundTag settings, Object yaml) {
        S decoded = this.decoder.decode(settings);
        if (decoded == null) {
            throw new IllegalArgumentException("the settings decoder returned nothing");
        }
        return new ConfiguredBehaviour<>(this, decoded, yaml);
    }
}
