package com.ivomens.mcheli.core.wingman.brain;

/**
 * The aircraft as the brain flies it: where it is, how it moves and sits, and the controls the
 * autopilot works directly. Reads see the brain's own changes of the same step.
 *
 * <p>Before the level ticks the brain works a {@link RecordedControls}: its changes become a
 * {@link PilotCommand} that the vehicle's scripted pilot applies as the first phase of the vehicle
 * tick. After every entity has moved, the corrections are applied to the aircraft as they are made.
 *
 * <p>Angles are Minecraft's: yaw grows turning left, negative pitch is nose up, positive roll banks left.
 */
public interface Controls {

    Point position();

    Point motion();

    float yaw();

    float pitch();

    float roll();

    double throttle();

    boolean onGround();

    void setThrottle(double throttle);

    void setYaw(float yaw);

    void setPitch(float pitch);

    void setRoll(float roll);

    /** Pitch and roll back to zero, as whenever the aircraft is on the ground. */
    default void levelAttitude() {
        setPitch(0.0F);
        setRoll(0.0F);
    }

    void setMotion(double x, double y, double z);

    void setMotionY(double y);

    void setHorizontalMotion(double x, double z);

    default void stopMotion() {
        setMotion(0.0, 0.0, 0.0);
    }

    /** Moves the aircraft; only the corrections after physics do this. */
    void setPosition(double x, double y, double z);

    /** Where the crew's weapons look: guided weapons aim along these angles. */
    void aimWeapons(float yaw);

    void aimWeaponsPitch(float pitch);

    /** Turns the player in the pilot seat, who would otherwise steer the aircraft with their view. */
    void turnPilot(float yaw);

    /** A VTOL plane's nozzle: down for hover, back for forward flight. Other aircraft ignore it. */
    void switchNozzle(boolean hover);

    /** Raises or lowers the landing gear when the aircraft can. */
    void setGear(boolean folded);
}
