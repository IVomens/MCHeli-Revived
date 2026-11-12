package com.ivomens.mcheli.aircraft;

import net.minecraft.world.phys.Vec3;

/** Where crew jump out during a parachute drop, and how many ticks apart. */
public class MobDropOption {

    public final Vec3 pos;
    public final int interval;

    public MobDropOption(Vec3 pos, int interval) {
        this.pos = pos;
        this.interval = interval;
    }

    @Override
    public String toString() {
        return "MobDropOption{pos=" + this.pos + ", interval=" + this.interval + '}';
    }
}
