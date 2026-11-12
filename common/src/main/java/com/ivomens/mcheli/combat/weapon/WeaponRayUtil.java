package com.ivomens.mcheli.combat.weapon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;

/**
 * Long block ray traces (laser designators, TV/AS missile aiming) that never load chunks: the ray
 * is traced in short segments and ends at the first unloaded chunk.
 */
public final class WeaponRayUtil {

    private static final double SEGMENT = 16.0;

    private WeaponRayUtil() {
    }

    /**
     * @return the block hit position, or null when nothing was hit before {@code dst} or before the
     * first unloaded chunk ({@code endAtUnloaded} receives that point if non-null).
     */
    @Nullable
    public static Vec3 clipLoaded(Level level, Vec3 src, Vec3 dst, @Nullable Entity entity, @Nullable Vec3[] endAtUnloaded) {
        BlockHitResult r = clipLoadedResult(level, src, dst, entity, endAtUnloaded);
        return r != null ? r.getLocation() : null;
    }

    /** Same as {@link #clipLoaded} but returns the full block hit. */
    @Nullable
    public static BlockHitResult clipLoadedResult(Level level, Vec3 src, Vec3 dst, @Nullable Entity entity, @Nullable Vec3[] endAtUnloaded) {
        Vec3 dir = dst.subtract(src);
        double len = dir.length();
        if (len < 1.0E-6) return null;
        dir = dir.scale(1.0 / len);
        double done = 0.0;
        Vec3 from = src;
        if (!level.hasChunkAt(BlockPos.containing(from))) {
            if (endAtUnloaded != null) endAtUnloaded[0] = from;
            return null;
        }
        while (done < len) {
            double step = Math.min(SEGMENT, len - done);
            Vec3 to = from.add(dir.scale(step));
            if (!level.hasChunkAt(BlockPos.containing(to))) {
                if (endAtUnloaded != null) endAtUnloaded[0] = to;
                return null;
            }
            ClipContext ctx = entity != null
                    ? new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)
                    : new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
            BlockHitResult r = level.clip(ctx);
            if (r.getType() != HitResult.Type.MISS) {
                return r;
            }
            from = to;
            done += step;
        }
        return null;
    }

    /** Hit position, or the end of the traced (loaded) part of the ray. */
    public static Vec3 clipLoadedOrEnd(Level level, Vec3 src, Vec3 dst, @Nullable Entity entity) {
        Vec3[] end = new Vec3[1];
        Vec3 hit = clipLoaded(level, src, dst, entity, end);
        if (hit != null) return hit;
        return end[0] != null ? end[0] : dst;
    }
}
