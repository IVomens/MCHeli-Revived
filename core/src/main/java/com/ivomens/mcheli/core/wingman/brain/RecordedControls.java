package com.ivomens.mcheli.core.wingman.brain;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Controls that start from an observed aircraft and keep every change to themselves, so the brain can
 * decide ahead of the vehicle tick and the scripted pilot applies the result later
 * ({@link #command()}). Reads see the changes made so far, with the same rules the aircraft applies:
 * a rotation that is not a finite number is ignored, and a damaged engine's throttle never opens
 * further.
 */
public final class RecordedControls implements Controls {

    private static final PilotCommand UNTOUCHED = new RecordedControls(new Point(0, 0, 0), new Point(0, 0, 0),
            0.0F, 0.0F, 0.0F, 0.0, false, false).command();

    private final boolean engineCrippled;
    private final boolean onGround;
    private Point position;
    private Point motion;
    private float yaw;
    private float pitch;
    private float roll;
    private double throttle;

    private final List<Double> throttleSettings = new ArrayList<>();
    private @Nullable Boolean nozzleHover;
    private @Nullable Float newYaw;
    private @Nullable Float newPitch;
    private @Nullable Float newRoll;
    private @Nullable Double motionX;
    private @Nullable Double motionY;
    private @Nullable Double motionZ;
    private @Nullable Point newPosition;
    private @Nullable Float weaponYaw;
    private @Nullable Float weaponPitch;
    private @Nullable Float pilotYaw;
    private @Nullable Boolean gearFolded;

    public RecordedControls(Point position, Point motion, float yaw, float pitch, float roll, double throttle,
                            boolean onGround, boolean engineCrippled) {
        this.position = position;
        this.motion = motion;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.throttle = throttle;
        this.onGround = onGround;
        this.engineCrippled = engineCrippled;
    }

    static PilotCommand untouched() {
        return UNTOUCHED;
    }

    /** Everything changed so far. */
    public PilotCommand command() {
        return new PilotCommand(this.throttleSettings, this.nozzleHover, this.newYaw, this.newPitch, this.newRoll,
                this.motionX, this.motionY, this.motionZ, this.newPosition, this.weaponYaw, this.weaponPitch,
                this.pilotYaw, this.gearFolded);
    }

    @Override
    public Point position() {
        return this.position;
    }

    @Override
    public Point motion() {
        return this.motion;
    }

    @Override
    public float yaw() {
        return this.yaw;
    }

    @Override
    public float pitch() {
        return this.pitch;
    }

    @Override
    public float roll() {
        return this.roll;
    }

    @Override
    public double throttle() {
        return this.throttle;
    }

    @Override
    public boolean onGround() {
        return this.onGround;
    }

    @Override
    public void setThrottle(double throttle) {
        this.throttleSettings.add(throttle);
        this.throttle = throttle > this.throttle && this.engineCrippled ? this.throttle : throttle;
    }

    @Override
    public void setYaw(float yaw) {
        if (Float.isFinite(yaw)) {
            this.yaw = yaw;
            this.newYaw = yaw;
        }
    }

    @Override
    public void setPitch(float pitch) {
        if (Float.isFinite(pitch)) {
            this.pitch = pitch;
            this.newPitch = pitch;
        }
    }

    @Override
    public void setRoll(float roll) {
        this.roll = roll;
        this.newRoll = roll;
    }

    @Override
    public void setMotion(double x, double y, double z) {
        this.motion = new Point(x, y, z);
        this.motionX = x;
        this.motionY = y;
        this.motionZ = z;
    }

    @Override
    public void setMotionY(double y) {
        this.motion = new Point(this.motion.x(), y, this.motion.z());
        this.motionY = y;
    }

    @Override
    public void setHorizontalMotion(double x, double z) {
        this.motion = new Point(x, this.motion.y(), z);
        this.motionX = x;
        this.motionZ = z;
    }

    @Override
    public void setPosition(double x, double y, double z) {
        this.position = new Point(x, y, z);
        this.newPosition = this.position;
    }

    @Override
    public void aimWeapons(float yaw) {
        this.weaponYaw = yaw;
    }

    @Override
    public void aimWeaponsPitch(float pitch) {
        this.weaponPitch = pitch;
    }

    @Override
    public void turnPilot(float yaw) {
        this.pilotYaw = yaw;
    }

    @Override
    public void switchNozzle(boolean hover) {
        this.nozzleHover = hover;
    }

    @Override
    public void setGear(boolean folded) {
        this.gearFolded = folded;
    }
}
