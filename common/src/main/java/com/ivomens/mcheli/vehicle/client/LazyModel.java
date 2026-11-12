package com.ivomens.mcheli.vehicle.client;

import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.api.vehicle.VehicleKind;

/**
 * Placeholder model used while a vehicle's real model is not loaded (on-demand mode). Rendering it
 * draws nothing but asks {@link OnDemandModels} to load the real one in the background.
 */
public class LazyModel implements IModelCustom {

    private final VehicleKind kind;
    private final String name;

    public LazyModel(VehicleKind kind, String name) {
        this.kind = kind;
        this.name = name;
    }

    private void request() {
        OnDemandModels.notifyRendered(this.kind, this.name);
    }

    @Override
    public String getType() {
        return "lazy";
    }

    @Override
    public void renderAll() {
        this.request();
    }

    @Override
    public void renderPart(String partName) {
        this.request();
    }
}
