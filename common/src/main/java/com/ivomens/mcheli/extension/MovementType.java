package com.ivomens.mcheli.extension;

import com.ivomens.mcheli.api.vehicle.extension.MovementModelFactory;
import com.ivomens.mcheli.api.vehicle.extension.SettingsDecoder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * A movement model a mod registered.
 *
 * @param <S> its decoded settings
 */
public record MovementType<S>(ResourceLocation id, SettingsDecoder<S> decoder, MovementModelFactory<S> factory) {

    /**
     * The model with the settings of one vehicle file.
     *
     * @param yaml the settings as the file spells them, or null when it gives none; written back by the emitter
     * @throws RuntimeException when the mod's decoder rejects the settings
     */
    public ConfiguredMovement<S> configure(CompoundTag settings, Object yaml) {
        S decoded = this.decoder.decode(settings);
        if (decoded == null) {
            throw new IllegalArgumentException("the settings decoder returned nothing");
        }
        return new ConfiguredMovement<>(this, decoded, yaml);
    }
}
