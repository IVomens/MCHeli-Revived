package com.ivomens.mcheli.extension;

import com.ivomens.mcheli.api.vehicle.extension.BehaviourContext;
import com.ivomens.mcheli.api.vehicle.extension.VehicleBehaviour;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * A behaviour a vehicle file names, with its decoded settings: one per file and content load, shared by every
 * vehicle of the type.
 *
 * @param yaml the settings as the file spells them, for the emitter
 * @param <S>  the decoded settings
 */
public record ConfiguredBehaviour<S>(BehaviourType<S> type, S settings, Object yaml) {

    public ResourceLocation id() {
        return this.type.id();
    }

    /** A new behaviour for one vehicle. */
    public VehicleBehaviour create(BehaviourContext context) {
        return Objects.requireNonNull(this.type.factory().create(this.settings, context),
                () -> "the factory of behaviour " + id() + " returned nothing");
    }
}
