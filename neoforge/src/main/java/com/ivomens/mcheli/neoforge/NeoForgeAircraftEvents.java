package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.content.def.vehicle.VehicleKinds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Game-bus listeners for aircraft that only exist on NeoForge. */
final class NeoForgeAircraftEvents {

    private NeoForgeAircraftEvents() {
    }

    static void register(IEventBus bus) {
        bus.addListener(NeoForgeAircraftEvents::onEntityJoinLevel);
    }

    /** Aircraft carry their kind ("Plane", "Tank", ...) in the NeoForge persistent data, as they always have. */
    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof AircraftEntity aircraft) {
            aircraft.getPersistentData().putString("EntityType", VehicleKinds.legacyEntityType(aircraft.kind()));
        }
    }
}
