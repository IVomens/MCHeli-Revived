package com.ivomens.mcheli.hud.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.client.HudStyle;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.hud.Hud;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.core.hud.HudFrame;
import com.ivomens.mcheli.hud.HudManager;
import com.ivomens.mcheli.vehicle.client.input.MouseStick;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The scripted HUD files a vehicle definition names: the TV missile HUD while the viewer steers a TV missile,
 * otherwise the HUD of the seat. Each frame reads the vehicle into one {@link HudFrame} and runs the script.
 */
public final class ScriptedHudStyle implements HudStyle {

    private final HudTelemetry telemetry;
    private final HudFrame frame = new HudFrame();
    private final HudRenderer renderer = new HudRenderer(name -> HudManager.get(name).orElse(null));

    /** @param stick the mouse stick, whose deflection the HUDs show */
    public ScriptedHudStyle(MouseStick stick) {
        this.telemetry = new HudTelemetry(stick);
    }

    @Override
    public void draw(HudCanvas canvas, AircraftEntity vehicle, Player viewer, int hudSeat, float partialTicks,
                     boolean autoScaled) {
        Hud hud = select(vehicle, viewer, hudSeat);
        if (hud == null || this.renderer.session().isBroken(hud)) return;

        this.telemetry.read(this.frame, vehicle, viewer, partialTicks, canvas);
        this.renderer.render(hud, this.frame, canvas, autoScaled);
        if (McheliConfig.debug().testMode()) {
            VariablesOverlay.draw(this.frame, canvas);
        }
    }

    @Nullable
    private static Hud select(AircraftEntity vehicle, Player viewer, int hudSeat) {
        VehicleSpec info = vehicle.spec();
        if (info == null) return null;
        Hud tvMissile = HudManager.get(VehicleHuds.TV_MISSILE).orElse(null);
        if (tvMissile != null && vehicle.cameras().usesMissileCamera(viewer) && vehicle.cameras().tvMissile() != null) {
            return tvMissile;
        }
        List<Hud> seats = VehicleHuds.of(info);
        return hudSeat >= 0 && hudSeat < seats.size() ? seats.get(hudSeat) : null;
    }
}
