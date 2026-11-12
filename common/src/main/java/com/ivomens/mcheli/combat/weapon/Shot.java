package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.event.ApiEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * One pull of the trigger as a {@link FireMode} sees it: who fires, from where and in which direction
 * (spread already applied), and what the client asked for.
 *
 * @param origin  the muzzle position
 * @param yaw     fire direction, wrapped to -180..180
 * @param pitch   fire direction, wrapped to -180..180
 * @param roll    the platform's roll, for the few weapons that launch along the full attitude
 * @param request the mode and lock target the client sent
 * @param soundAllowed  whether the fire sound plays; a weapon with a sound delay stays quiet in between
 */
public record Shot(Level level, Shooter shooter, WeaponMount mount, Vec3 origin, float yaw, float pitch, float roll,
                   FireRequest request, boolean soundAllowed) {

    public boolean isClient() {
        return this.level.isClientSide;
    }

    /** Server: asks the API's listeners whether the shot may go ahead ({@code WeaponFired}); a client never asks. */
    boolean allowedByListeners() {
        if (!(this.level instanceof ServerLevel server)) {
            return true;
        }
        Entity platform = this.platform();
        return ApiEvents.weaponFired(server, platform instanceof Vehicle vehicle ? vehicle : null, this.user(), platform,
                this.mount.name(), this.origin, this.yaw, this.pitch);
    }

    @Nullable
    public Entity user() {
        return this.shooter.user();
    }

    public Entity platform() {
        return this.shooter.platform();
    }

    /** The aim of this shot, or of the user (with the mount's fixed angles) when the shot carries none. */
    Vec2 aimOrUserAim() {
        if (this.yaw != 0.0F || this.pitch != 0.0F) {
            return new Vec2(this.yaw, this.pitch);
        }
        Entity base = this.mount.spec().missile().offAxis() && this.user() != null ? this.user() : this.platform();
        return new Vec2(base.getYRot() + this.mount.fixedYaw(), base.getXRot() + this.mount.fixedPitch());
    }
}
