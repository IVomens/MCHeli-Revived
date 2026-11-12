package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.content.def.weapon.CasingSpec;

/** Spent case ejected by a gun: model name, ejection direction and speed, bounce, gravity and scale. */
public class Cartridge {

    public final String name;
    public final float acceleration;
    public final float yaw;
    public final float pitch;
    public final float bound;
    public final float gravity;
    public final float scale;
    /**
     * Always null and read by nothing: the client looks the case model up by {@link #name}. The field stays
     * only because the canonical content dump lists it.
     */
    @SuppressWarnings("unused")
    private final Object model = null;

    public Cartridge(String name, float acceleration, float yaw, float pitch, float bound, float gravity, float scale) {
        this.name = name;
        this.acceleration = acceleration;
        this.yaw = yaw;
        this.pitch = pitch;
        this.bound = bound;
        this.gravity = gravity;
        this.scale = scale;
    }

    /** The casing a weapon file describes. */
    public static Cartridge of(CasingSpec casing) {
        return new Cartridge(casing.name(), casing.acceleration(), casing.yaw(), casing.pitch(), casing.bound(),
                casing.gravity(), casing.scale());
    }
}
