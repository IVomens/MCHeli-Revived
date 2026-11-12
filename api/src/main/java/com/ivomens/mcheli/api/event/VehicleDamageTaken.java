package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * A hit was taken off a vehicle's hit points: what {@link VehicleDamaged} announced, after armor, the hit part's
 * damage factor and the listeners' changes. Fired right after the hit points went down; when they ran out,
 * {@link VehicleDestroyed} follows. Not fired for hits on a wreck, for hits armor reduced to nothing, or for a
 * player's punch that breaks the vehicle down into its item.
 *
 * <p>Meant for hit markers, statistics and kill feeds; to change or stop the damage, listen to
 * {@link VehicleDamaged}, whose event object is no longer valid by the time the damage is known.
 *
 * <p>Server side, on the server thread. Since API 1.1.
 */
public interface VehicleDamageTaken extends McheliEvent {

    Vehicle vehicle();

    DamageSource source();

    /** Who is responsible for the hit, as {@link DamageSource#getEntity()}. */
    @Nullable
    Entity attacker();

    /** The MCHeli weapon type of the hit, as {@link VehicleDamaged#weaponId()}; null for other damage. */
    @Nullable
    String weaponId();

    /** The damage after armor, more than 0; the vehicle lost its whole part in hit points (none below 1). */
    float damage();

    /** The hit points left after the hit; 0 when it destroyed the vehicle. */
    int hp();

    /** The vehicle type's full hit points. */
    int maxHp();
}
