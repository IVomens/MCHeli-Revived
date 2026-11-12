package com.ivomens.mcheli.apiservice;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.content.ContentLookup;
import com.ivomens.mcheli.api.event.EventBus;
import com.ivomens.mcheli.api.target.TargetRegistry;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleHitBox;
import com.ivomens.mcheli.api.vehicle.VehicleSeat;
import com.ivomens.mcheli.api.vehicle.action.VehicleActions;
import com.ivomens.mcheli.api.vehicle.data.VehicleDataKeys;
import com.ivomens.mcheli.api.vehicle.extension.VehicleExtensions;
import com.ivomens.mcheli.api.wingman.Wingmen;
import com.ivomens.mcheli.combat.guidance.ExternalTargets;
import com.ivomens.mcheli.entityview.Riders;
import com.ivomens.mcheli.event.ApiEventBus;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import com.ivomens.mcheli.vehicledata.DataKeyRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MCHeli's implementation of the public API, found by {@code McheliApi.get()} through
 * {@code META-INF/services}. One instance for the whole game; it owns the event bus, the target registry and the
 * addons' vehicle data keys.
 */
public final class ApiService implements McheliApi {

    /** The API version this build implements; ApiVersionTest keeps it equal to api_version in gradle.properties. */
    public static final String API_VERSION = "1.1.0";

    private final ApiEventBus events = new ApiEventBus();
    private final ContentLookup content = new LoadedContentLookup();
    private final VehicleExtensions vehicleExtensions = new LoadedVehicleExtensions();
    private final ExternalTargets targets = new ExternalTargets();
    private final Wingmen wingmen = new ApiWingmen();
    private final VehicleActions actions = new ApiVehicleActions();
    private final DataKeyRegistry dataKeys = new DataKeyRegistry();

    @Override
    public String apiVersion() {
        return API_VERSION;
    }

    @Override
    public Optional<Vehicle> vehicleOf(@Nullable Entity entity) {
        return Optional.ofNullable(switch (entity) {
            case Vehicle vehicle -> vehicle;
            case VehicleSeat seat -> seat.getParent();
            case VehicleHitBox hitBox -> hitBox.owner() instanceof Vehicle owner ? owner : null;
            case null -> null;
            default -> Riders.vehicleOf(entity);
        });
    }

    @Override
    public List<Vehicle> vehicles(Level level) {
        return List.copyOf(AircraftIndex.of(level).aircraft());
    }

    @Override
    public List<Vehicle> vehiclesIn(Level level, AABB area) {
        List<Vehicle> found = new ArrayList<>();
        for (AircraftEntity aircraft : AircraftIndex.of(level).aircraft()) {
            if (aircraft.getBoundingBox().intersects(area)) {
                found.add(aircraft);
            }
        }
        return List.copyOf(found);
    }

    @Override
    public ContentLookup content() {
        return this.content;
    }

    @Override
    public EventBus events() {
        return this.events;
    }

    @Override
    public VehicleExtensions vehicleExtensions() {
        return this.vehicleExtensions;
    }

    @Override
    public TargetRegistry targets() {
        return this.targets;
    }

    @Override
    public Wingmen wingmen() {
        return this.wingmen;
    }

    @Override
    public VehicleActions vehicleActions() {
        return this.actions;
    }

    @Override
    public VehicleDataKeys vehicleData() {
        return this.dataKeys;
    }
}
