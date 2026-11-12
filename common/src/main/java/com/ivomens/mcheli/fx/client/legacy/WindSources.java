package com.ivomens.mcheli.fx.client.legacy;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.util.state.StaticCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The vehicles that blow smoke around: those with the throttle above 0.1. Every smoke particle asks for
 * them, so they are gathered once per level tick and shared; the list is a cache of the level's state.
 */
public final class WindSources {

    private static final List<Vehicle> SOURCES = new ArrayList<>();
    @StaticCache(reset = "reset")
    private static long gatheredAt = Long.MIN_VALUE;
    @StaticCache(reset = "reset")
    private static Level gatheredIn;

    private WindSources() {
    }

    /** The vehicles blowing smoke in the level this tick. */
    static List<Vehicle> of(Level level) {
        long time = level.getGameTime();
        if (time != gatheredAt || level != gatheredIn) {
            gatheredAt = time;
            gatheredIn = level;
            SOURCES.clear();
            if (level instanceof ClientLevel clientLevel) {
                for (Entity entity : clientLevel.entitiesForRendering()) {
                    if (entity instanceof Vehicle vehicle && vehicle.throttle() > 0.1F) {
                        SOURCES.add(vehicle);
                    }
                }
            }
        }
        return SOURCES;
    }

    /** Forgets the gathered vehicles, and with them the level; called when the client leaves a world. */
    public static void reset() {
        gatheredAt = Long.MIN_VALUE;
        gatheredIn = null;
        SOURCES.clear();
    }
}
