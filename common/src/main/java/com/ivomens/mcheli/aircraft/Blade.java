package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.core.math.Degrees;

/**
 * One rotor blade. While spinning it follows the rotor angle plus its own offset; when folded it
 * swings round to lie near 0 degrees (alongside the tail).
 */
public class Blade {

    private static final float DEFAULT_FOLD_SPEED = 3.0F;
    private static final float DEFAULT_FOLD_ROTATION = 5.0F;

    private final float baseRotation;
    private float rotation;
    private float prevRotation;
    private float foldSpeed = DEFAULT_FOLD_SPEED;
    /** Angle from 0 at which the blade counts as folded. */
    private float foldRotation = DEFAULT_FOLD_ROTATION;

    public Blade(float baseRotation) {
        this.baseRotation = baseRotation;
    }

    public float getRotation() {
        return this.rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = (float) Degrees.wrap360(rotation);
    }

    public float getPrevRotation() {
        return this.prevRotation;
    }

    public void setPrevRotation(float rotation) {
        this.prevRotation = (float) Degrees.wrap360(rotation);
    }

    public float getBaseRotation() {
        return this.baseRotation;
    }

    public Blade setFoldSpeed(float speed) {
        if (speed > 0.1) {
            this.foldSpeed = speed;
        }
        return this;
    }

    /** Ignored unless larger than two fold steps, so a folding blade cannot step past it. */
    public Blade setFoldRotation(float rotation) {
        if (rotation > this.foldSpeed * 2.0F) {
            this.foldRotation = rotation;
        }
        return this;
    }

    private boolean isUnfolded() {
        return this.rotation > this.foldRotation && this.rotation < 360.0F - this.foldRotation;
    }

    public void forceFold() {
        if (isUnfolded()) {
            this.rotation = this.rotation < 180.0F ? this.foldRotation : 360.0F - this.foldRotation;
            this.rotation %= 360.0F;
            this.prevRotation = this.rotation;
        }
    }

    /** Steps the blade towards its folded position; returns true once it is there. */
    public boolean folding() {
        if (!isUnfolded()) {
            return true;
        }
        this.rotation = this.rotation < 180.0F ? this.rotation - this.foldSpeed : this.rotation + this.foldSpeed;
        this.rotation %= 360.0F;
        return false;
    }

    /** Steps the blade back to its spinning position for the rotor angle; returns true once there. */
    public boolean unfolding(float rotorRotation) {
        float target = (float) Degrees.wrap360(rotorRotation + this.baseRotation);
        float previous = this.rotation;
        if (target <= 180.0F) {
            this.rotation = (float) Degrees.wrap360(this.rotation + this.foldSpeed);
            if (this.rotation >= target && previous <= target) {
                this.rotation = target;
                return true;
            }
        } else {
            this.rotation = (float) Degrees.wrap360(this.rotation - this.foldSpeed);
            if (this.rotation <= target && previous >= target) {
                this.rotation = target;
                return true;
            }
        }
        return false;
    }
}
