package com.ivomens.mcheli.api;

import com.ivomens.mcheli.api.content.ContentLookup;
import com.ivomens.mcheli.api.event.EventBus;
import com.ivomens.mcheli.api.target.TargetRegistry;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.extension.VehicleExtensions;
import com.ivomens.mcheli.api.wingman.Wingmen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * The entry point of the MCHeli: Revived API: vehicle lookups, the loaded content and the event bus.
 *
 * <pre>{@code
 * McheliApi api = McheliApi.get();
 * api.vehicleOf(player).ifPresent(vehicle -> LOGGER.info("{} is aboard a {}", player.getName().getString(),
 *         vehicle.kind()));
 * }</pre>
 *
 * <p><b>Side and thread.</b> {@link #get()} works on either side and from any thread once MCHeli is loaded.
 * The lookups that take a {@link Level} read that level's entities, so call them on the level's own thread:
 * the server thread for a server level, the client thread for a client level.
 *
 * <p><b>Stability.</b> Stable since API 1.0. MCHeli implements this interface and addons only call it, so a
 * minor version may add methods to it.
 */
public interface McheliApi {

    /**
     * The running MCHeli's API. It is found through {@link java.util.ServiceLoader} the first time this is
     * called; every call returns the same instance.
     *
     * @throws IllegalStateException when no implementation is on the class path, which means MCHeli itself
     *                               is not installed
     */
    static McheliApi get() {
        return ApiHolder.INSTANCE;
    }

    /**
     * The version of this API that the running MCHeli implements, {@code major.minor.patch}, for example
     * {@code 1.0.0}. An addon compiled against {@code a.b.x} works with every running {@code a.c.y} where
     * {@code c >= b}.
     */
    String apiVersion();

    /**
     * The vehicle an entity is, belongs to or is aboard: the vehicle itself, one of its seats or hit boxes,
     * its pilot, a passenger, or an operator flying it from a remote station. Empty for anything else.
     */
    Optional<Vehicle> vehicleOf(@Nullable Entity entity);

    /** Every vehicle loaded in the level, in the order they entered it. A copy; empty when there are none. */
    List<Vehicle> vehicles(Level level);

    /** The vehicles loaded in the level whose bounding box intersects {@code area}. A copy. */
    List<Vehicle> vehiclesIn(Level level, AABB area);

    /** The vehicle and weapon types loaded from the content packs right now. */
    ContentLookup content();

    /** Where addons listen to what happens to vehicles and weapons. */
    EventBus events();

    /**
     * The behaviour types and movement models mods registered through
     * {@link com.ivomens.mcheli.api.vehicle.extension.VehicleExtensionProvider}, and which a vehicle uses. Since
     * API 1.1.
     */
    VehicleExtensions vehicleExtensions();

    /**
     * Which entities of other mods MCHeli's radar, lock-on seekers and guided missiles treat as targets, and
     * which entities they never target. Any thread, either side. Since API 1.1.
     */
    TargetRegistry targets();

    /**
     * The AI wingmen: who has which, what they are doing, and the orders the wingman panel gives. Server side,
     * on the server thread. Since API 1.1.
     */
    Wingmen wingmen();
}
