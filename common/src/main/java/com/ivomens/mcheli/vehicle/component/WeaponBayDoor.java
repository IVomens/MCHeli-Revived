package com.ivomens.mcheli.vehicle.component;

/** Opening angle of one weapon bay door; it opens while a crew member has one of the bay's weapons selected. */
public final class WeaponBayDoor {

    private static final float OPEN_ANGLE = 90.0F;
    private static final float SPEED = 3.0F;

    private float angle;
    private float prevAngle;

    public float angle() {
        return this.angle;
    }

    public float prevAngle() {
        return this.prevAngle;
    }

    public void step(boolean open) {
        this.prevAngle = this.angle;
        this.angle = open ? Math.min(this.angle + SPEED, OPEN_ANGLE) : Math.max(this.angle - SPEED, 0.0F);
    }
}
