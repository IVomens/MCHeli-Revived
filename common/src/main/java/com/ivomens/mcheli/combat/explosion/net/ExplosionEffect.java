package com.ivomens.mcheli.combat.explosion.net;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What a client needs to draw one explosion: where, how big, whether under water, and the blocks it
 * affected, in the order the server's rays found them. The blocks are only sent to a client whose
 * particle style builds explosions from them (the legacy style); for every other client the list is
 * empty.
 * <p>
 * The position is kept on a grid of {@link #POSITION_STEPS} steps per block, so it travels as small
 * whole numbers; the error, at most 1/8192 of a block, is far below a pixel of a block's texture
 * (1/16). The canonical constructor puts the position on the grid, so what the server builds is what
 * the client decodes.
 */
public record ExplosionEffect(Vec3 pos, float size, boolean inWater, List<BlockPos> affectedBlocks) {

    /** Grid steps per block of a position. */
    public static final int POSITION_STEPS = 4096;
    /** Clients get at most this many affected blocks of one explosion. */
    public static final int MAX_POSITIONS = 4096;

    public ExplosionEffect {
        pos = new Vec3(onGrid(pos.x), onGrid(pos.y), onGrid(pos.z));
        affectedBlocks = List.copyOf(affectedBlocks);
    }

    /**
     * The effect with the affected blocks cut down to {@link #MAX_POSITIONS}, or without blocks.
     *
     * @param blocks whether the receiving client draws the explosion from its blocks
     */
    public static ExplosionEffect of(Vec3 pos, float size, boolean inWater, List<BlockPos> affectedBlocks,
                                     boolean blocks) {
        List<BlockPos> sample = blocks
                ? affectedBlocks.subList(0, Math.min(affectedBlocks.size(), MAX_POSITIONS)) : List.of();
        return new ExplosionEffect(pos, size, inWater, sample);
    }

    /** The same explosion without its blocks. */
    public ExplosionEffect withoutBlocks() {
        return this.affectedBlocks.isEmpty() ? this : new ExplosionEffect(this.pos, this.size, this.inWater, List.of());
    }

    /** The coordinate in grid steps. */
    static long steps(double coordinate) {
        return Math.round(coordinate * POSITION_STEPS);
    }

    private static double onGrid(double coordinate) {
        return steps(coordinate) / (double) POSITION_STEPS;
    }
}
