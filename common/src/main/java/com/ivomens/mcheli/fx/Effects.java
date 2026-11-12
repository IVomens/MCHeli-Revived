package com.ivomens.mcheli.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The particle effects common code can ask for. A client draws them in the style its config picks
 * (the legacy particles or the modern ones); a dedicated server draws nothing. Every method must only
 * be called for a client level, as the callers have always checked.
 */
public interface Effects {

    /** Draws nothing. */
    Effects NONE = new Effects() {
    };

    /** The effects in the style the config asks for right now. */
    static Effects get() {
        return EffectStyle.INSTANCE.effects();
    }

    /** The mod's own smoke, or its splash for the name {@code Splash}. */
    default void spawn(ParticleParams params) {
    }

    /**
     * A vanilla particle by its old name ({@code smoke}, {@code largeexplode}, {@code blockdust}, ...).
     *
     * @param params block state id for block particles, item id for {@code iconcrack}
     */
    default void vanilla(String name, double x, double y, double z, double mx, double my, double mz, int... params) {
    }

    /** Cracks of the block at the given block position, if it is not air. */
    default void blockCrack(Level level, int blockX, int blockY, int blockZ, double x, double y, double z,
                            double mx, double my, double mz) {
    }

    /** Dust of the block, or of the first non-air neighbour; whether any was drawn. */
    default boolean blockDust(Level level, int blockX, int blockY, int blockZ, double x, double y, double z,
                              double mx, double my, double mz, float scale) {
        return false;
    }

    /** The flash at a gun's muzzle. */
    default void muzzleFlash(Level level, double x, double y, double z, float size, float r, float g, float b,
                             float a, int age) {
    }

    /** A cloud puff in the given colour that drifts sideways by up to {@code spread}. */
    default void tintedCloud(double x, double y, double z, float r, float g, float b, float spread,
                             RandomSource random) {
    }

    /**
     * Debris and smoke where a bullet hit a block: {@code cracks} pieces of the block, plus up to two
     * more, flying about {@code spread}, and {@code clouds} puffs of smoke.
     */
    default void bulletDebris(Level level, Vec3 at, BlockPos block, float width, int cracks, float spread, int clouds,
                              RandomSource random) {
    }

    /** Red sparks where a bullet hit a vehicle protected by its iron curtain. */
    default void ironCurtainSparks(Level level, Vec3 at, BlockPos block, float width, float height, int cracks,
                                   float spread, RandomSource random) {
    }

    /**
     * Draws a whole explosion as one effect, if this style has one, and says whether it did. The legacy
     * style has none: its explosions are built from debris, smoke and dust.
     */
    default boolean explosion(double x, double y, double z, float size) {
        return false;
    }

    /**
     * Whether {@link #explosion} always draws the whole explosion, so the blocks it affected are never
     * needed; the server only sends them to clients whose style says no.
     */
    default boolean drawsWholeExplosions() {
        return false;
    }

    /** Like {@link #explosion}, for the water an explosion throws up. */
    default boolean waterSplash(double x, double y, double z, float size) {
        return false;
    }
}
