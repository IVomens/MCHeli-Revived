package com.ivomens.mcheli.combat.weapon;

import net.minecraft.util.Mth;

/**
 * How far a mount's barrel is pushed back by its last shot, for the model: a quick kick that eases
 * out over the definition's recoil buffer count.
 */
public final class RecoilBuffer {

    private final int length;
    private final int speed;
    private int count;
    private float offset;
    private float prevOffset;

    RecoilBuffer(int length, int speed) {
        this.length = length;
        this.speed = speed;
    }

    /** The push-back this tick, 0 to 1. */
    public float value() {
        return this.offset;
    }

    /** The push-back the tick before. */
    public float previous() {
        return this.prevOffset;
    }

    void tick(boolean fired) {
        this.prevOffset = this.offset;
        if (fired && this.count <= 0) {
            this.count = this.length;
        }
        if (this.count <= 0) {
            this.offset = 0.0F;
            return;
        }
        if (this.length <= 1) {
            this.offset = 1.0F;
        } else if (this.length == 2) {
            this.offset = this.count == 2 ? 1.0F : 0.6F;
        } else {
            if (this.count > this.length / 2) {
                this.count -= this.speed;
            }
            this.offset = Mth.sin((float) this.count / this.length * (float) Math.PI);
        }
        this.count--;
    }
}
