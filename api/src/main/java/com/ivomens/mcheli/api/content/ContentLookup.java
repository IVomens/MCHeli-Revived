package com.ivomens.mcheli.api.content;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;

import java.util.List;
import java.util.Optional;

/**
 * The vehicle and weapon types loaded from the content packs: MCHeli's bundled content, the packs mods add
 * ({@link ContentPackProvider}) and the packs in {@code mcheli_addons/}, after the override rules picked one
 * file per name.
 *
 * <pre>{@code
 * McheliApi.get().content().vehicle(VehicleKind.HELICOPTER, "ah-64")
 *         .ifPresent(info -> LOGGER.info("{} comes from pack {}", info.displayName(), info.packId()));
 * }</pre>
 *
 * <p><b>Side and thread.</b> Both sides load the content. An operator can reload it in game, which replaces
 * the types; call these on the server or client thread, and look a type up again rather than keeping it.
 *
 * <p><b>Stability.</b> Stable since API 1.0. Implemented by MCHeli only; a minor version may add methods.
 */
public interface ContentLookup {

    /** Every loaded vehicle type, by kind and then by id. */
    List<VehicleInfo> vehicles();

    /** The vehicle type with this id; ids are unique only within a kind. */
    Optional<VehicleInfo> vehicle(VehicleKind kind, String id);

    /** The type of a vehicle in the world; empty while a client has not received it yet. */
    Optional<VehicleInfo> vehicleOf(Vehicle vehicle);

    /** Every loaded weapon type, by id. */
    List<WeaponInfo> weapons();

    /** The weapon type with this id. */
    Optional<WeaponInfo> weapon(String id);
}
