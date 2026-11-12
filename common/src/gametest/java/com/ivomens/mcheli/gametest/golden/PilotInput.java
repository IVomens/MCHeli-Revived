package com.ivomens.mcheli.gametest.golden;

import java.util.EnumSet;
import java.util.Set;

/**
 * What the pilot does in one tick: the keys held, where the mouse stick is, and any one-shot
 * switches pressed this tick.
 *
 * @param keys    bit set of {@link Key} ordinals
 * @param stickX  stick deflection to the right, in the mouse units {@code steerByMouse} takes
 * @param stickY  stick deflection up, which raises the nose
 * @param rudder  extra yaw in the same units; only the handling assists set it, so every other
 *                script steers through {@code steerByMouse} exactly as before
 * @param actions switches pressed this tick, sent once with the control packet
 */
public record PilotInput(int keys, float stickX, float stickY, float rudder, Set<Action> actions) {

    public static final PilotInput NONE = new PilotInput(0, 0.0F, 0.0F, 0.0F, Set.of());

    public boolean holds(Key key) {
        return (this.keys & key.bit()) != 0;
    }

    public PilotInput withKey(Key key) {
        return new PilotInput(this.keys | key.bit(), this.stickX, this.stickY, this.rudder, this.actions);
    }

    public PilotInput withStick(float x, float y) {
        return new PilotInput(this.keys, this.stickX + x, this.stickY + y, this.rudder, this.actions);
    }

    /** The same input with the stick and rudder replaced, as an assist hands them back. */
    public PilotInput withStickAndRudder(float x, float y, float newRudder) {
        return new PilotInput(this.keys, x, y, newRudder, this.actions);
    }

    public PilotInput withAction(Action action) {
        EnumSet<Action> all = this.actions.isEmpty() ? EnumSet.noneOf(Action.class) : EnumSet.copyOf(this.actions);
        all.add(action);
        return new PilotInput(this.keys, this.stickX, this.stickY, this.rudder, all);
    }

    /** The movement keys, as the control packet carries them. */
    public enum Key {
        THROTTLE_UP,
        THROTTLE_DOWN,
        LEFT,
        RIGHT,
        BRAKE;

        int bit() {
            return 1 << ordinal();
        }
    }

    /** One-shot switches; each maps to one field of the control packet. */
    public enum Action {
        GEAR_UP,
        GEAR_DOWN,
        VTOL_ON,
        VTOL_OFF,
        HOVER_ON,
        HOVER_OFF,
        FOLD_BLADES,
        UNFOLD_BLADES,
        FOLD_WINGS,
        UNFOLD_WINGS
    }
}
