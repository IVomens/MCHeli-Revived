package com.ivomens.mcheli.api.vehicle;

import java.util.List;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Who is aboard a vehicle. Seat 0 is the pilot, who rides the vehicle itself; seats 1 to {@link #count()}
 * are the passenger seats, each a {@link VehicleSeat} entity the passenger rides. Read-only.
 *
 * <p>Both sides, on the thread of the vehicle's level. Stable since API 1.0; implemented by MCHeli only, so a
 * minor version may add methods.
 */
public interface Crew {

    /** The number of passenger seats, not counting the pilot. */
    int count();

    /** Whether the id names the pilot (0) or one of the passenger seats. */
    boolean isValidSeatId(int seatId);

    /** The entity in the seat, or null when it is empty. */
    @Nullable
    Entity riderOf(int seatId);

    /** The seat the entity sits in, or -1 when it is not aboard. */
    int seatIdOf(@Nullable Entity entity);

    /** The entities sitting in the passenger seats, in seat order; empty seats are skipped. */
    List<Entity> passengers();

    /** Whether the entity is the pilot or sits in one of the seats. */
    boolean isAboard(@Nullable Entity entity);
}
