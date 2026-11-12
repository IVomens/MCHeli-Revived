package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.combat.CombatRandom;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.sound.WorldSounds;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** The steps most fire modes share: aiming, launching a projectile, recoil and the fire sound. */
public final class Shots {

    private Shots() {
    }

    /**
     * The fire direction of the shot, clamped to what the mount may reach and with the yaw wrapped.
     *
     * @param limitYaw  clamp the yaw to the mount's arc as well as the pitch
     * @param seatPitch use the seat's pitch limits instead of the weapon's
     */
    public static Vec2 rotation(Shot shot, boolean limitYaw, boolean seatPitch) {
        Vec2 aim = shot.aimOrUserAim();
        Vec2 clamped = shot.shooter().clampAim(shot.mount(), aim.x, aim.y, limitYaw, seatPitch);
        return new Vec2(Mth.wrapDegrees(clamped.x), clamped.y);
    }

    /** Unit vector along the shot's direction, the platform's roll included. */
    public static Vec3 launchDirection(Shot shot) {
        return Rotations.rotate(0.0, 0.0, 1.0, -shot.yaw(), -shot.pitch(), -shot.roll());
    }

    /**
     * Random offset of (yaw, pitch) inside a circular cone whose diameter is {@code accuracy} degrees,
     * uniformly distributed over the cone's cross section.
     */
    public static Vec2 scatter(RandomSource random, float yaw, float pitch, float accuracy) {
        if (accuracy <= 0.0F) {
            return new Vec2(yaw, pitch);
        }
        double radius = Math.sqrt(random.nextDouble()) * (accuracy * 0.5);
        double theta = random.nextDouble() * 2 * Math.PI;
        return new Vec2(yaw + (float) (radius * Math.cos(theta)), pitch + (float) (radius * Math.sin(theta)));
    }

    /**
     * A projectile of the weapon at the shot's muzzle, moving at the mount's speed along the direction:
     * named after the weapon, with the mount's warhead and the shooter's names.
     */
    public static ProjectileEntity projectile(Shot shot, ProjectileKind kind, Vec3 direction, float yaw, float pitch,
                                              double speed) {
        ProjectileEntity projectile = ProjectileEntity.create(kind, shot.level(), shot.origin(), direction, yaw, pitch,
                speed);
        arm(projectile, shot);
        return projectile;
    }

    /** Names the projectile after the shot's weapon and gives it the mount's warhead and the shooters. */
    public static void arm(ProjectileEntity projectile, Shot shot) {
        WeaponMount mount = shot.mount();
        MountStats stats = mount.stats();
        projectile.setWeapon(mount.name());
        projectile.warhead().set(stats.power(), stats.explosionPower(), stats.explosionPowerInWater(), stats.piercing());
        projectile.setShooter(shot.user(), shot.platform());
        projectile.fuzes().setAirburstDistance(mount.airburstDistance());
    }

    /** A gun round along (yaw, pitch), not yet spawned (see {@link #spawnHalfStepAhead}). */
    public static ProjectileEntity round(Shot shot, ProjectileKind kind, float yaw, float pitch) {
        Vec3 motion = Vec3.directionFromRotation(pitch, yaw);
        return projectile(shot, kind, motion, yaw, pitch, shot.mount().stats().speed());
    }

    /** Spawns a round half a step ahead of the muzzle, so it clears the barrel and the hull. */
    public static void spawnHalfStepAhead(Level level, ProjectileEntity round) {
        Vec3 motion = round.getDeltaMovement();
        round.setPos(round.getX() + motion.x * 0.5, round.getY() + motion.y * 0.5, round.getZ() + motion.z * 0.5);
        level.addFreshEntity(round);
    }

    /** Client: kicks the local player's view by the weapon's recoil. */
    public static void applyRecoil(Shot shot) {
        WeaponSpec info = shot.mount().spec();
        RandomSource random = CombatRandom.of(shot.level()).aim();
        float pitch = info.recoil().pitch() + random.nextFloat() * info.recoil().pitchRange();
        float yaw = info.recoil().yaw() + (random.nextFloat() - 0.5F) * info.recoil().yawRange();
        LocalClient.get().kickView(pitch, yaw, info.recoil().recoverFactor());
    }

    /** Server: the weapon's fire sound at the entity, unless the group is still waiting out a sound delay. */
    public static void playFireSound(Shot shot, @Nullable Entity at) {
        playSound(shot, at, shot.mount().spec().sound().locations().fire());
    }

    public static void playSound(Shot shot, @Nullable Entity at, @Nullable ResourceLocation sound) {
        WeaponSpec info = shot.mount().spec();
        if (at == null || sound == null || at.level().isClientSide || !shot.soundAllowed()) {
            return;
        }
        float pitchRandom = info.sound().pitchRandom();
        float pitch = info.sound().pitch() * (1.0F - pitchRandom)
                + CombatRandom.of(shot.level()).aim().nextFloat() * pitchRandom;
        Level level = shot.level();
        if (shot.mount().isHeavy()) {
            WorldSounds.playHeavySound(level, at.getX(), at.getY(), at.getZ(), sound, info.sound().volume(), pitch);
        } else {
            ModSounds.playSound(level, at.getX(), at.getY(), at.getZ(), sound, info.sound().volume(), pitch);
        }
    }
}
