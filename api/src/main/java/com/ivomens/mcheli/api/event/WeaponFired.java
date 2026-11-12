package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A weapon is about to fire one shot: a vehicle's weapon fired by its crew, an AI gunner or a wingman, or a
 * handheld launcher. Fired once per shot, before anything is spawned or ammunition is used. Cancelling it
 * stops the shot: nothing is launched and no round is used. The shooter's client may already have shown its
 * own predicted muzzle flash and sound.
 *
 * <pre>{@code
 * events.register(WeaponFired.class, fired -> {
 *     if (fired.shooter() instanceof Player player && fired.level().getServer()
 *             .isUnderSpawnProtection(fired.level(), BlockPos.containing(fired.origin()), player)) {
 *         fired.setCancelled(true);
 *     }
 * });
 * }</pre>
 *
 * <p>Server side, on the server thread. Stable since API 1.0.
 */
public interface WeaponFired extends McheliEvent, Cancellable {

    ServerLevel level();

    /** The vehicle the weapon is mounted on; null for a handheld weapon. */
    @Nullable
    Vehicle vehicle();

    /** The player or mob pulling the trigger; null for an unmanned mount. */
    @Nullable
    Entity shooter();

    /** What the weapon is mounted on: the vehicle, or the shooter itself for a handheld weapon. */
    Entity platform();

    /** The weapon type's id, as {@link com.ivomens.mcheli.api.content.WeaponInfo#id()}. */
    String weaponId();

    /** The muzzle position. */
    Vec3 origin();

    /** The direction of fire, spread included, in degrees as Minecraft's entity rotation. */
    float yaw();

    /** The direction of fire, spread included, in degrees as Minecraft's entity rotation. */
    float pitch();
}
