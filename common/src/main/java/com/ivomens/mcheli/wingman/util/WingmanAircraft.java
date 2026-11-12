package com.ivomens.mcheli.wingman.util;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import net.minecraft.world.entity.Entity;

/** Small aircraft helpers shared by the wingman commands, GUIs and packets. */
public final class WingmanAircraft {

    private WingmanAircraft() {}

    public static boolean isAircraft(Entity entity) {
        return entity instanceof AircraftEntity;
    }

    /** Fills the aircraft's tank; aircraft without a fuel budget are left alone. */
    public static void fillFuel(Entity aircraft) {
        if (!(aircraft instanceof AircraftEntity ac)) return;
        int max = ac.fuel().capacity();
        if (max > 0) ac.fuel().setAmount(max);
    }

    /**
     * Display name for GUIs and commands: the aircraft model name (for example "MQ-9"), else its kind,
     * else the entity class name.
     */
    public static String getAircraftName(Entity e) {
        if (e instanceof AircraftEntity ac) {
            String type = ac.identity().typeName();
            if (type != null && !type.isEmpty()) return type;
            String kind = ac.kind().directory();
            if (kind != null && !kind.isEmpty()) return kind;
        }
        return e != null ? e.getClass().getSimpleName() : "?";
    }
}
