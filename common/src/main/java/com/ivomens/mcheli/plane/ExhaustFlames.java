package com.ivomens.mcheli.plane;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * Client: the afterburner flames lean into a turn as the pilot holds a side key, followed every
 * frame; the renderer keeps each flame's flip-book frame here as well.
 */
public final class ExhaustFlames {

    /** How fast the flames lean into a turn, per second. */
    private static final float FOLLOW_RATE = 2.0F;

    private final AircraftEntity plane;
    /** Sideways lean of the flames while turning, in -1..1. */
    private float lean;
    private float prevLean;
    @Nullable
    private Animation animation;

    ExhaustFlames(AircraftEntity plane) {
        this.plane = plane;
    }

    /** One frame of the pilot's flying: the flames lean towards the held side key. */
    void followKeys(float frameSeconds) {
        this.prevLean = this.lean;
        boolean left = this.plane.keys().moveLeft();
        boolean right = this.plane.keys().moveRight();
        float target = 0.0F;
        if (left && !right) {
            target = -1.0F;
        } else if (right && !left) {
            target = 1.0F;
        }
        float follow = Math.min(FOLLOW_RATE * frameSeconds, 1.0F);
        this.lean = Mth.clamp(this.lean + (target - this.lean) * follow, -1.0F, 1.0F);
    }

    public float lean() {
        return this.lean;
    }

    public float prevLean() {
        return this.prevLean;
    }

    /** The flames' flip-book state, made for {@code flameCount} flames on first use. */
    public Animation animation(int flameCount) {
        if (this.animation == null || this.animation.frame.length != flameCount) {
            this.animation = new Animation(flameCount);
        }
        return this.animation;
    }

    /** Each flame's current flip-book frame, and how many rendered frames it has shown it. */
    public static final class Animation {

        public final int[] frame;
        public final int[] tick;

        private Animation(int flameCount) {
            this.frame = new int[flameCount];
            this.tick = new int[flameCount];
        }
    }
}
