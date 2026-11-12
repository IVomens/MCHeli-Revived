package com.ivomens.mcheli.combat.explosion;

import com.ivomens.mcheli.combat.CombatRandom;
import com.ivomens.mcheli.platform.Services;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The blocks an explosion reaches: rays from the surface of a 16x16x16 grid, each with a random
 * strength that the blocks it passes use up. Cheaper than it looks:
 * <ul>
 *   <li>a ray never loads a chunk (it stops at the first unloaded one);</li>
 *   <li>explosion resistance is looked up once per block position (rays overlap heavily near the
 *       centre) and positions are collected in a primitive long set;</li>
 *   <li>a ray is capped in length so giant addon explosions cannot stall the tick.</li>
 * </ul>
 */
final class BlockRays {

    /** Upper bound of a single block ray in blocks. */
    private static final float MAX_RAY_LENGTH = 96.0F;
    /** Rays start from the surface of a cube of this many points per edge. */
    private static final int RAY_GRID = 16;
    private static final float RAY_STEP = 0.3F;
    private static final float RAY_STEP_DECAY = 0.22500001F;

    private final Level world;
    private final McheliExplosion explosion;
    @Nullable
    private final Entity exploder;
    private final ExplosionSpec spec;
    private final RandomSource random;
    private final float blockPowerDivisor;
    private final LongOpenHashSet affected = new LongOpenHashSet();
    private final Long2FloatOpenHashMap resistanceCache = new Long2FloatOpenHashMap();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private long lastLoadedChunk = Long.MIN_VALUE;
    private long lastUnloadedChunk = Long.MIN_VALUE;

    BlockRays(Level world, McheliExplosion explosion, @Nullable Entity exploder) {
        this.world = world;
        this.explosion = explosion;
        this.exploder = exploder;
        this.spec = explosion.spec();
        this.random = CombatRandom.of(world).rays();
        this.blockPowerDivisor = Math.max(1.0F, this.spec.blockPower());
        this.resistanceCache.defaultReturnValue(Float.NaN);
    }

    /** Casts every ray and adds the blocks they reach to {@code toBlow}. */
    void collect(List<BlockPos> toBlow) {
        int last = RAY_GRID - 1;
        for (int i = 0; i < RAY_GRID; i++) {
            for (int j = 0; j < RAY_GRID; j++) {
                for (int k = 0; k < RAY_GRID; k++) {
                    if (i == 0 || i == last || j == 0 || j == last || k == 0 || k == last) {
                        this.cast(i / 15.0F * 2.0F - 1.0F, j / 15.0F * 2.0F - 1.0F, k / 15.0F * 2.0F - 1.0F);
                    }
                }
            }
        }
        LongIterator it = this.affected.iterator();
        while (it.hasNext()) {
            toBlow.add(BlockPos.of(it.nextLong()));
        }
    }

    private void cast(double dirX, double dirY, double dirZ) {
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        Vec3 step = new Vec3(dirX / length * RAY_STEP, dirY / length * RAY_STEP, dirZ / length * RAY_STEP);
        float strength = Math.min(this.spec.blockSize() * (0.7F + this.random.nextFloat() * 0.6F), MAX_RAY_LENGTH);
        double rayX = this.spec.at().x;
        double rayY = this.spec.at().y;
        double rayZ = this.spec.at().z;
        for (; strength > 0.0F; strength -= RAY_STEP_DECAY) {
            int blockX = Mth.floor(rayX);
            int blockY = Mth.floor(rayY);
            int blockZ = Mth.floor(rayZ);
            if (blockY < this.world.getMinBuildHeight() || blockY >= this.world.getMaxBuildHeight()
                    || !this.isLoaded(blockX, blockZ)) {
                return;
            }
            strength = this.pass(this.pos.set(blockX, blockY, blockZ), strength);
            rayX += step.x;
            rayY += step.y;
            rayZ += step.z;
        }
    }

    /** Whether the chunk is loaded, remembering the last loaded and unloaded chunk of the rays. */
    private boolean isLoaded(int blockX, int blockZ) {
        long chunk = (((long) (blockX >> 4)) << 32) | ((blockZ >> 4) & 0xFFFFFFFFL);
        if (chunk == this.lastLoadedChunk) {
            return true;
        }
        if (chunk == this.lastUnloadedChunk) {
            return false;
        }
        if (!this.world.hasChunk(blockX >> 4, blockZ >> 4)) {
            this.lastUnloadedChunk = chunk;
            return false;
        }
        this.lastLoadedChunk = chunk;
        return true;
    }

    /** The ray passes the block: its resistance weakens the ray, and the block is reached while it is strong. */
    private float pass(BlockPos blockPos, float strength) {
        long key = blockPos.asLong();
        BlockState state = null;
        float resistance = this.resistanceCache.get(key);
        if (Float.isNaN(resistance)) {
            state = this.world.getBlockState(blockPos);
            resistance = this.resistance(blockPos, state);
            this.resistanceCache.put(key, resistance);
        }
        if (resistance >= 0.0F) {
            if (this.spec.inWater()) {
                resistance *= this.random.nextFloat() * 0.2F + 0.2F;
            }
            resistance /= this.blockPowerDivisor;
            strength -= (resistance + 0.3F) * 0.3F;
        }
        if (strength > 0.0F && !this.affected.contains(key)) {
            boolean explode = true;
            if (this.exploder != null) {
                if (state == null) state = this.world.getBlockState(blockPos);
                explode = this.exploder.shouldBlockExplode(this.explosion, this.world, blockPos, state, strength);
            }
            if (explode) this.affected.add(key);
        }
        return strength;
    }

    /** Explosion resistance of the block, or -1 for air. */
    private float resistance(BlockPos blockPos, BlockState state) {
        if (state.isAir()) {
            return -1.0F;
        }
        float resistance = Services.GAMEPLAY.getExplosionResistance(state, this.world, blockPos, this.explosion);
        if (this.exploder != null) {
            resistance = this.exploder.getBlockExplosionResistance(this.explosion, this.world, blockPos, state,
                    state.getFluidState(), resistance);
        }
        return resistance;
    }
}
