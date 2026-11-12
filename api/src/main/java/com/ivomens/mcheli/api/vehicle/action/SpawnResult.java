package com.ivomens.mcheli.api.vehicle.action;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Optional;

/**
 * What a {@link VehicleSpawner#spawn()} did: the vehicle it placed, or why it placed none.
 *
 * <p>Immutable. Server side. Since API 1.1; implemented by MCHeli only, so a minor version may add methods.
 */
public interface SpawnResult {

    /** Whether the vehicle was placed, and if not, why. */
    SpawnStatus status();

    /** Whether the vehicle was placed; the same as {@code status().isSpawned()}. */
    default boolean isSpawned() {
        return status().isSpawned();
    }

    /** The vehicle that was placed; empty when none was. */
    Optional<Vehicle> vehicle();

    /**
     * The message a player placing the vehicle would have been shown, for {@link SpawnStatus#LIMIT_REACHED}
     * (it names the limit); empty otherwise.
     */
    Optional<Component> message();

    /**
     * How boarding went for each rider the spawner was given, by seat id ({@link ActionResult#SUCCESS} when the
     * rider sits there). Empty when no riders were given or the vehicle was not placed.
     */
    Map<Integer, ActionResult> riders();
}
