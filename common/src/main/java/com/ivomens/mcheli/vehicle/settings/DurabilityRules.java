package com.ivomens.mcheli.vehicle.settings;

/**
 * How a vehicle takes damage, heals and ends. Server side.
 *
 * @param unbreakable               players cannot break a vehicle by hitting it ({@code PreventingBroken})
 * @param breakableOnlyWithPickaxe  players break a vehicle only with a pickaxe
 * @param creativeDropsItem         whether breaking it in creative drops its item (sneaking inverts it)
 * @param autoRepairAbove           a vehicle repairs itself while its health fraction is at least this
 * @param collisionDamage           whether crashing into the ground or a wall hurts the hull
 * @param collisionDamageFactor     how much a crash hurts, relative to the default
 * @param killCrewWhenDestroyed     whether the crew still aboard die with the vehicle
 * @param wreckSeconds              how long a wreck burns before it despawns
 */
public record DurabilityRules(boolean unbreakable, boolean breakableOnlyWithPickaxe, boolean creativeDropsItem,
                              double autoRepairAbove, boolean collisionDamage, double collisionDamageFactor,
                              boolean killCrewWhenDestroyed, int wreckSeconds) {
}
