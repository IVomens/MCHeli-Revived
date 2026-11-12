package com.ivomens.mcheli.aircraft;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Main bounding box of an aircraft. Intersection and ray tests also consider the aircraft's
 * rotated part boxes ({@link PartBoundingBox}), so collision and hit detection see the whole hull.
 * The tests have no side effects: which part a hit lands on is asked separately, through
 * {@link #partTouching} and {@link #partOnRay}, by the damage code.
 * <p>
 * Every derived box (inflate, move, ...) keeps this type so the part test survives vanilla code
 * that grows the box before testing it (e.g. entity picking).
 */
public class AircraftBoundingBox extends AABB {

    private static final double PARALLEL_EPSILON = 1.0E-7;
    private static final double SLAB_EPSILON = 1.0E-6;

    private final AircraftEntity ac;

    public AircraftBoundingBox(AircraftEntity ac, AABB aabb) {
        this(ac, aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    public AircraftBoundingBox(AircraftEntity ac, double x1, double y1, double z1, double x2, double y2, double z2) {
        super(x1, y1, z1, x2, y2, z2);
        this.ac = ac;
    }

    private AABB derive(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AircraftBoundingBox(this.ac, x1, y1, z1, x2, y2, z2);
    }

    private static double centerDistanceSqr(AABB a, AABB b) {
        double dx = (a.minX + a.maxX) / 2.0 - (b.minX + b.maxX) / 2.0;
        double dy = (a.minY + a.maxY) / 2.0 - (b.minY + b.maxY) / 2.0;
        double dz = (a.minZ + a.maxZ) / 2.0 - (b.minZ + b.maxZ) / 2.0;
        return dx * dx + dy * dy + dz * dz;
    }

    /** A popped reactive armour tile no longer exists for collision and hits. */
    private static boolean isGone(PartBoundingBox part) {
        return part.isERA() && !part.eraActive();
    }

    /** Intersects the main box or any part box that is still there. */
    @Override
    public boolean intersects(@NotNull AABB other) {
        if (super.intersects(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ)) {
            return true;
        }
        AABB envelope = this.ac.partBoxes().envelope();
        if (envelope == null || !envelope.intersects(other)) {
            return false;
        }
        for (PartBoundingBox part : this.ac.partBoxes().boxes()) {
            if (touches(part, other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean touches(PartBoundingBox part, AABB area) {
        return !isGone(part) && part.boundingBox().intersects(area) && part.intersectsAABB(area);
    }

    /**
     * The part an area touches: of the parts it touches, the one whose centre is nearest the area's
     * centre, provided it is nearer than the main box's centre when the area touches the main box
     * too. Null when the area lands on the main box or misses.
     */
    @Nullable
    public PartBoundingBox partTouching(AABB area) {
        PartBoundingBox nearest = null;
        double bestDistance = 1.0E7;
        if (super.intersects(area.minX, area.minY, area.minZ, area.maxX, area.maxY, area.maxZ)) {
            bestDistance = centerDistanceSqr(area, this);
        }
        for (PartBoundingBox part : this.ac.partBoxes().boxes()) {
            if (!touches(part, area)) {
                continue;
            }
            double distance = centerDistanceSqr(area, part.boundingBox());
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = part;
            }
        }
        return nearest;
    }

    @Override
    public @NotNull AABB inflate(double x, double y, double z) {
        return derive(this.minX - x, this.minY - y, this.minZ - z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    @Override
    public @NotNull AABB minmax(AABB other) {
        return derive(Math.min(this.minX, other.minX), Math.min(this.minY, other.minY), Math.min(this.minZ, other.minZ),
                Math.max(this.maxX, other.maxX), Math.max(this.maxY, other.maxY), Math.max(this.maxZ, other.maxZ));
    }

    @Override
    public @NotNull AABB expandTowards(double x, double y, double z) {
        return derive(this.minX + Math.min(0.0, x), this.minY + Math.min(0.0, y), this.minZ + Math.min(0.0, z),
                this.maxX + Math.max(0.0, x), this.maxY + Math.max(0.0, y), this.maxZ + Math.max(0.0, z));
    }

    @Override
    public @NotNull AABB contract(double x, double y, double z) {
        double minX = this.minX;
        double minY = this.minY;
        double minZ = this.minZ;
        double maxX = this.maxX;
        double maxY = this.maxY;
        double maxZ = this.maxZ;
        if (x < 0.0) {
            minX -= x;
        } else if (x > 0.0) {
            maxX -= x;
        }
        if (y < 0.0) {
            minY -= y;
        } else if (y > 0.0) {
            maxY -= y;
        }
        if (z < 0.0) {
            minZ -= z;
        } else if (z > 0.0) {
            maxZ -= z;
        }
        return derive(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public @NotNull AABB move(double x, double y, double z) {
        return derive(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    /** Ray intersection; a part box hit closer to the start than the main box wins. */
    @Override
    public @NotNull Optional<Vec3> clip(@NotNull Vec3 start, @NotNull Vec3 end) {
        return Optional.ofNullable(this.clipWithParts(start, end).point());
    }

    /** The part a ray enters first, if that is before it enters the main box; null otherwise. */
    @Nullable
    public PartBoundingBox partOnRay(Vec3 start, Vec3 end) {
        return this.clipWithParts(start, end).part();
    }

    private RayHit clipWithParts(Vec3 start, Vec3 end) {
        Vec3 best = super.clip(start, end).orElse(null);
        PartBoundingBox bestPart = null;
        double bestDistance = best != null ? start.distanceTo(best) : Double.MAX_VALUE;
        double dirX = end.x - start.x;
        double dirY = end.y - start.y;
        double dirZ = end.z - start.z;

        for (PartBoundingBox part : this.ac.partBoxes().boxes()) {
            if (isGone(part)) {
                continue;
            }
            double t = clipPart(part, start, dirX, dirY, dirZ);
            if (Double.isNaN(t)) {
                continue;
            }
            Vec3 hit = new Vec3(start.x + dirX * t, start.y + dirY * t, start.z + dirZ * t);
            double distance = start.distanceTo(hit);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = hit;
                bestPart = part;
            }
        }
        return new RayHit(best, bestPart);
    }

    private record RayHit(@Nullable Vec3 point, @Nullable PartBoundingBox part) {
    }

    /**
     * Slab test of the segment start + t * dir (t in [0, 1]) against the oriented part box, done in
     * the box's local frame. Returns the entry parameter (the exit one when starting inside), or NaN
     * on a miss.
     */
    private static double clipPart(PartBoundingBox part, Vec3 start, double dirX, double dirY, double dirZ) {
        Vec3 ax = part.axisX();
        Vec3 ay = part.axisY();
        Vec3 az = part.axisZ();
        double relX = start.x - part.center().x;
        double relY = start.y - part.center().y;
        double relZ = start.z - part.center().z;
        double localDirX = dirX * ax.x + dirY * ax.y + dirZ * ax.z;
        double localDirY = dirX * ay.x + dirY * ay.y + dirZ * ay.z;
        double localDirZ = dirX * az.x + dirY * az.y + dirZ * az.z;
        double localX = relX * ax.x + relY * ax.y + relZ * ax.z;
        double localY = relX * ay.x + relY * ay.y + relZ * ay.z;
        double localZ = relX * az.x + relY * az.y + relZ * az.z;

        double tMin = 0.0;
        double tMax = 1.0;
        if (Math.abs(localDirX) > PARALLEL_EPSILON) {
            double t1 = (-part.halfWidth() - localX) / localDirX;
            double t2 = (part.halfWidth() - localX) / localDirX;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        } else if (Math.abs(localX) > part.halfWidth() + SLAB_EPSILON) {
            return Double.NaN;
        }
        if (Math.abs(localDirY) > PARALLEL_EPSILON) {
            double t1 = (-part.halfHeight() - localY) / localDirY;
            double t2 = (part.halfHeight() - localY) / localDirY;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        } else if (Math.abs(localY) > part.halfHeight() + SLAB_EPSILON) {
            return Double.NaN;
        }
        if (Math.abs(localDirZ) > PARALLEL_EPSILON) {
            double t1 = (-part.halfDepth() - localZ) / localDirZ;
            double t2 = (part.halfDepth() - localZ) / localDirZ;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        } else if (Math.abs(localZ) > part.halfDepth() + SLAB_EPSILON) {
            return Double.NaN;
        }

        if (tMax < tMin || tMin > 1.0 || tMax < 0.0) {
            return Double.NaN;
        }
        return tMin < 0.0 ? tMax : tMin;
    }
}
