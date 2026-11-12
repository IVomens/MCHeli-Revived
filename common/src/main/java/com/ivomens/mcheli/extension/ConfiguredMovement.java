package com.ivomens.mcheli.extension;

import com.ivomens.mcheli.api.vehicle.extension.MovementModel;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * The movement model a vehicle file picks, with its decoded settings: one per file and content load, shared by
 * every vehicle of the type.
 *
 * @param yaml the settings as the file spells them, or null when it gives none; for the emitter
 * @param <S>  the decoded settings
 */
public record ConfiguredMovement<S>(MovementType<S> type, S settings, @Nullable Object yaml) {

    public ResourceLocation id() {
        return this.type.id();
    }

    /** A new model for one vehicle. */
    public MovementModel create() {
        return Objects.requireNonNull(this.type.factory().create(this.settings),
                () -> "the factory of movement model " + id() + " returned nothing");
    }
}
