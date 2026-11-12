package com.ivomens.mcheli.vehicle.client;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinition;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's models for every vehicle definition, by kind and name. The definitions themselves are
 * common code and must load on a dedicated server, so the models live here instead. Models load on
 * worker threads and render on the render thread; each entry is replaced whole.
 */
public final class VehicleModelRegistry {

    private static final Map<VehicleKind, Map<String, VehicleModels>> MODELS = createMaps();

    private VehicleModelRegistry() {
    }

    // one concurrent map per kind, made up front, so the outer map is only ever read
    private static Map<VehicleKind, Map<String, VehicleModels>> createMaps() {
        Map<VehicleKind, Map<String, VehicleModels>> maps = new EnumMap<>(VehicleKind.class);
        for (VehicleKind kind : VehicleKind.values()) {
            maps.put(kind, new ConcurrentHashMap<>());
        }
        return Collections.unmodifiableMap(maps);
    }

    /** The vehicle's models; an empty set while nothing is loaded. */
    public static VehicleModels of(VehicleKind kind, String name) {
        return name == null ? VehicleModels.NONE : MODELS.get(kind).getOrDefault(name, VehicleModels.NONE);
    }

    public static VehicleModels of(VehicleDefinition definition) {
        return of(definition.kind(), definition.name());
    }

    public static VehicleModels of(VehicleSpec info) {
        return of(info.kind(), info.name());
    }

    public static void put(VehicleKind kind, String name, VehicleModels models) {
        MODELS.get(kind).put(name, models);
    }
}
