package com.ivomens.mcheli.combat.weapon;

import net.minecraft.util.RandomSource;

/** The spin of a rotary gun's barrels: it spins up while the group fires and winds down after. */
public final class BarrelSpin {

    private static final float MAX_SPEED = 74.0F;
    private static final int SPIN_UP = 25;
    private static final int SPIN_UP_JITTER = 3;

    private float speed;
    private float angle;
    private float prevAngle;

    public float angle() {
        return this.angle;
    }

    public float prevAngle() {
        return this.prevAngle;
    }

    void tick(boolean firing, RandomSource random) {
        if (firing && this.speed < MAX_SPEED + 1.0F) {
            this.speed = Math.min(MAX_SPEED, this.speed + (SPIN_UP + random.nextInt(SPIN_UP_JITTER)));
        }
        this.prevAngle = this.angle;
        this.angle += this.speed;
        if (this.angle >= 360.0F) {
            this.angle -= 360.0F;
            this.prevAngle -= 360.0F;
        }
        if (this.speed > 0.0F) {
            this.speed = Math.max(0.0F, this.speed - 1.0F);
        }
    }
}
