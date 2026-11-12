package com.ivomens.mcheli.api.vehicle.action;

import com.ivomens.mcheli.api.content.WeaponInfo;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.data.MutableVehicleData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Changes one vehicle: its health, fuel, ammunition, crew, owner and lock, and the addons' data on it. The
 * read-only {@link Vehicle} view never changes anything; this does. Get one with
 * {@code McheliApi.get().vehicleActions().control(vehicle)}; it is a thin handle, so getting a new one each time
 * costs nothing.
 *
 * <pre>{@code
 * VehicleControl control = McheliApi.get().vehicleActions().control(vehicle);
 * control.repair();
 * control.refuel();
 * control.rearm();
 * if (control.eject(1) == ActionResult.SEAT_EMPTY) { ... }
 * }</pre>
 *
 * <p>The actions are what the game does itself: damage goes through the vehicle's own damage handling (armor,
 * hit parts, events), a dismounted rider leaves at the seat's exit as if they got off, and changes reach the
 * clients the usual way. They are privileged: no action checks a player's permission or the vehicle's lock, so
 * an addon that acts for a player checks {@link #mayBoard} or {@link #mayChangeLock} itself.
 *
 * <p>Seats are numbered as in {@link com.ivomens.mcheli.api.vehicle.Crew}: 0 is the pilot, 1 and up the
 * passenger seats. Weapon slots are numbered from 0 in the order the vehicle type lists its weapons, up to
 * {@link #weaponCount()} (exclusive).
 *
 * <p><b>Side and thread.</b> Server only, on the server thread.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface VehicleControl {

    /** The vehicle this handle changes. */
    Vehicle vehicle();

    // Health

    /** Hit points left, from 0 to {@link #maxHealth()}. */
    int health();

    /** The vehicle type's hit points. */
    int maxHealth();

    /** Repairs all damage. {@link ActionResult#NO_CHANGE} at full health; a wreck cannot be repaired. */
    ActionResult repair();

    /**
     * Repairs up to {@code amount} hit points, never above {@link #maxHealth()}.
     *
     * @throws IllegalArgumentException when the amount is not positive
     */
    ActionResult repair(int amount);

    /**
     * Damages the vehicle as a hit would: through the vehicle's own damage handling, so armor, the damage
     * config, friendly fire, invulnerability and the damage events all apply, and the vehicle is destroyed when
     * its hit points run out. A player attacker's punch may pick the vehicle up, as the player's own would.
     *
     * @return {@link ActionResult#SUCCESS} when the hit landed, {@link ActionResult#REFUSED} when the vehicle
     * ignored it (invulnerable, iron curtain, absorbed by armor)
     */
    ActionResult damage(DamageSource source, float amount);

    /**
     * Destroys the vehicle as if its hit points ran out: it becomes a wreck, {@code VehicleDestroyed} fires and
     * it explodes. {@link ActionResult#NO_CHANGE} when it already is a wreck. To remove a vehicle without a
     * wreck, discard its entity.
     *
     * @param attacker who destroyed it, for the event and the explosion; may be null
     */
    ActionResult destroy(@Nullable Entity attacker);

    // Fuel

    /** The fuel in the tank, from 0 to {@link #fuelCapacity()}. */
    int fuel();

    /** The size of the tank; 0 for vehicle types without one. */
    int fuelCapacity();

    /** Fills the tank; an empty tank is filled with the default fuel. */
    ActionResult refuel();

    /**
     * Sets the fuel in the tank, capped at {@link #fuelCapacity()}; an empty tank is filled with the default
     * fuel.
     *
     * @throws IllegalArgumentException when the amount is negative
     */
    ActionResult setFuel(int amount);

    // Weapons

    /** The number of weapon slots. */
    int weaponCount();

    /** The weapon type in the slot; empty for a slot the vehicle does not have. */
    Optional<WeaponInfo> weapon(int slot);

    /** The rounds of the weapon in the slot, loaded and in reserve; 0 for a slot the vehicle does not have. */
    int ammo(int slot);

    /** The most rounds the weapon in the slot carries; 0 for a slot the vehicle does not have. */
    int maxAmmo(int slot);

    /** Fills every weapon's magazine and reserve. */
    ActionResult rearm();

    /** Fills the magazine and reserve of the weapon in the slot. */
    ActionResult rearm(int slot);

    /**
     * Sets the rounds of the weapon in the slot, capped at {@link #maxAmmo(int)}: the magazine is filled first
     * and the rest goes to the reserve.
     *
     * @throws IllegalArgumentException when the amount is negative
     */
    ActionResult setAmmo(int slot, int amount);

    // Crew

    /**
     * Seats the entity, which leaves whatever it rides first (another seat of this vehicle included). A player
     * in the pilot seat boards as if they had clicked the vehicle. Locks do not apply.
     */
    ActionResult board(Entity rider, int seatId);

    /** Puts whoever sits in the seat out at the seat's exit, as if they got off. */
    ActionResult eject(int seatId);

    /** Puts everyone aboard out at their seats' exits; returns how many left. */
    int ejectAll();

    /** Puts the entity out at its seat's exit, as if it got off. */
    ActionResult dismount(Entity rider);

    // Owner and lock

    /** The owner's UUID; empty for a vehicle without an owner. */
    Optional<UUID> owner();

    /** The owner's name when they last placed, locked or were given the vehicle; empty without an owner. */
    Optional<String> ownerName();

    /** Makes the player the owner, as if they had placed the vehicle. A lock stays as it was. */
    ActionResult setOwner(Player owner);

    /** Makes the player with this UUID and name the owner, who need not be online. A lock stays as it was. */
    ActionResult setOwner(UUID owner, String name);

    /** Leaves the vehicle without an owner, which also unlocks it. */
    ActionResult clearOwner();

    /** Whether the vehicle is locked now: its owner locked it and the server allows locking. */
    boolean isLocked();

    /**
     * Locks or unlocks the vehicle. Only a vehicle with an owner can be locked ({@link ActionResult#NO_OWNER}),
     * and only while the server allows locking ({@link ActionResult#LOCKING_DISABLED}); the lock rules of the
     * admin config (team members, passenger seats, operators) decide who it keeps out.
     */
    ActionResult setLocked(boolean locked);

    /** Whether the lock lets the player take the seat, as when they click the vehicle. */
    boolean mayBoard(Player player, int seatId);

    /** Whether the player may lock and unlock the vehicle: its owner or an operator. */
    boolean mayChangeLock(Player player);

    // Addon data

    /** The addons' data on the vehicle, for changing it. */
    MutableVehicleData data();
}
