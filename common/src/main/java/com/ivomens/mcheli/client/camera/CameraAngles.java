package com.ivomens.mcheli.client.camera;

/**
 * Camera yaw, pitch and roll in degrees, as the loader is about to apply them. The loader glue fills it
 * from its camera event, lets the mod adjust it, and applies the result; one instance per call site.
 */
public final class CameraAngles {

    private float yaw;
    private float pitch;
    private float roll;

    public float yaw() {
        return this.yaw;
    }

    public float pitch() {
        return this.pitch;
    }

    public float roll() {
        return this.roll;
    }

    public void set(float yaw, float pitch, float roll) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }

    /** Adds to the roll only, leaving yaw and pitch as the loader computed them. */
    public void addRoll(float degrees) {
        this.roll += degrees;
    }
}
