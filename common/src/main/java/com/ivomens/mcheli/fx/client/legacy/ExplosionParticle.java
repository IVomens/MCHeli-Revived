package com.ivomens.mcheli.fx.client.legacy;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/** Muzzle flash: a short flip-book from the 4x2 frame sheet {@code textures/flash.png}, full bright. */
public class ExplosionParticle extends BaseParticle {

    private static final int FRAMES_PER_ROW = 4;
    private static final int FRAMES_PER_COLUMN = 2;
    private static final int LAST_FRAME = 7;

    private int ticks;
    /** Ticks the flip-book takes; derived from the requested age but always 1 to 6. */
    private final int duration;
    private final float size;
    private int currentFrame;

    public ExplosionParticle(Level level, double x, double y, double z, float size, int age) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        // the formula can yield 0, which would never expire; keep at least one tick
        this.duration = Math.max(1, (1 + age) % 7);
        this.roll = -45.0F + this.random.nextFloat() * 90.0F;
        this.size = size + this.random.nextFloat() * 0.2F;
        this.oRoll = this.roll;
        this.hasPhysics = false;
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, @NotNull Camera camera, float partialTicks) {
        this.currentFrame = (int) ((this.ticks + partialTicks) * LAST_FRAME / this.duration);
        if (this.currentFrame > LAST_FRAME) {
            return;
        }
        super.render(buffer, camera, partialTicks);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return this.size * 1.2F;
    }

    @Override
    protected float getU0() {
        return (this.currentFrame % FRAMES_PER_ROW) / (float) FRAMES_PER_ROW;
    }

    @Override
    protected float getU1() {
        return (this.currentFrame % FRAMES_PER_ROW + 1) / (float) FRAMES_PER_ROW;
    }

    @Override
    protected float getV0() {
        return (this.currentFrame / FRAMES_PER_ROW) / (float) FRAMES_PER_COLUMN;
    }

    @Override
    protected float getV1() {
        return (this.currentFrame / FRAMES_PER_ROW + 1) / (float) FRAMES_PER_COLUMN;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.ticks++;
        if (this.ticks >= this.duration) {
            this.setExpired();
        }
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderTypes.FLASH;
    }
}
