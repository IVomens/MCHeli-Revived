package com.ivomens.mcheli.hud.client.instrument;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import net.minecraft.world.entity.player.Player;

/**
 * An instrument drawn in code rather than from a HUD script: the radar warning receiver, the BVR lock
 * markers, the GPS marker and the mortar radar. The MCHeli HUD layer draws all of them for the aircraft the
 * local player rides or controls; each one decides for itself whether it shows.
 */
@FunctionalInterface
public interface Instrument {

    void draw(HudCanvas canvas, float partialTicks, Player player, AircraftEntity vehicle);

    static double interpolate(double now, double old, float partialTicks) {
        return old + (now - old) * partialTicks;
    }
}
