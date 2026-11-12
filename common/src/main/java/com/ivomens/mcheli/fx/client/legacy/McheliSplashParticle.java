package com.ivomens.mcheli.fx.client.legacy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Water thrown up by an impact: falls back down and splashes again where it lands in water. */
public class McheliSplashParticle extends BaseParticle {

    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    public McheliSplashParticle(Level level, double x, double y, double z, double mx, double my, double mz) {
        super(level, x, y, z, mx, my, mz);
        this.rCol = this.gCol = this.bCol = this.random.nextFloat() * 0.3F + 0.7F;
        this.setParticleScale(this.random.nextFloat() * 0.5F + 5.0F);
        this.setParticleMaxAge((int) (80.0 / (this.random.nextFloat() * 0.8 + 0.2)) + 2);
    }

    private boolean isWaterAtCenter() {
        this.probe.set((int) (this.x + 0.5), (int) (this.y + 0.5), (int) (this.z + 0.5));
        return this.level.isLoaded(this.probe) && this.level.getBlockState(this.probe).is(Blocks.WATER);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age < this.lifetime) {
            this.setParticleTextureIndex((int) (8.0 * this.age / this.lifetime));
            this.age++;
        } else {
            this.setExpired();
        }

        this.yd -= 0.06F;
        boolean beforeInWater = this.isWaterAtCenter();
        this.move(this.xd, this.yd, this.zd);
        boolean nowInWater = this.isWaterAtCenter();
        if (this.yd < -0.6 && !beforeInWater && nowInWater) {
            // hitting the water surface throws up spray and bubbles, more the faster it falls
            double count = -this.yd * 10.0;
            for (int i = 0; i < count; i++) {
                this.level.addParticle(
                        ParticleTypes.SPLASH,
                        this.x + 0.5 + (this.random.nextDouble() - 0.5) * 2.0,
                        this.y + this.random.nextDouble(),
                        this.z + 0.5 + (this.random.nextDouble() - 0.5) * 2.0,
                        (this.random.nextDouble() - 0.5) * 2.0,
                        4.0,
                        (this.random.nextDouble() - 0.5) * 2.0);
                this.level.addParticle(
                        ParticleTypes.BUBBLE,
                        this.x + 0.5 + (this.random.nextDouble() - 0.5) * 2.0,
                        this.y - this.random.nextDouble(),
                        this.z + 0.5 + (this.random.nextDouble() - 0.5) * 2.0,
                        (this.random.nextDouble() - 0.5) * 2.0,
                        -0.5,
                        (this.random.nextDouble() - 0.5) * 2.0);
            }
        } else if (this.onGround) {
            this.setExpired();
        }

        this.xd *= 0.9;
        this.zd *= 0.9;
    }
}
