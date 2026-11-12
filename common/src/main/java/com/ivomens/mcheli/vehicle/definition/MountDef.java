package com.ivomens.mcheli.vehicle.definition;

import net.minecraft.world.phys.Vec3;

/**
 * One weapon mount: where a weapon of {@code weaponType} sits and how far it can turn. A vehicle
 * with two pods of the same rocket has two mounts of that type.
 *
 * @param weaponType  the weapon definition's name
 * @param pos         the muzzle position, including the global vehicle Y offset
 * @param yaw         the mount's fixed yaw relative to the airframe
 * @param pitch       the mount's fixed pitch relative to the airframe
 * @param pilotCanUse whether the pilot may fire it as well as the seat's rider
 * @param seatId      the seat that fires it
 * @param defaultYaw  the yaw the turret rests at
 * @param turret      whether it turns with the vehicle's turret
 */
public record MountDef(String weaponType, Vec3 pos, float yaw, float pitch, boolean pilotCanUse, int seatId,
                       float defaultYaw, float minYaw, float maxYaw, float minPitch, float maxPitch, boolean turret) {
}
