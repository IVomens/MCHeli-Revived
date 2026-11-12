package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.entityview.TowChain;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleHitBox;
import com.ivomens.mcheli.api.vehicle.VehicleSeat;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.config.McheliConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * What a projectile runs into on this tick's path: blocks (breaking glass and the like on the way),
 * then the nearest entity it may hit, using a box swept along the motion.
 */
public final class Collisions {

    /**
     * Extra margin of the entity query box around the swept path. Entity lookups only scan chunk
     * sections within 2 blocks of the box, so large aircraft whose centre is further away would be
     * missed with a perfectly tight box.
     */
    private static final double LARGE_ENTITY_MARGIN = 8.0;
    private static final float ENTITY_HIT_INFLATION = 0.3F;
    /** Slack of the quick segment test, far above any rounding of the clip test it stands in front of. */
    private static final double SEGMENT_MARGIN = 1.0E-3;
    /** Block ray traces through at most this many breakable blocks (glass...) per tick. */
    private static final int MAX_BLOCKS_BROKEN_PER_TICK = 4;
    private static final IgnoredClasses IGNORED = new IgnoredClasses();

    private Collisions() {
    }

    /** Hits the block or entity on this tick's path; with a delay fuze, bounces off blocks instead. */
    static void collide(ProjectileEntity projectile) {
        WeaponSpec info = projectile.spec();
        Vec3 path = projectile.getDeltaMovement().scale(projectile.stepFactor());
        BlockHitResult blockHit = clipBlocks(projectile, path);
        if (info.explosion().fuseDelay() > 0) {
            if (blockHit != null) {
                bounce(projectile, blockHit.getDirection(), info.bound());
                projectile.fuzes().startDelay(info);
            }
            return;
        }
        Vec3 from = projectile.position();
        Vec3 to = blockHit != null ? blockHit.getLocation() : from.add(path);
        EntityHitResult entityHit = findEntityOnPath(projectile, from, to);
        HitResult hit = entityHit != null ? entityHit : blockHit;
        if (hit != null) {
            projectile.impact(hit);
        }
    }

