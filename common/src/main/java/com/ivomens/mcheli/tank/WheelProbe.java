package com.ivomens.mcheli.tank;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * One ground-contact probe of a tank's suspension: a small collision box that is pushed towards its mount
 * point every tick and reports whether it touches the ground. It is not a real entity; block collision goes
 * through the vanilla collision API, which never loads chunks.
 */
public final class WheelProbe {

    /** How high a probe climbs onto a block in its way. */
    public static final float STEP_HEIGHT = 1.5F;
    private static final float SIZE = 1.0F;
    /** After its own move a probe settles this far down, so it keeps touching flat ground. */
    private static final double SETTLE = -0.1;

    private final Level level;
    /** Mount point in vehicle space. */
    private final Vec3 mountPoint;
    private final boolean frontOfCenter;
    private boolean touchingGround;
    /** Motion requested for the next {@link #moveTowardsMount}. */
    private double motionX;
    private double motionY;
    private double motionZ;
    private double x;
    private double y;
    private double z;
    private AABB bb = makeBox(0.0, 0.0, 0.0);

    /**
     * @param mountPoint     where the wheel is mounted, in vehicle space
     * @param weightedCenter the hull's weighted center, which tells front wheels from rear ones
     */
    public WheelProbe(Level level, Vec3 mountPoint, Vec3 weightedCenter) {
        this.level = level;
        this.mountPoint = mountPoint;
        this.frontOfCenter = mountPoint.z >= weightedCenter.z;
    }

    private static AABB makeBox(double x, double y, double z) {
        double halfSize = SIZE / 2.0;
        return new AABB(x - halfSize, y, z - halfSize, x + halfSize, y + SIZE, z + halfSize);
    }

    /** Where the wheel is mounted, in vehicle space. */
    public Vec3 mountPoint() {
        return this.mountPoint;
    }

    /** Whether the wheel sits in front of the weighted center. */
    public boolean isFrontOfCenter() {
        return this.frontOfCenter;
    }

    /** Whether the probe touched the ground on its last move, or was counted as touching since. */
    public boolean touchesGround() {
        return this.touchingGround;
    }

    /** Counts the probe as touching the ground until its next move. */
    public void countAsTouchingGround() {
        this.touchingGround = true;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public AABB getBoundingBox() {
        return this.bb;
    }

    public void setPos(double x, double y, double z) {
        if (x != this.x || y != this.y || z != this.z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.bb = makeBox(x, y, z);
        }
    }

    /** Sets the motion the next {@link #moveTowardsMount} tries. */
    public void setMotion(double x, double y, double z) {
        this.motionX = x;
        this.motionY = y;
        this.motionZ = z;
    }

    /** The space the next {@link #moveTowardsMount} can sweep, settling included. */
    public AABB reach() {
        return this.bb.expandTowards(this.motionX, this.motionY + SETTLE, this.motionZ);
    }

    /** Moves by the motion set with {@link #setMotion}, then settles onto the ground below. */
    public void moveTowardsMount(List<VoxelShape> entityShapes) {
        this.move(this.motionX, this.motionY, this.motionZ, entityShapes);
        this.move(0.0, SETTLE, 0.0, entityShapes);
    }

    /**
     * Moves the probe like an entity: collide, try stepping up to {@link #STEP_HEIGHT} when blocked
     * horizontally on the ground, and update {@link #touchesGround}.
     *
     * @param entityShapes collision shapes of nearby solid entities, gathered once per tick by the
     *                     wheel manager (may be empty)
     */
    private void move(double dx, double dy, double dz, List<VoxelShape> entityShapes) {
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            return;
        }

        AABB start = this.bb;
        Vec3 moved = Entity.collideBoundingBox(null, new Vec3(dx, dy, dz), start, this.level, entityShapes);
        boolean blockedHorizontally = moved.x != dx || moved.z != dz;
        boolean landedNow = moved.y != dy && dy < 0.0;
        if ((this.touchingGround || landedNow) && blockedHorizontally) {
            moved = this.stepUp(start, dx, dz, moved, entityShapes);
        }

        this.bb = start.move(moved);
        this.x = (this.bb.minX + this.bb.maxX) / 2.0;
        this.y = this.bb.minY;
        this.z = (this.bb.minZ + this.bb.maxZ) / 2.0;
        this.touchingGround = dy != moved.y && dy < 0.0;
        if (dx != moved.x) {
            this.motionX = 0.0;
        }
        if (dz != moved.z) {
            this.motionZ = 0.0;
        }
    }

    /** The move over the block in the way, if it gets further than {@code blocked}. */
    private Vec3 stepUp(AABB start, double dx, double dz, Vec3 blocked, List<VoxelShape> entityShapes) {
        Vec3 up = Entity.collideBoundingBox(null, new Vec3(0.0, STEP_HEIGHT, 0.0), start, this.level, entityShapes);
        AABB raised = start.move(0.0, up.y, 0.0);
        Vec3 across = Entity.collideBoundingBox(null, new Vec3(dx, 0.0, dz), raised, this.level, entityShapes);
        AABB shifted = raised.move(across.x, 0.0, across.z);
        Vec3 down = Entity.collideBoundingBox(null, new Vec3(0.0, -up.y, 0.0), shifted, this.level, entityShapes);
        return across.horizontalDistanceSqr() > blocked.horizontalDistanceSqr()
                ? new Vec3(across.x, up.y + down.y, across.z) : blocked;
    }
}
