package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A weapon held by a player or mob on foot (or fired from a designator it stands at): the holder is
 * both the user and the platform, and the muzzle turns with the holder's view.
 */
public record HandheldShooter(Entity holder) implements Shooter {

    @Override
    public Entity user() {
        return this.holder;
    }

    @Override
    public Entity platform() {
        return this.holder;
    }

    @Override
    @Nullable
    public ArmedVehicle vehicle() {
        return null;
    }

    @Override
    public Vec3 muzzleOffset(WeaponMount mount) {
        return Rotations.rotate(mount.position(), -this.holder.getYRot(), -this.holder.getXRot(), 0.0F);
    }

    @Override
    public Vec2 barrelAim(WeaponGroup group, WeaponMount mount) {
        return new Vec2(this.holder.getYRot() + group.yaw() + mount.fixedYaw(),
                this.holder.getXRot() + group.pitch() + mount.fixedPitch());
    }
}