    /**
     * Block ray trace along the path, breaking the config's bullet-breakable blocks (glass...) on the
     * way on the server. Returns the block hit, or null.
     */
    @Nullable
    private static BlockHitResult clipBlocks(ProjectileEntity projectile, Vec3 path) {
        Level level = projectile.level();
        BlockHitResult hit = null;
        Vec3 from = projectile.position();
        Vec3 to = from.add(path.x, path.y, path.z);
        for (int i = 0; i <= MAX_BLOCKS_BROKEN_PER_TICK; i++) {
            hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return null;
            }
            if (projectile.shooter() == null) break;
            BlockPos blockPos = hit.getBlockPos();
            Block block = level.getBlockState(blockPos).getBlock();
            if (!McheliConfig.combat().blocks().bulletBreakable().contains(block)) break;
            if (level.isClientSide) {
                // the server breaks it; the client just lets the round pass
                return null;
            }
            level.destroyBlock(blockPos, true);
        }
        return hit;
    }

    /** Bounces off the hit block face with the weapon's bounce factor. */
    private static void bounce(ProjectileEntity projectile, Direction side, float bound) {
        Vec3 m = projectile.getDeltaMovement();
        double mx = m.x;
        double my = m.y;
        double mz = m.z;
        double px = projectile.getX();
        double pz = projectile.getZ();
        switch (side) {
            case DOWN -> my = my > 0.0 ? -my * bound : my;
            case UP -> my = my < 0.0 ? -my * bound : my;
            case NORTH -> {
                if (mz > 0.0) mz = -mz * bound;
                else pz += mz;
            }
            case SOUTH -> {
                if (mz < 0.0) mz = -mz * bound;
                else pz += mz;
            }
            case WEST -> {
                if (mx > 0.0) mx = -mx * bound;
                else px += mx;
            }
            case EAST -> {
                if (mx < 0.0) mx = -mx * bound;
                else px += mx;
            }
        }
        if (bound <= 0.0F) {
            mx *= 0.25;
            my *= 0.25;
            mz *= 0.25;
        }
        projectile.setDeltaMovement(mx, my, mz);
        if (px != projectile.getX() || pz != projectile.getZ()) {
            projectile.setPos(px, projectile.getY(), pz);
        }
        ProjectileSync.disturbed(projectile);
    }

    /**
     * Closest entity the projectile may hit on the segment, using a box swept along the motion: what
     * {@code ProjectileUtil.getEntityHitResult} finds with {@link #canHit} as its filter. The entities are
     * visited in the same order and the nearest hit wins the same way, but the cheap test comes first: an
     * entity whose box the segment misses is not asked whether it may be hit. In a firefight the swept box
     * holds hundreds of other rounds, and the segment touches almost none of them.
     */
    @Nullable
    private static EntityHitResult findEntityOnPath(ProjectileEntity projectile, Vec3 from, Vec3 to) {
        AABB box = projectile.getBoundingBox().expandTowards(to.subtract(from)).inflate(LARGE_ENTITY_MARGIN);
        NearestHit nearest = new NearestHit(projectile, from, to);
        projectile.level().getEntities(projectile, box, nearest);
        return nearest.entity != null ? new EntityHitResult(nearest.entity, nearest.entity.position()) : null;
    }

    /** Visits the candidates of {@link #findEntityOnPath}, keeping the nearest; never collects any. */
    private static final class NearestHit implements Predicate<Entity> {

        private final ProjectileEntity projectile;
        private final Vec3 from;
        private final Vec3 to;
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;
        private double distanceSq = Double.MAX_VALUE;
        @Nullable
        private Entity entity;

        NearestHit(ProjectileEntity projectile, Vec3 from, Vec3 to) {
            this.projectile = projectile;
            this.from = from;
            this.to = to;
            this.minX = Math.min(from.x, to.x);
            this.minY = Math.min(from.y, to.y);
            this.minZ = Math.min(from.z, to.z);
            this.maxX = Math.max(from.x, to.x);
            this.maxY = Math.max(from.y, to.y);
            this.maxZ = Math.max(from.z, to.z);
        }

        @Override
        public boolean test(Entity candidate) {
            AABB box = candidate.getBoundingBox();
            // a plain box the segment cannot reach is skipped; an aircraft's box also clips its part boxes
            if (box.getClass() == AABB.class && !this.segmentNear(box)) {
                return false;
            }
            Optional<Vec3> clip = box.inflate((double) ENTITY_HIT_INFLATION).clip(this.from, this.to);
            if (clip.isPresent()) {
                double distanceSq = this.from.distanceToSqr(clip.get());
                if (distanceSq < this.distanceSq && canHit(this.projectile, candidate)) {
                    this.entity = candidate;
                    this.distanceSq = distanceSq;
                }
            }
            return false;
        }

        /** Whether the segment's bounds reach the box grown by the hit inflation (with a margin for rounding). */
        private boolean segmentNear(AABB box) {
            double grow = ENTITY_HIT_INFLATION + SEGMENT_MARGIN;
            return this.maxX >= box.minX - grow && this.minX <= box.maxX + grow && this.maxY >= box.minY - grow
                    && this.minY <= box.maxY + grow && this.maxZ >= box.minZ - grow && this.minZ <= box.maxZ + grow;
        }
    }

    /**
     * Whether the projectile may hit the entity: not its shooter, the shooter's vehicle and crew, chains,
     * seats and hit boxes, projectiles of the same shooter, or classes the config tells bullets to ignore.
     */
    public static boolean canHit(ProjectileEntity projectile, @Nullable Entity entity) {
        if (entity == null || !entity.isPickable()) return false;
        boolean excludedType = switch (entity) {
            case TowChain ignored -> true;
            case VehicleSeat ignored -> true;
            case VehicleHitBox ignored -> true;
            case ProjectileEntity other -> projectile.level().isClientSide
                    || sameEntity(other.platform(), projectile.platform())
                    || sameEntity(other.shooter(), projectile.shooter());
            default -> false;
        };
        if (excludedType) return false;
        if (sameEntity(entity, projectile.shooter())) return false;
        if (projectile.platform() instanceof Vehicle vehicle
                && (sameEntity(entity, vehicle.entity()) || vehicle.seats().isAboard(entity))) {
            return false;
        }
        return !IGNORED.isIgnored(entity);
    }

    /** True for the same entity, or both null; entities are compared by id. */
    static boolean sameEntity(@Nullable Entity a, @Nullable Entity b) {
        return (a != null ? a.getId() : -1) == (b != null ? b.getId() : -1);
    }

    /** Entity classes matched by the config's bullet ignore list, remembered per list the config hands out. */
    private static final class IgnoredClasses {

        private final Map<Class<?>, Boolean> matches = new ConcurrentHashMap<>();
        private volatile List<String> source;
        private volatile int sourceSize;

        boolean isIgnored(Entity entity) {
            List<String> ignoreList = McheliConfig.combat().ignoredBulletHits();
            if (ignoreList.isEmpty()) return false;
            if (ignoreList != this.source || ignoreList.size() != this.sourceSize) {
                this.matches.clear();
                this.source = ignoreList;
                this.sourceSize = ignoreList.size();
            }
            return this.matches.computeIfAbsent(entity.getClass(), type -> {
                String className = type.getName().toLowerCase(Locale.ROOT);
                for (String s : ignoreList) {
                    if (className.contains(s.toLowerCase(Locale.ROOT))) return true;
                }
                return false;
            });
        }
    }
}
