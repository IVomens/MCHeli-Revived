package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.platform.Services;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** The entity a player looks at within arm's reach, for the wrench and for boarding a seat. */
public final class AircraftPicking {

    /** Entities can be picked from this far away. */
    private static final double PICK_REACH = 4.0;

    private AircraftPicking() {
    }

    /** The aircraft the entity is looking at within reach; a seat counts as its aircraft. */
    @Nullable
    public static AircraftEntity lookedAtAircraft(LivingEntity entity) {
        Entity hit = lookedAt(entity);
        if (hit instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        if (hit instanceof SeatEntity seat) {
            return seat.getParent();
        }
        return null;
    }

    /** The entity under the crosshair within reach, unless a block is closer. */
    @Nullable
    public static Entity lookedAt(LivingEntity user) {
        Vec3 eyePos = user.getEyePosition(1.0F);
        Vec3 reach = user.getViewVector(1.0F).scale(PICK_REACH);
        Vec3 traceEnd = eyePos.add(reach);
        BlockHitResult blockTrace = user.level().clip(new ClipContext(eyePos, traceEnd, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, user));
        double closest = blockTrace.getType() != HitResult.Type.MISS
                ? blockTrace.getLocation().distanceToSqr(eyePos)
                : PICK_REACH * PICK_REACH;

        AABB searchBox = user.getBoundingBox().expandTowards(reach).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(user, eyePos, traceEnd, searchBox,
                e -> !e.isSpectator() && e.isPickable()
                        && (e != user.getVehicle() || Services.GAMEPLAY.canRiderInteract(e)), closest);
        return hit != null ? hit.getEntity() : null;
    }
}
