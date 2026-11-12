package com.ivomens.mcheli.vehicle.client;

import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.client.model.IModelCustom;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * The models of one vehicle definition: the body and the parts that have a model file of their own.
 * A part without one is drawn from the {@code $name} group of the body. Loading builds a complete new
 * set and publishes it at once, so a frame never sees a body without its parts.
 *
 * @param body  the body, a placeholder while loading on demand, or null when there is none
 * @param parts part models by part name; parts drawn from the body are absent
 */
public record VehicleModels(@Nullable IModelCustom body, Map<String, IModelCustom> parts) {

    static final VehicleModels NONE = new VehicleModels(null, Map.of());

    public VehicleModels {
        parts = Map.copyOf(parts);
    }

    @Nullable
    public IModelCustom part(String modelName) {
        return modelName == null ? null : this.parts.get(modelName);
    }

    @Nullable
    public IModelCustom part(VehicleParts.DrawnPart part) {
        return part(part.modelName);
    }
}
