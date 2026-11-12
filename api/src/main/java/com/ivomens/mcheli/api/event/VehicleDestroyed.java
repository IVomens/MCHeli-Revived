package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * A vehicle's hit points ran out and it became a wreck: it catches fire, ejects its crew and explodes. The
 * wreck stays in the world for a while; {@link VehicleRemoved} follows when it goes. A player breaking a
 * vehicle down into its item does not destroy it.
 *
 * <p>Server side, on the server thread, once per vehicle. Stable since API 1.0.
 */
public interface VehicleDestroyed extends McheliEvent {

    Vehicle vehicle();

    /** Who dealt the final damage, when the damage had a source entity. */
    @Nullable
    Entity attacker();
}
