package com.ivomens.mcheli.hud.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.client.HudStyle;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.hud.client.modern.ModernHud;
import com.ivomens.mcheli.hud.client.modern.ModernHudStyle;
import com.ivomens.mcheli.hud.client.widget.HudLayout;
import com.ivomens.mcheli.hud.client.widget.HudWidgetLayer;
import com.ivomens.mcheli.hud.client.widget.HudWidgetRegistry;
import com.ivomens.mcheli.vehicle.client.input.MouseStick;
import net.minecraft.world.entity.player.Player;

/**
 * The style the {@code HudStyle} config option picks, checked every frame so a config reload takes effect at
 * once. TV missile guidance always uses the scripted TV HUD, since the camera then looks through the missile
 * rather than the cockpit. The HUD widgets of addons are drawn on top of either style.
 */
public final class ConfiguredHudStyle implements HudStyle {

    private final HudStyle scripted;
    private final HudStyle modern = new ModernHudStyle();
    private final HudWidgetRegistry widgets;
    private final HudWidgetLayer widgetLayer;

    /**
     * @param stick   the mouse stick, whose deflection the scripted HUDs show
     * @param widgets the HUD widgets addons registered
     */
    public ConfiguredHudStyle(MouseStick stick, HudWidgetRegistry widgets) {
        this.scripted = new ScriptedHudStyle(stick);
        this.widgets = widgets;
        this.widgetLayer = new HudWidgetLayer(widgets);
    }

    @Override
    public void draw(HudCanvas canvas, AircraftEntity vehicle, Player viewer, int hudSeat, float partialTicks,
                     boolean autoScaled) {
        boolean modernHud = McheliConfig.useModernHud() && !isSteeringTvMissile(vehicle, viewer);
        HudStyle style = modernHud ? this.modern : this.scripted;
        style.draw(canvas, vehicle, viewer, hudSeat, partialTicks, autoScaled);
        if (!this.widgets.isEmpty()) {
            drawWidgets(canvas, vehicle, viewer, partialTicks, modernHud);
        }
    }

    private void drawWidgets(HudCanvas canvas, AircraftEntity vehicle, Player viewer, float partialTicks,
                             boolean modernHud) {
        HudLayout layout = !modernHud ? HudLayout.LEGACY
                : ModernHud.drawsFlightLayout(vehicle, viewer) ? HudLayout.FLIGHT : HudLayout.SIGHT;
        this.widgetLayer.draw(canvas, vehicle, viewer, vehicle.seats().seatIdOf(viewer), partialTicks, layout,
                McheliConfig.hudColor(), (float) McheliConfig.hud().scale());
    }

    private static boolean isSteeringTvMissile(AircraftEntity vehicle, Player viewer) {
        return vehicle.cameras().tvMissile() != null && vehicle.cameras().usesMissileCamera(viewer);
    }
}
