package com.ivomens.mcheli.combat.explosion;

import com.ivomens.mcheli.combat.weapon.DamageFactor;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Everything an explosion goes off with.
 * <p>
 * {@code size} is the radius of the visual effect and, unless set apart, of the damage and the block
 * rays. Most explosions only set a size and a few flags; {@link #at} starts a builder with the usual
 * defaults (block size, damage power and damage radius equal to the size, block power equal to the
 * block size, a sound, nothing set on fire).
 *
 * @param blockSize      reach of the block-breaking rays
 * @param damagePower    scales the damage dealt to entities
 * @param blockPower     divides the resistance of the blocks the rays pass through
 * @param damageRadius   reach of the entity damage (half the distance it is falling off over)
 * @param fire           sets fire to the blocks around the centre
 * @param destroyBlocks  breaks the blocks the rays reach (also needs the config and mob griefing)
 * @param igniteEntities seconds of burning given to entities at the centre, falling off outwards
 * @param sound          plays the explosion sound
 * @param medium         where it goes off; under water the rays pass blocks more easily
 * @param damageFactor   per entity class damage multipliers of the weapon, or null
 * @param cover          how cover between the centre and an entity counts
 */
public record ExplosionSpec(Vec3 at, float size, float blockSize, float damagePower, float blockPower,
                            float damageRadius, boolean fire, boolean destroyBlocks, int igniteEntities,
                            boolean sound, Medium medium, @Nullable DamageFactor damageFactor, BlastCover cover) {

    public enum Medium { AIR, WATER }

    public boolean inWater() {
        return this.medium == Medium.WATER;
    }

    public static Builder at(Vec3 at, float size) {
        return new Builder(at, size);
    }

    public static Builder at(double x, double y, double z, float size) {
        return new Builder(new Vec3(x, y, z), size);
    }

    /** Fills the spec in; anything not set keeps the default described on {@link ExplosionSpec}. */
    public static final class Builder {

        private final Vec3 at;
        private final float size;
        private float blockSize;
        private float damagePower;
        private float damageRadius;
        private float blockPower = Float.NaN;
        private boolean fire;
        private boolean destroyBlocks;
        private int igniteEntities;
        private boolean sound = true;
        private Medium medium = Medium.AIR;
        @Nullable
        private DamageFactor damageFactor;
        private BlastCover cover = BlastCover.EXPOSURE;

        private Builder(Vec3 at, float size) {
            this.at = at;
            this.size = size;
            this.blockSize = size;
            this.damagePower = size;
            this.damageRadius = size;
        }

        public Builder blockSize(float blockSize) {
            this.blockSize = blockSize;
            return this;
        }

        public Builder damagePower(float damagePower) {
            this.damagePower = damagePower;
            return this;
        }

        public Builder blockPower(float blockPower) {
            this.blockPower = blockPower;
            return this;
        }

        public Builder damageRadius(float damageRadius) {
            this.damageRadius = damageRadius;
            return this;
        }

        public Builder fire(boolean fire) {
            this.fire = fire;
            return this;
        }

        public Builder destroyBlocks(boolean destroyBlocks) {
            this.destroyBlocks = destroyBlocks;
            return this;
        }

        public Builder igniteEntities(int seconds) {
            this.igniteEntities = seconds;
            return this;
        }

        public Builder sound(boolean sound) {
            this.sound = sound;
            return this;
        }

        public Builder underWater(boolean inWater) {
            this.medium = inWater ? Medium.WATER : Medium.AIR;
            return this;
        }

        public Builder damageFactor(@Nullable DamageFactor damageFactor) {
            this.damageFactor = damageFactor;
            return this;
        }

        public Builder cover(BlastCover cover) {
            this.cover = cover;
            return this;
        }

        public ExplosionSpec build() {
            float power = Float.isNaN(this.blockPower) ? this.blockSize : this.blockPower;
            return new ExplosionSpec(this.at, this.size, this.blockSize, this.damagePower, power, this.damageRadius,
                    this.fire, this.destroyBlocks, this.igniteEntities, this.sound, this.medium, this.damageFactor,
                    this.cover);
        }
    }
}
