package com.ivomens.mcheli.hud.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.client.HudStyle;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.hud.client.modern.ModernHudStyle;
import com.ivomens.mcheli.vehicle.client.input.MouseStick;
import net.minecraft.world.entity.player.Player;

/**
 * The style the {@code HudStyle} config option picks, checked every frame so a config reload takes effect at
 * once. TV missile guidance always uses the scripted TV HUD, since the camera then looks through the missile
 * rather than the cockpit.
 */
public final class ConfiguredHudStyle implements HudStyle {

    private final HudStyle scripted;
    private final HudStyle modern = new ModernHudStyle();

    /** @param stick the mouse stick, whose deflection the scripted HUDs show */
    public ConfiguredHudStyle(MouseStick stick) {
        this.scripted = new ScriptedHudStyle(stick);
    }

    @Override
    public void draw(HudCanvas canvas, AircraftEntity vehicle, Player viewer, int hudSeat, float partialTicks,
                     boolean autoScaled) {
        HudStyle style = McheliConfig.useModernHud() && !isSteeringTvMissile(vehicle, viewer) ? this.modern
                : this.scripted;
        style.draw(canvas, vehicle, viewer, hudSeat, partialTicks, autoScaled);
    }

    private static boolean isSteeringTvMissile(AircraftEntity vehicle, Player viewer) {
        return vehicle.cameras().tvMissile() != null && vehicle.cameras().usesMissileCamera(viewer);
    }
}
