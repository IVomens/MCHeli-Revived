package com.ivomens.mcheli.core.sim.port;

/**
 * Questions the motion models ask about the terrain around the body's current position.
 * <p>
 * Implementations answer for wherever the body is when asked, so a probe after the move sees the
 * new position.
 */
public interface TerrainProbe {

    /**
     * True if no solid block lies within {@code depth} blocks below, in a {@code size} wide square
     * column. The column is centred with the legacy {@code (int) (x + 0.5)} rounding, which truncates
     * towards zero and so differs from {@code floor} at negative coordinates.
     */
    boolean isClearBelow(int size, int depth);

    /** Submerged fraction of the hull in fifths, from 0 (dry) to 1 (fully under). */
    double waterDepth();

    /** True if the block right below is water or air, so the hull is not resting on solid ground. */
    boolean isAboveWaterOrAir();
}
