package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.aircraft.client.GuiInteractSeat;
import com.ivomens.mcheli.aircraft.client.HitMarkerOverlay;
import com.ivomens.mcheli.aircraft.client.HudStyle;
import com.ivomens.mcheli.client.overlay.HudOverlay;
import com.ivomens.mcheli.equipment.gltd.client.GltdScreen;
import com.ivomens.mcheli.equipment.launcher.client.HandheldWeaponView;
import com.ivomens.mcheli.equipment.launcher.client.LauncherSight;
import com.ivomens.mcheli.equipment.rangefinder.client.RangeFinderHud;
import com.ivomens.mcheli.equipment.rangefinder.client.RangeFinderScope;
import com.ivomens.mcheli.equipment.wrench.client.WrenchHud;
import com.ivomens.mcheli.helicopter.client.HelicopterHud;
import com.ivomens.mcheli.multiplay.client.TargetMarkerOverlay;
import com.ivomens.mcheli.multiplay.client.TeamMarkers;
import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.multiplay.client.TeamList;
import com.ivomens.mcheli.multiplay.client.TitleOverlay;
import com.ivomens.mcheli.plane.client.PlaneHud;
import com.ivomens.mcheli.ship.client.ShipHud;
import com.ivomens.mcheli.tank.client.TankHud;
import com.ivomens.mcheli.vehicle.client.VehicleHud;
import com.ivomens.mcheli.world.gunner.client.SpawnGunnerHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.player.Player;

import java.util.function.BooleanSupplier;

/**
 * Every MCHeli overlay, in the order they are drawn from the MCHeli HUD layers: one primary overlay (the
 * vehicle's or held item's, whichever applies first), the secondary ones on top of it, and in the top
 * layer the team list and the titles.
 */
final class Overlays {

    private final HitMarkerOverlay hitMarker;
    private final TitleOverlay titles;
    private final TargetMarkerOverlay targetMarkers;
    /** Every overlay that needs {@link HudOverlay#onTick()}. */
    private final HudOverlay[] ticked;
    /** The overlay of the vehicle or held item; only the first one that applies is drawn. */
    private final HudOverlay[] primary;
    /** Drawn on top of the primary overlay whenever they apply. */
    private final HudOverlay[] secondary;
    private final BooleanSupplier showsTeamList;

    Overlays(Minecraft minecraft, HandheldWeaponView launcher, RangeFinderScope rangeFinder, HudStyle seatHud,
             ScreenProjection projection, TeamMarkers teamMarkers, BooleanSupplier showsTeamList) {
        this.hitMarker = new HitMarkerOverlay(minecraft, projection);
        this.titles = new TitleOverlay(minecraft);
        this.targetMarkers = new TargetMarkerOverlay(minecraft, teamMarkers);
        this.showsTeamList = showsTeamList;
        HudOverlay heli = new HelicopterHud(minecraft, seatHud, projection);
        HudOverlay plane = new PlaneHud(minecraft, seatHud, projection);
        HudOverlay ship = new ShipHud(minecraft, seatHud, projection);
        HudOverlay tank = new TankHud(minecraft, seatHud, projection);
        HudOverlay gltd = new GltdScreen(minecraft);
        HudOverlay vehicle = new VehicleHud(minecraft, seatHud, projection);
        HudOverlay lightWeapon = new LauncherSight(minecraft, launcher);
        HudOverlay wrench = new WrenchHud(minecraft);
        HudOverlay spawnGunner = new SpawnGunnerHud(minecraft);
        HudOverlay rangeFinderHud = new RangeFinderHud(minecraft, rangeFinder);
        HudOverlay interactSeat = new GuiInteractSeat(minecraft);

        this.primary = new HudOverlay[]{rangeFinderHud, lightWeapon, heli, plane, ship, tank, gltd, vehicle};
        this.secondary = new HudOverlay[]{this.hitMarker, wrench, interactSeat, spawnGunner, this.targetMarkers};
        this.ticked = new HudOverlay[]{this.hitMarker, heli, plane, ship, tank, gltd, vehicle, lightWeapon, wrench,
                spawnGunner, rangeFinderHud, this.targetMarkers, this.titles, interactSeat};
    }

    /** The hit marker, flashed when one of the player's bullets hits. */
    HitMarkerOverlay hitMarker() {
        return this.hitMarker;
    }

    /** The titles the server sends. */
    TitleOverlay titles() {
        return this.titles;
    }

    void onTick() {
        for (HudOverlay overlay : this.ticked) {
            overlay.onTick();
        }
    }

    private static boolean draw(HudOverlay overlay, GuiGraphics graphics, float partialTicks) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !overlay.isDrawGui(player)) return false;
        overlay.render(graphics, partialTicks);
        return true;
    }

    /** Overlays stay visible behind the chat and the Flan's Mod vehicle controller screen. */
    private static boolean canDrawOverlays(Minecraft mc) {
        return mc.screen == null || mc.screen instanceof ChatScreen
                || mc.screen.getClass().getName().contains("GuiDriveableController");
    }

    /** Main HUD pass, drawn below the hotbar and chat. */
    void render(GuiGraphics graphics, float partialTicks) {
        if (!canDrawOverlays(Minecraft.getInstance())) return;

        for (HudOverlay overlay : this.primary) {
            if (draw(overlay, graphics, partialTicks)) break;
        }
        for (HudOverlay overlay : this.secondary) {
            draw(overlay, graphics, partialTicks);
        }
    }

    /** Top pass: the team list and titles, drawn above the chat. */
    void renderTop(GuiGraphics graphics, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (!canDrawOverlays(mc)) return;

        if (this.showsTeamList.getAsBoolean()) {
            TeamList.draw(graphics, mc, mc.font, false);
        }
        draw(this.titles, graphics, partialTicks);
    }
}
