package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.config.McheliConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Shared frame of the per vehicle-type overlays: draws the seat HUD in the {@link HudStyle} it is given, on the
 * overlay's canvas, and lays out the key hint columns.
 */
public abstract class VehicleHudOverlay extends AircraftHudBase {

    protected static final int KEY_HINT_COLOR = 0xAFFFFFFF;
    protected static final int KEY_HINT_INACTIVE_COLOR = 0xAF8F8F8F;
    protected static final int HIT_MARKER_COLOR = 0xFF28D448;
    private static final int KEY_HINT_TOP_OFFSET = 80;

    private final HudStyle seatHud;

    protected VehicleHudOverlay(Minecraft minecraft, HudStyle seatHud, ScreenProjection projection) {
        super(minecraft, projection);
        this.seatHud = seatHud;
    }

    /** The seat HUD and key hints are hidden in third person unless the player enabled them. */
    protected static boolean showsHud(boolean isThirdPersonView) {
        return !isThirdPersonView || McheliConfig.hud().displayInThirdPerson();
    }

    /** The pilot in gunner mode looks through the gunner sight, so he gets the gunner seat's HUD. */
    protected static int gunnerAwareHudSeat(AircraftEntity vehicle, Player player, int seatId) {
        return seatId == 0 && vehicle.modes().usesGunnerView(player) ? 1 : seatId;
    }

    protected void drawSeatHud(AircraftEntity vehicle, Player player, int hudSeat) {
        this.seatHud.draw(this.canvas, vehicle, player, hudSeat, this.smoothCamPartialTicks,
                isExperimentalAutoScaleAircraftGui());
    }

    /** Resets both key hint columns to their top and returns their layout. */
    protected LayoutTheme beginKeyHints() {
        int screenWidth = this.centerX * 2;
        int leftX = Math.max(10, (int) (screenWidth * 0.05));
        int rightX = Math.min(screenWidth - 100, (int) (screenWidth * 0.80));
        this.currentLeftY = this.centerY - KEY_HINT_TOP_OFFSET;
        this.currentRightY = this.centerY - KEY_HINT_TOP_OFFSET;
        return new LayoutTheme(leftX, rightX, KEY_HINT_COLOR, KEY_HINT_INACTIVE_COLOR);
    }
}
