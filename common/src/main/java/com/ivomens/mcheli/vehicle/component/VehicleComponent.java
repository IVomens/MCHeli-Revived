package com.ivomens.mcheli.vehicle.component;

import net.minecraft.nbt.CompoundTag;

/**
 * One concern of a vehicle that owns its own state: crew, weapons, fuel, damage, parts and so on.
 * <p>
 * <b>Dependencies.</b> A component gets every collaborator it works with through its constructor: the
 * other components of its vehicle (or the narrow view of one it needs), the synced values it owns, and
 * its host entity where it has to touch the world. It never asks the vehicle entity for another
 * component, so its constructor is the complete list of what it depends on. The vehicle's assembly
 * ({@code AircraftComponents}, and each vehicle kind's own constructor for the kind's components)
 * builds the components in dependency order, which keeps the graph free of cycles; docs/COMPONENTS.md
 * draws it for every vehicle kind. Both are checked by the architecture tests.
 * <p>
 * A component that reaches <em>another</em> vehicle (a tanker, a rack, a repair station) goes through
 * {@code VehiclePeers}, the one place that names those interactions.
 * <p>
 * <b>Ticking.</b> The vehicle's tick pipeline decides when a component runs; a component never asks
 * which side it is on, it gets {@link #serverTick} on the server and {@link #clientTick} on the client.
 * A component with a tick of its own declares where it belongs with {@link TickOrder}, and the
 * pipeline refuses to start when its order breaks a declaration.
 * <p>
 * Saved state goes through {@link #save} and {@link #load} with the legacy keys from
 * {@code VehicleNbtCodec}, so old worlds keep loading.
 */
public interface VehicleComponent {

    default void serverTick(TickContext ctx) {
    }

    default void clientTick(TickContext ctx) {
    }

    default void save(CompoundTag tag) {
    }

    default void load(CompoundTag tag) {
    }

    /**
     * Writes the component's key state for the vehicle inspector. Read-only: it changes nothing, and it
     * runs on either side, as often as every tick while an inspector is open.
     */
    default void describe(StateSink state) {
    }
}
