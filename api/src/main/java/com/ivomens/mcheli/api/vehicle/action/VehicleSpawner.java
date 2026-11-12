package com.ivomens.mcheli.api.vehicle.action;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Places a new vehicle of one type: set what you need, then call {@link #spawn()}. Every setter returns this
 * spawner. {@link VehicleActions#spawner} makes one.
 *
 * <pre>{@code
 * SpawnResult result = McheliApi.get().vehicleActions()
 *         .spawner(level, VehicleKind.HELICOPTER, "ah-64", pos)
 *         .rotation(player.getYRot(), 0.0F)
 *         .owner(player)
 *         .rider(0, player)
 *         .spawn();
 * }</pre>
 *
 * <p>The vehicle joins the level the way a placed one does ({@code VehicleSpawned} fires), with full health
 * and the fuel and ammunition of a vehicle placed from a new item, which is usually none: fill it with
 * {@link VehicleControl#refuel()} and {@link VehicleControl#rearm()}. Unlike a player placing it, a
 * spawner does not check creative-only types, target drones or the {@code PlaceableOnSpongeOnly} rule, and
 * checks the server's vehicle limits only when asked to ({@link #respectLimits}).
 *
 * <p><b>Side and thread.</b> Server only, on the server thread. A spawner is not thread-safe; each
 * {@link #spawn()} places another vehicle.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface VehicleSpawner {

    /** The heading (degrees, as an entity's yaw) and pitch the vehicle starts with; both 0 by default. */
    VehicleSpawner rotation(float yaw, float pitch);

    /**
     * The player who owns the vehicle, as if they had placed it: the owner can lock it, and the per-player
     * vehicle limit counts it. Null (the default) leaves it without an owner, like a dispensed vehicle.
     */
    VehicleSpawner owner(@Nullable Player owner);

    /**
     * Seats an entity once the vehicle is in the level: seat 0 is the pilot, 1 and up the passenger seats.
     * The rider leaves whatever it rides first. Locks do not apply. How it went is in
     * {@link SpawnResult#riders()}. A second rider for the same seat replaces the first.
     */
    VehicleSpawner rider(int seatId, Entity rider);

    /**
     * Whether the server's vehicle limits ({@code MaxVehiclesPerPlayer} for the owner, per chunk, per level and
     * per kind) can refuse the vehicle, as they refuse a player placing one. Off by default: addons usually
     * spawn vehicles for their own reasons (a minigame, a shop), and decide themselves.
     */
    VehicleSpawner respectLimits(boolean respect);

    /**
     * Whether the vehicle needs free space where it is placed, as when a player places one. On by default; off,
     * it is placed even inside blocks or other entities.
     */
    VehicleSpawner requireFreeSpace(boolean require);

    /** Places the vehicle. Never throws for a refusal; the result says what happened. */
    SpawnResult spawn();
}
