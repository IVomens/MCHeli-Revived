package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import com.ivomens.mcheli.combat.weapon.WeaponRayUtil;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.util.Terrain;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Torpedoes, launched at the definition's speed plus the carrier's. A guided torpedo needs its shooter
 * to aim at a water block within range and homes in on that point; an unguided one runs straight.
 */
public final class TorpedoLaunch implements FireMode {

    private static final double GUIDED_AIM_RANGE_CLIENT = 100.0;
    private static final double GUIDED_AIM_RANGE_SERVER = 150.0;

    @Override
    public boolean predict(Shot shot) {
        return !shot.mount().spec().missile().guidedTorpedo() || this.waterAimedAt(shot) != null;
    }

    @Override
    public boolean fire(Shot shot) {
        WeaponSpec info = shot.mount().spec();
        Entity carrier = shot.platform();
        if (!info.missile().guidedTorpedo()) {
            Vec3 motion = launchMotion(info, carrier, shot.yaw(), shot.pitch());
            this.launch(shot, motion, motion, shot.yaw(), null);
            return true;
        }
        BlockHitResult aimedAt = this.waterAimedAt(shot);
        if (aimedAt == null) {
            return false;
        }
        Entity aim = aimSource(shot);
        Vec3 motion = launchMotion(info, carrier, aim.getYRot(), aim.getXRot());
        this.launch(shot, carrier.getDeltaMovement(), motion, aim.getYRot(), aimedAt.getLocation());
        return true;
    }

    private static Entity aimSource(Shot shot) {
        return shot.user() != null ? shot.user() : shot.platform();
    }

    /** The water block the shooter aims at within range, or null. */
    @Nullable
    private BlockHitResult waterAimedAt(Shot shot) {
        Entity aim = aimSource(shot);
        double range = shot.isClient() ? GUIDED_AIM_RANGE_CLIENT : GUIDED_AIM_RANGE_SERVER;
        Vec3 look = Rotations.rotate(0.0, 0.0, 1.0, -aim.getYRot(), -aim.getXRot(), -shot.roll()).normalize();
        Vec3 src = aim.position();
        BlockHitResult hit = WeaponRayUtil.clipLoadedResult(shot.level(), src, src.add(look.scale(range)), aim, null);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !Terrain.isNearWater(shot.level(),
                hit.getBlockPos())) {
            return null;
        }
        return hit;
    }

    /** The definition's speed along (yaw, pitch) plus the carrier's velocity. */
    private static Vec3 launchMotion(WeaponSpec info, Entity carrier, float yaw, float pitch) {
        return Vec3.directionFromRotation(pitch,
                yaw).scale(info.ballistics().acceleration()).add(carrier.getDeltaMovement());
    }

    /**
     * The torpedo starts at the length of its launch motion; the motion handed to the constructor only
     * seeds where it faces, the real motion is set right after.
     */
    private void launch(Shot shot, Vec3 constructorMotion, Vec3 motion, float yaw, @Nullable Vec3 target) {
        ProjectileEntity torpedo = Shots.projectile(shot, ProjectileKind.TORPEDO, constructorMotion, yaw, 0.0F,
                (float) motion.length());
        torpedo.setDeltaMovement(motion);
        if (target != null) {
            torpedo.aimAt(target);
        }
        shot.level().addFreshEntity(torpedo);
        Shots.playFireSound(shot, shot.platform());
    }
}
