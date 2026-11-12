package com.ivomens.mcheli.core.sim.control;

/**
 * The pilot's stick for one attitude step. The mouse is one source of it; a joystick, the stick keys
 * and the handling assists are others, and all of them arrive here in the same units.
 *
 * @param stickX   virtual stick deflection to the right, in mouse units; the mouse moves the stick
 *                 and the stick sets the turn rate, so a held deflection keeps turning
 * @param stickY   stick deflection up, which raises the nose
 * @param freeLook the mouse moves only the pilot's head, so the stick reads as centred
 * @param rudder   extra yaw to the right, in the same units, on top of what the stick yaws; zero
 *                 unless a joystick twist, a yaw key or an assist adds some, and then the models
 *                 turn exactly as they always did
 */
public record AttitudeInput(float stickX, float stickY, boolean freeLook, float rudder) {

    public static final AttitudeInput CENTRED = new AttitudeInput(0.0F, 0.0F, false);

    /** The mouse's stick, without rudder. */
    public AttitudeInput(float stickX, float stickY, boolean freeLook) {
        this(stickX, stickY, freeLook, 0.0F);
    }

    /** The stick as the airframe sees it: centred while the pilot looks around. */
    public float effectiveX() {
        return this.freeLook ? 0.0F : this.stickX;
    }

    public float effectiveY() {
        return this.freeLook ? 0.0F : this.stickY;
    }

    /** The rudder as the airframe sees it: none while the pilot looks around. */
    public float effectiveRudder() {
        return this.freeLook ? 0.0F : this.rudder;
    }

    /**
     * The yaw request of a stick whose x axis also yaws: the stick alone when there is no rudder, so
     * the default input is passed on untouched, otherwise both.
     */
    public float yawRequest(float stickYaw) {
        float extra = effectiveRudder();
        return extra == 0.0F ? stickYaw : stickYaw + extra;
    }
}
