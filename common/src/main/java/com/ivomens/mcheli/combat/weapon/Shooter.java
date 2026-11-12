package com.ivomens.mcheli.combat.weapon;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Who fires a weapon and what it is mounted on. Weapons ask the shooter where a muzzle is and where it
 * may point, so they never need to know which vehicle (or none) carries them: vehicles answer through
 * their weapon component, handheld weapons through {@link HandheldShooter}.
 */
public interface Shooter {

    /** The player or mob pulling the trigger; null for an unmanned mount. */
    @Nullable
    Entity user();

    /** What the weapon is mounted on: the vehicle, or the user itself for handheld weapons. */
    Entity platform();

    /** The vehicle carrying the weapon, or null for handheld weapons. */
    @Nullable
    ArmedVehicle vehicle();

    /** The muzzle of the mount as an offset from the platform's position, in world space. */
    Vec3 muzzleOffset(WeaponMount mount);

    /** Where the group's barrels point, before any spread: (yaw, pitch), not wrapped. */
    Vec2 barrelAim(WeaponGroup group, WeaponMount mount);

    /**
     * The fire direction clamped to what the mount may reach, such as a tank's turret arc.
     *
     * @param limitYaw  clamp the yaw to the mount's arc as well as the pitch
     * @param seatPitch use the seat's pitch limits instead of the weapon's
     */
    default Vec2 clampAim(WeaponMount mount, float yaw, float pitch, boolean limitYaw, boolean seatPitch) {
        return new Vec2(yaw, pitch);
    }

    /** Players fire on the regular cooldown; everyone else waits the padded one (see {@link Cooldowns}). */
    default boolean isPlayer() {
        return this.user() instanceof Player;
    }
}
