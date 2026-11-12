package com.ivomens.mcheli.bench;

import net.minecraft.world.phys.Vec3;

/**
 * Where the in-game benchmark puts things, worked out from the point the player stood on when it
 * started, so the server (which spawns the vehicles) and the client (which flies the camera) agree
 * without talking. The parked vehicles stand in rows of {@value #PER_ROW} south of the start, the
 * moving ones drive along a lane in front of the first row, the camera helicopter waits at the start
 * facing the rows, and the orbit camera circles the middle of the rows.
 */
public record BenchmarkLayout(Vec3 start, int vehicles) {

    public static final int PER_ROW = 15;
    private static final double COLUMN_GAP = 14.0;
    private static final double ROW_GAP = 18.0;
    /** From the start to the first row, in blocks. */
    private static final double FIRST_ROW = 24.0;
    private static final double LANE = 12.0;
    private static final double ORBIT_HEIGHT = 30.0;
    private static final double MIN_ORBIT_RADIUS = 60.0;

    /** One vehicle in ten drives back and forth along the lane. */
    public int moving() {
        return this.vehicles / 10;
    }

    public int parked() {
        return this.vehicles - moving();
    }

    public int rows() {
        return (parked() + PER_ROW - 1) / PER_ROW;
    }

    /** Ground position (y of the start) of parked vehicle {@code index}. */
    public Vec3 parkedPosition(int index) {
        int column = index % PER_ROW;
        int row = index / PER_ROW;
        return new Vec3(this.start.x + (column - (PER_ROW - 1) / 2.0) * COLUMN_GAP, this.start.y,
                this.start.z + FIRST_ROW + row * ROW_GAP);
    }

    /** The middle of the lane the moving vehicles drive along. */
    public Vec3 laneCentre() {
        return new Vec3(this.start.x, this.start.y, this.start.z + LANE);
    }

    /** What the orbit camera looks at: the middle of the parked rows. */
    public Vec3 centre() {
        return new Vec3(this.start.x, this.start.y, this.start.z + FIRST_ROW + Math.max(0, rows() - 1) * ROW_GAP / 2);
    }

    public double orbitRadius() {
        return Math.max(MIN_ORBIT_RADIUS, rows() * ROW_GAP / 2 + 50.0);
    }

    /**
     * Camera position at {@code turn} (0 to 1) around the orbit, starting on the start's side of the rows
     * (north) and going round anticlockwise seen from above.
     */
    public Vec3 orbitPosition(double turn) {
        double angle = Math.PI * 2 * turn;
        Vec3 c = centre();
        return new Vec3(c.x - Math.sin(angle) * orbitRadius(), c.y + ORBIT_HEIGHT,
                c.z - Math.cos(angle) * orbitRadius());
    }
}
