package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * A weapon of a vehicle got more ammunition: its pilot paid for it with the weapon's round items (from the
 * vehicle's panel), or a supply vehicle parked nearby restocked it. Fired once per weapon and supply, after the
 * rounds were added. Moving rounds from the reserve into the magazine (reloading) is not rearming, and neither
 * is the free top-up a weapon with infinite ammunition gets as it fires.
 *
 * <p>Server side, on the server thread. Since API 1.1.
 */
public interface VehicleRearmed extends McheliEvent {

    Vehicle vehicle();

    /** The weapon type that was restocked, as {@link com.ivomens.mcheli.api.content.WeaponInfo#id()}. */
    String weaponId();

    /** How many rounds were added, more than 0. */
    int rounds();

    /** Who supplied them: the paying player, or the supply vehicle's entity; null when neither applies. */
    @Nullable
    Entity supplier();
}
