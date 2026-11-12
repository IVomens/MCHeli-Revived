package com.ivomens.mcheli.fx.client.modern;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * One configurable billboard particle. Everything the effects need (growth, fade, spin, drag,
 * gravity, bouncing) is expressed with a handful of numbers, so new effects are made by
 * combining {@link Builder} settings rather than by writing new particle classes.
 */
public class FxParticle extends SingleQuadParticle {

    private final FxSprite sprite;
    private final float startSize;
    private final float endSize;
    private final float peakAlpha;
    private final float fadeIn;
    private final float spin;
    private final float drag;
    private final float lift;
    private final boolean bounces;
    private final boolean animate;
    private final int fixedFrame;

    private FxParticle(ClientLevel level, double x, double y, double z, Builder b) {
        super(level, x, y, z);
        this.sprite = b.sprite;
        this.xd = b.vx;
        this.yd = b.vy;
        this.zd = b.vz;
        this.lifetime = Math.max(1, b.lifetime);
        this.startSize = b.startSize;
        this.endSize = b.endSize;
        this.quadSize = b.startSize;
        this.peakAlpha = b.alpha;
        this.alpha = b.fadeIn > 0 ? 0 : b.alpha;
        this.fadeIn = b.fadeIn;
        this.rCol = b.r;
        this.gCol = b.g;
        this.bCol = b.b;
        this.gravity = b.gravity;
        this.spin = b.spin;
        this.drag = b.drag;
        this.lift = b.lift;
        this.bounces = b.bounces;
        this.hasPhysics = b.bounces;
        this.animate = b.animate;
        this.fixedFrame = b.animate ? 0 : this.random.nextInt(FxSprite.FRAMES);
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.friction = 1.0F;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        float t = (float) this.age / this.lifetime;
        this.quadSize = Mth.lerp(t, this.startSize, this.endSize);
        this.alpha = this.peakAlpha * fade(t);
        if (!(this.bounces && this.onGround)) {
            this.roll += this.spin;
        }

        this.yd += this.lift - 0.04F * this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= this.drag;
        this.yd *= this.drag;
        this.zd *= this.drag;

        if (this.bounces && this.onGround) {
            this.yd *= -0.35;
            this.xd *= 0.6;
            this.zd *= 0.6;
        }
    }

    /** Quick fade-in, a plateau, then a smooth fade-out over the second half of life. */
    private float fade(float t) {
        float in = this.fadeIn > 0 ? Math.min(1, t / this.fadeIn) : 1;
        float out = t < 0.5F ? 1 : 1 - (t - 0.5F) * 2;
        return in * out * out * (3 - 2 * out);
    }

    private int frame() {
        if (!this.animate) return this.fixedFrame;
        return Math.min(FxSprite.FRAMES - 1, this.age * FxSprite.FRAMES / this.lifetime);
    }

    @Override
    protected float getU0() {
        return frame() / (float) FxSprite.FRAMES;
    }

    @Override
    protected float getU1() {
        return (frame() + 1) / (float) FxSprite.FRAMES;
    }

    @Override
    protected float getV0() {
        return 0;
    }

    @Override
    protected float getV1() {
        return 1;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return this.sprite.isEmissive() ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return this.sprite.renderType;
    }

    public static Builder of(FxSprite sprite) {
        return new Builder(sprite);
    }

    /** Fluent settings for one particle. Unset values have sensible defaults. */
    public static final class Builder {
        private final FxSprite sprite;
        private double vx, vy, vz;
        private int lifetime = 40;
        private float startSize = 0.5F, endSize = 1.5F;
        private float alpha = 1, fadeIn = 0.08F;
        private float r = 1, g = 1, b = 1;
        private float gravity, lift, spin, drag = 0.96F;
        private boolean bounces, animate = true;

        private Builder(FxSprite sprite) {
            this.sprite = sprite;
        }

        public Builder velocity(double x, double y, double z) {
            this.vx = x;
            this.vy = y;
            this.vz = z;
            return this;
        }

        public Builder lifetime(int ticks) {
            this.lifetime = ticks;
            return this;
        }

        public Builder size(float start, float end) {
            this.startSize = start;
            this.endSize = end;
            return this;
        }

        public Builder alpha(float alpha) {
            this.alpha = alpha;
            return this;
        }

        public Builder fadeIn(float fractionOfLife) {
            this.fadeIn = fractionOfLife;
            return this;
        }

        public Builder color(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
            return this;
        }

        public Builder gravity(float gravity) {
            this.gravity = gravity;
            return this;
        }

        /** Constant upward acceleration, for rising smoke. */
        public Builder lift(float lift) {
            this.lift = lift;
            return this;
        }

        public Builder spin(float radiansPerTick) {
            this.spin = radiansPerTick;
            return this;
        }

        public Builder drag(float drag) {
            this.drag = drag;
            return this;
        }

        /** Collides with blocks and bounces; used for debris. */
        public Builder bounces() {
            this.bounces = true;
            return this;
        }

        /** Uses one random frame for its whole life instead of playing the sheet. */
        public Builder randomFrame() {
            this.animate = false;
            return this;
        }

        public FxParticle build(ClientLevel level, double x, double y, double z) {
            return new FxParticle(level, x, y, z, this);
        }
    }
}
