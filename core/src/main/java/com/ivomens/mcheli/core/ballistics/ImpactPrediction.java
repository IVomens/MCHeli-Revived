package com.ivomens.mcheli.core.ballistics;

import javax.annotation.Nullable;

/**
 * Where an unguided shot will land (the CCIP sight): its flight integrated step by step with the weapon's
 * gravity and water drag until it hits something. The world is asked through {@link Surroundings}, which
 * never loads a chunk.
 */
public final class ImpactPrediction {

    /** The longest flight followed, in steps. */
    public static final int MAX_STEPS = 512;

    private ImpactPrediction() {
    }

    /**
     * How the shot falls.
     *
     * @param gravity         added to the vertical motion every step in air
     * @param gravityInWater  added instead while under water
     * @param velocityInWater the motion is multiplied by this every step under water, when it is above 0
     */
    public record Fall(double gravity, double gravityInWater, double velocityInWater) {
    }

    /**
     * What the flight passes through.
     *
     * @param <H> where a hit is reported, in the caller's own type
     */
    public interface Surroundings<H> {

        /** Whether the chunk holding the position is loaded. */
        boolean isLoaded(double x, double y, double z);

        /** Whether the block at the position is water. */
        boolean isWater(double x, double y, double z);

        /** The first block hit on the segment between the two positions, or null when it is clear. */
        @Nullable
        H hit(double fromX, double fromY, double fromZ, double toX, double toY, double toZ);
    }

    /**
     * The flight of a shot launched at (x, y, z) with the motion (mx, my, mz).
     *
     * @param stepFactor how many steps of its motion the shot moves per tick ({@link ProjectileSteps})
     * @param maxSteps   the longest flight to follow
     * @param lowestY    the flight is given up once it falls below this height
     * @return the hit, or null when the shot leaves the loaded area, falls too low or flies too long
     */
    @Nullable
    public static <H> H impact(double x, double y, double z, double mx, double my, double mz, Fall fall,
                               double stepFactor, int maxSteps, double lowestY, Surroundings<H> world) {
        for (int i = 0; i < maxSteps; i++) {
            if (!world.isLoaded(x, y, z)) {
                return null;
            }
            if (world.isWater(x, y, z)) {
                my += fall.gravityInWater();
                if (fall.velocityInWater() > 0.0F) {
                    mx *= fall.velocityInWater();
                    my *= fall.velocityInWater();
                    mz *= fall.velocityInWater();
                }
            } else {
                my += fall.gravity();
            }
            double nextX = x + mx * stepFactor;
            double nextY = y + my * stepFactor;
            double nextZ = z + mz * stepFactor;
            if (!world.isLoaded(nextX, nextY, nextZ)) {
                return null;
            }
            H hit = world.hit(x, y, z, nextX, nextY, nextZ);
            if (hit != null) {
                return hit;
            }
            x = nextX;
            y = nextY;
            z = nextZ;
            if (y < lowestY) return null;
        }
        return null;
    }
}
