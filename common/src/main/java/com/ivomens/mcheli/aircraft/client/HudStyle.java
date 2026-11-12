package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.hud.client.ScriptedHudStyle;
import net.minecraft.world.entity.player.Player;

/**
 * A way to draw a vehicle's seat HUD (attitude, weapon status, sights...): the {@link ScriptedHudStyle scripted}
 * HUD files of the vehicle, or the {@link com.ivomens.mcheli.hud.client.modern.ModernHudStyle modern} HUD. A new
 * style only implements this.
 */
@FunctionalInterface
public interface HudStyle {

    /**
     * @param hudSeat    the seat whose HUD is shown; the pilot in gunner mode sees the gunner seat's HUD
     * @param autoScaled whether the player scales the HUD with the window; a style may ignore it
     */
    void draw(HudCanvas canvas, AircraftEntity vehicle, Player viewer, int hudSeat, float partialTicks,
              boolean autoScaled);
}
