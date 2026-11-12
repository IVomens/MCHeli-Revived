package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.config.McheliConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * The mouse read as a flight stick. Every frame the cursor movement becomes a delta (in vanilla mouse
 * units, scaled by both sensitivity options) and accumulates into a virtual stick deflection, clamped to
 * {@link #MAX_LENGTH}. The HUD shows the deflection; the attitude models steer with both.
 *
 * <p>One per client; only the render thread touches it.
 */
public final class MouseStick {

    /** How far the virtual stick can be deflected, in mouse units. */
    public static final double MAX_LENGTH = 40.0;

    private double prevDeltaX;
    private double prevDeltaY;
    private double deltaX;
    private double deltaY;
    private double stickX;
    private double stickY;

    /**
     * Reads this frame's cursor movement. Without a grabbed mouse, a focused window or with a screen open
     * the deltas stay zero and the stick keeps its deflection.
     *
     * @param rawX       cursor movement to the right since the last frame
     * @param rawY       cursor movement upwards since the last frame
     * @param stickMode  whether the stick stays where it is put; otherwise a small deflection springs back
     * @param ridingAircraft whether the player rides an aircraft, which the pitch inversion option applies to
     */
    void read(Minecraft mc, double rawX, double rawY, boolean stickMode, float partialTicks, boolean ridingAircraft) {
        this.prevDeltaX = this.deltaX;
        this.prevDeltaY = this.deltaY;
        this.deltaX = 0.0;
        this.deltaY = 0.0;
        if (!mc.mouseHandler.isMouseGrabbed() || !mc.isWindowActive() || mc.screen != null) {
            return;
        }

        if (!stickMode) {
            if (Math.abs(this.stickX) < MAX_LENGTH * 0.2) {
                this.stickX *= 1.0F - 0.15F * partialTicks;
            }
            if (Math.abs(this.stickY) < MAX_LENGTH * 0.2) {
                this.stickY *= 1.0F - 0.15F * partialTicks;
            }
        }

        // Same sensitivity curve as vanilla mouse look, scaled by the MCHeli sensitivity option.
        double sensitivity = mc.options.sensitivity().get() * 0.6 + 0.2;
        double vanillaFactor = sensitivity * sensitivity * sensitivity * 8.0;
        double modFactor = McheliConfig.controls().mouseSensitivity() * 0.1;
        this.deltaX = modFactor * rawX * vanillaFactor;
        this.deltaY = modFactor * rawY * vanillaFactor;
        if (McheliConfig.controls().invertMousePitch() && ridingAircraft) {
            // flight-sim style: pull the mouse back (down) to raise the nose
            this.deltaY = -this.deltaY;
        }

        this.stickX += this.deltaX;
        this.stickY += this.deltaY * yDirection(mc);
        double lengthSq = this.stickX * this.stickX + this.stickY * this.stickY;
        if (lengthSq > 1.0) {
            double length = Math.sqrt(lengthSq);
            double clamped = Math.min(length, MAX_LENGTH);
            this.stickX = this.stickX / length * clamped;
            this.stickY = this.stickY / length * clamped;
        }
    }

    /** -1 when exactly one of the vanilla and the MCHeli "invert mouse" options is on. */
    private static int yDirection(Minecraft mc) {
        boolean inverted = mc.options.invertYMouse().get() != McheliConfig.controls().invertMouse();
        return inverted ? -1 : 1;
    }

    /** {@code Entity.turn} with this frame's deltas; positive mouse Y looks up, so the pitch is negated. */
    void turn(Player player) {
        player.turn(this.deltaX, -this.deltaY);
    }

    /** The stick deflection to the right. */
    public double stickX() {
        return this.stickX;
    }

    /** The stick deflection, pulled back positive unless the mouse is inverted. */
    public double stickY() {
        return this.stickY * yDirection(Minecraft.getInstance());
    }

    double rawStickX() {
        return this.stickX;
    }

    double rawStickY() {
        return this.stickY;
    }

    /** Yaw input of this frame, averaged with the last one to smooth uneven frames. */
    float smoothedDeltaX() {
        return (float) (this.deltaX + this.prevDeltaX) / 2.0F;
    }

    /** Pitch input of this frame (positive looks down), averaged with the last one. */
    float smoothedPitchDelta() {
        return (float) -(this.deltaY + this.prevDeltaY) / 2.0F;
    }

    double deltaX() {
        return this.deltaX;
    }

    double prevDeltaX() {
        return this.prevDeltaX;
    }

    double deltaY() {
        return this.deltaY;
    }

    double prevDeltaY() {
        return this.prevDeltaY;
    }

    /** Scales this frame's vertical input, e.g. by a gun's camera rotation speed. */
    void scaleDeltaY(double factor) {
        this.deltaY *= factor;
    }

    /** Lets the stick drift back to centre, by {@code factor} per frame. */
    void scaleStick(double factor) {
        this.stickX *= factor;
        this.stickY *= factor;
    }

    /** Puts the stick's x axis where a joystick holds it, so the HUD shows the joystick's deflection. */
    void placeX(double x) {
        this.stickX = x;
    }

    /** Puts the stick's (uninverted) y axis where a joystick holds it. */
    void placeY(double y) {
        this.stickY = y;
    }

    /** Whether the mouse moved this frame. */
    boolean moved() {
        return this.deltaX != 0.0 || this.deltaY != 0.0;
    }

    /** Centres the stick (the deltas of this frame stay). */
    void centreStick() {
        this.stickX = 0.0;
        this.stickY = 0.0;
    }

    /** Centres the stick and drops this frame's movement. */
    void zero() {
        centreStick();
        this.deltaX = 0.0;
        this.deltaY = 0.0;
    }

    /** Outside stick mode, or with the stick nearly centred, it drifts back to centre. */
    void dampUnlessHeld(boolean stickMode) {
        double length = (float) Math.sqrt(this.stickX * this.stickX + this.stickY * this.stickY);
        if (!stickMode || length < MAX_LENGTH * 0.1) {
            scaleStick(0.95);
        }
    }
}
