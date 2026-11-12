package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Which texture a vehicle wears: its livery once loaded, else the definition texture it was given,
 * swapped for the thermal texture while a thermal camera is on (except on the player's own vehicle).
 * <p>
 * Resolving can start a livery download, so it happens on the render thread; the result is published
 * for the Flywheel visuals, which pose on worker threads.
 */
public final class VehicleTextures {

    private static final Map<VehicleKind, String> DIRECTORIES = directories();
    /** The last texture each vehicle was drawn with, shared with the Flywheel worker threads. */
    private static final Map<AircraftEntity, ResourceLocation> PUBLISHED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final Supplier<AircraftEntity> ownVehicle;
    private final Function<String, ResourceLocation> liveryTexture;
    private final ThermalVision thermal;

    /**
     * @param ownVehicle    the vehicle the local player rides this frame, which never wears the thermal texture
     * @param liveryTexture the texture of a livery id, or null while it is not (yet) available
     * @param thermal       replaces the other vehicles' textures while the player looks through a thermal camera
     */
    public VehicleTextures(Supplier<AircraftEntity> ownVehicle, Function<String, ResourceLocation> liveryTexture,
                           ThermalVision thermal) {
        this.ownVehicle = ownVehicle;
        this.liveryTexture = liveryTexture;
        this.thermal = thermal;
    }

    private static Map<VehicleKind, String> directories() {
        Map<VehicleKind, String> directories = new EnumMap<>(VehicleKind.class);
        for (VehicleKind kind : VehicleKind.values()) {
            directories.put(kind, "textures/" + kind.directory() + "/");
        }
        return Collections.unmodifiableMap(directories);
    }

    /** The vehicle's texture this frame; render thread only. */
    public ResourceLocation resolve(AircraftEntity ac, VehicleSpec info) {
        ResourceLocation livery = McheliConfig.server().enableLiveries()
                ? this.liveryTexture.apply(ac.appearance().liveryId()) : null;
        ResourceLocation location = livery != null ? livery
                : McheliEntityRenderer.texture(DIRECTORIES.get(info.kind()), ac.appearance().texture());
        ResourceLocation texture = ac == this.ownVehicle.get() ? location : this.thermal.remap(location);
        PUBLISHED.put(ac, texture);
        return texture;
    }

    /** The texture last resolved for the vehicle, or null before it was first drawn; any thread. */
    @Nullable
    public static ResourceLocation published(AircraftEntity ac) {
        return PUBLISHED.get(ac);
    }
}
