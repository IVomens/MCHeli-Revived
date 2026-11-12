package com.ivomens.mcheli.fx.client.legacy;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;


/** Engine, missile and wreck smoke: drifts, may grow (diffusible), fade to grey and rise late in life. */
public class McheliSmokeParticle extends BaseParticle {

    private static final double WIND_RANGE = 15.0;

    public McheliSmokeParticle(Level level, double x, double y, double z, double mx, double my, double mz) {
        super(level, x, y, z, mx, my, mz);
        this.rCol = this.gCol = this.bCol = this.random.nextFloat() * 0.3F + 0.7F;
        this.setParticleScale(this.random.nextFloat() * 0.5F + 5.0F);
        this.setParticleMaxAge((int) (16.0 / (this.random.nextFloat() * 0.8 + 0.2)) + 2);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age < this.lifetime) {
            this.setParticleTextureIndex((int) (8.0 * this.age / this.lifetime));
            this.age++;
            if (this.diffusible && this.particleScale < this.particleMaxScale) {
                this.particleScale += 0.8F;
            }

            if (this.turnsWhite) {
                this.fadeTowardsGrey();
            }

            this.applyWind();
            if ((float) this.age / this.lifetime > this.risesAfter) {
                this.yd += 0.02;
            } else {
                this.yd = this.yd + this.gravity;
            }

            this.move(this.xd, this.yd, this.zd);
            if (this.diffusible) {
                this.xd *= 0.96;
                this.zd *= 0.96;
                this.yd *= 0.96;
            } else {
                this.xd *= 0.9;
                this.zd *= 0.9;
            }
        } else {
            this.setExpired();
        }
    }

    /** Coloured smoke slowly loses its tint: every channel drifts towards the brightest one. */
    private void fadeTowardsGrey() {
        float lowest = Math.min(Math.min(this.bCol, this.gCol), this.rCol);
        float highest = Math.max(Math.max(this.bCol, this.gCol), this.rCol);
        if (highest - lowest > 0.2) {
            this.rCol += (highest - this.rCol) * 0.016F;
            this.gCol += (highest - this.gCol) * 0.016F;
            this.bCol += (highest - this.bCol) * 0.016F;
        }
    }

    /** Rotor downwash: nearby aircraft with throttle push the smoke away. */
    private void applyWind() {
        if (!this.windBlown) {
            return;
        }
        // one shared per-tick list of throttled-up aircraft instead of an entity query per particle
        for (Vehicle vehicle : WindSources.of(this.level)) {
            Entity ac = vehicle.entity();
            double dx = ac.getX() - this.x;
            double dy = ac.getY() - this.y;
            double dz = ac.getZ() - this.z;
            if (Math.abs(dx) > WIND_RANGE + ac.getBbWidth() || Math.abs(dz) > WIND_RANGE + ac.getBbWidth()
                    || Math.abs(dy) > WIND_RANGE + ac.getBbHeight()) {
                continue;
            }
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            double push = (23.0 - distance) * 0.01F * vehicle.throttle();
            this.xd -= dx * push;
            this.zd -= dz * push;
        }
    }

    /** Lit by full sky light only, ignoring block light, so smoke looks the same day and night under cover. */
    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.pack(0, 15);
    }
}
