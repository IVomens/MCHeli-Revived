package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The aircraft with a searchlight in the client level. Searchlights light the entities they point at, so
 * entity rendering asks for the brightest one; the list is gathered once per tick instead of every frame.
 *
 * <p>One per client, owned by the client session; only the client thread touches it.
 */
public final class SearchLights {

    private final List<AircraftEntity> aircraft = new ArrayList<>();

    /** Gathers the aircraft with a searchlight; called once per client tick. */
    public void refresh(ClientLevel level) {
        this.aircraft.clear();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof AircraftEntity ac && ac.searchLight().isFitted()) {
                this.aircraft.add(ac);
            }
        }
    }

    /** The aircraft with a searchlight, as of this client tick. */
    public List<AircraftEntity> aircraft() {
        return Collections.unmodifiableList(this.aircraft);
    }
}
