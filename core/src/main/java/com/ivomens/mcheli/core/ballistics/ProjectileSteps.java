package com.ivomens.mcheli.core.ballistics;

/**
 * How far a projectile moves per tick, and which chunks that move crosses.
 * <p>
 * Fast rounds and rockets do not get their speed raised past what the game can sync; they move several
 * steps of their motion per tick instead, the step factor.
 */
public final class ProjectileSteps {

    /** Weapon acceleration that corresponds to one movement step per tick. */
    public static final float SPEED_PER_STEP = 4.0F;

    /** Blocks between the chunk checks along a long move; less than half a chunk, so none is skipped. */
    private static final double CHUNK_PROBE_SPACING = 8.0;

    private ProjectileSteps() {
    }

    /**
     * Steps per tick of a projectile launched with this acceleration.
     *
     * @param multiStep whether its kind moves in several steps at all (rounds and rockets)
     */
    public static double stepFactor(boolean multiStep, float acceleration) {
        return multiStep && acceleration > SPEED_PER_STEP ? acceleration / SPEED_PER_STEP : 1.0;
    }

    /** Whether a chunk, by chunk coordinates, is loaded. */
    @FunctionalInterface
    public interface LoadedChunks {
        boolean has(int chunkX, int chunkZ);
    }

    /**
     * True if every chunk the horizontal segment from (x0, z0) to (x1, z1) crosses is loaded. It looks
     * at the end first, then at the start and points every few blocks along the way.
     */
    public static boolean isSegmentLoaded(double x0, double z0, double x1, double z1, LoadedChunks chunks) {
        int cx0 = floor(x0) >> 4;
        int cz0 = floor(z0) >> 4;
        int cx1 = floor(x1) >> 4;
        int cz1 = floor(z1) >> 4;
        if (!chunks.has(cx1, cz1)) return false;
        if (cx0 == cx1 && cz0 == cz1) return chunks.has(cx0, cz0);
        double dx = x1 - x0;
        double dz = z1 - z0;
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) / CHUNK_PROBE_SPACING);
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            if (!chunks.has(floor(x0 + dx * t) >> 4, floor(z0 + dz * t) >> 4)) return false;
        }
        return true;
    }

    /** The block coordinate of a position, the way the game rounds it ({@code Mth.floor}). */
    public static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }
}
