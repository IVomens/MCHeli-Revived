package com.ivomens.mcheli.combat.weapon.net;

import net.minecraft.world.phys.Vec3;

/**
 * Where a radar contact is this tick and how far it moved since the last, in fixed point
 * ({@link #UNITS_PER_BLOCK} per block): the position relative to the message's origin, the motion as
 * this position minus the last. A radar display draws a marker and a distance to a tenth of a block,
 * so 1/256 of a block is finer than anything it shows.
 */
public record RadarContactFix(int entityId, int x, int y, int z, int dx, int dy, int dz) {

    public static final double UNITS_PER_BLOCK = 256.0;

    /** The fix of an entity at {@code pos}, a tick after {@code previous}, against the block {@code origin}. */
    public static RadarContactFix of(int entityId, int originX, int originY, int originZ, Vec3 pos, Vec3 previous) {
        int x = units(pos.x - originX);
        int y = units(pos.y - originY);
        int z = units(pos.z - originZ);
        return new RadarContactFix(entityId, x, y, z, x - units(previous.x - originX),
                y - units(previous.y - originY), z - units(previous.z - originZ));
    }

    /** The position this fix stands for, given the message's origin. */
    public Vec3 position(int originX, int originY, int originZ) {
        return new Vec3(originX + this.x / UNITS_PER_BLOCK, originY + this.y / UNITS_PER_BLOCK,
                originZ + this.z / UNITS_PER_BLOCK);
    }

    /** The position a tick before, given the message's origin. */
    public Vec3 previousPosition(int originX, int originY, int originZ) {
        return new Vec3(originX + (this.x - this.dx) / UNITS_PER_BLOCK, originY + (this.y - this.dy) / UNITS_PER_BLOCK,
                originZ + (this.z - this.dz) / UNITS_PER_BLOCK);
    }

    boolean moving() {
        return (this.dx | this.dy | this.dz) != 0;
    }

    private static int units(double blocks) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(blocks * UNITS_PER_BLOCK)));
    }
}
