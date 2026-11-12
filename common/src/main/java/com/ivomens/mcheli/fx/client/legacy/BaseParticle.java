package com.ivomens.mcheli.fx.client.legacy;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Base of the mod's textured particles. Textures are single images split into an 8-frame strip
 * (see {@link #setParticleTextureIndex}); the U range selects the frame, V always spans the image,
 * so frames advance horizontally and never scroll vertically.
 */
public abstract class BaseParticle extends TextureSheetParticle {

    private static final int FRAMES = 8;

    // How the smoke particles behave; set by LegacyEffects from the particle's parameters.
    /** Drifts with the wind. */
    protected boolean windBlown;
    /** Starts small and grows up to {@link #particleMaxScale}. */
    protected boolean diffusible;
    /** Fades to white as it ages. */
    protected boolean turnsWhite;
    protected float particleScale = 1.0F;
    protected float particleMaxScale;
    /** Extra per-tick vertical acceleration (hides the vanilla field on purpose: its own rules differ). */
    protected float gravity;
    /** Fraction of the lifetime after which the particle starts to rise. */
    protected float risesAfter;
    protected int particleTextureIndexX;

    public BaseParticle(Level level, double x, double y, double z, double mx, double my, double mz) {
        super((ClientLevel) level, x, y, z, mx, my, mz);
        // keep the exact motion; the vanilla constructor randomises it
        this.xd = mx;
        this.yd = my;
        this.zd = mz;
        this.particleMaxScale = this.particleScale;
    }

    public BaseParticle setParticleScale(float scale) {
        this.particleScale = scale;
        return this;
    }

    public void setParticleMaxAge(int age) {
        this.lifetime = age;
    }

    /** Selects one of the frames of the strip. */
    public void setParticleTextureIndex(int frame) {
        this.particleTextureIndexX = frame % FRAMES;
    }

    public void setRBGColorF(float r, float g, float b) {
        this.setColor(r, g, b);
    }

    public void setAlphaF(float a) {
        this.setAlpha(a);
    }

    public void setExpired() {
        this.remove();
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return 0.1F * this.particleScale;
    }

    /** Frame strip: {@value #FRAMES} frames side by side. */
    @Override
    protected float getU0() {
        return this.particleTextureIndexX / (float) FRAMES;
    }

    @Override
    protected float getU1() {
        return (this.particleTextureIndexX + 1) / (float) FRAMES;
    }

    @Override
    protected float getV0() {
        return 0.0F;
    }

    @Override
    protected float getV1() {
        return 1.0F;
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderTypes.SMOKE;
    }
}
