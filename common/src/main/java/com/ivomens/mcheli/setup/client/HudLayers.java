package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.client.overlay.ScreenCanvas;
import com.ivomens.mcheli.hud.client.instrument.BvrLockMarkers;
import com.ivomens.mcheli.hud.client.instrument.GpsMarker;
import com.ivomens.mcheli.hud.client.instrument.Instrument;
import com.ivomens.mcheli.hud.client.instrument.MortarRadar;
import com.ivomens.mcheli.hud.client.instrument.RadarWarningReceiver;
import com.ivomens.mcheli.util.Ids;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * GUI layers that draw every MCHeli overlay: aircraft HUD scripts, the instruments (RWR, BVR lock, GPS,
 * mortar radar), hit marker, tool overlays, and on top the multiplayer list and titles. The vanilla hotbar
 * layer is left untouched, so it keeps showing the player's items while seated.
 */
public final class HudLayers {

    public static final ResourceLocation HUD = Ids.of("hud");
    public static final ResourceLocation HUD_TOP = Ids.of("hud_top");

    private HudLayers() {
    }

    /** The main layer, drawn right above the vanilla camera overlays (pumpkin, powder snow...). */
    public static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        float partialTicks = delta.getGameTimeDeltaPartialTick(true);

        renderInstruments(graphics, partialTicks, mc.player);

        ClientTicks handler = McheliClient.session().ticks();
        if (handler != null) handler.renderHud(graphics, partialTicks);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The top layer, drawn right above the vanilla chat. */
    public static void renderTop(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        ClientTicks handler = McheliClient.session().ticks();
        if (handler != null) handler.renderHudTop(graphics, delta.getGameTimeDeltaPartialTick(true));
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderInstruments(GuiGraphics graphics, float partialTicks, Player player) {
        AircraftEntity ac = RiddenVehicle.of(player);
        if (ac == null || ac.spec() == null) return;

        ScreenCanvas canvas = new ScreenCanvas(graphics);
        try {
            for (Instrument instrument : McheliClient.session().instruments()) {
                instrument.draw(canvas, partialTicks, player, ac);
            }
        } finally {
            canvas.resetColor();
        }
    }
}
