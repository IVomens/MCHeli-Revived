package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.client.render.ScreenProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Hit marker shown for a moment whenever one of the player's bullets hits, in or out of a vehicle. */
public class HitMarkerOverlay extends AircraftHudBase {

    private static final int DURATION_TICKS = 15;
    private static final int COLOR = 0xCFFFFFFF;

    private int ticksLeft = 0;

    public HitMarkerOverlay(Minecraft minecraft, ScreenProjection projection) {
        super(minecraft, projection);
    }

    @Override
    public boolean isDrawGui(Player player) {
        return true;
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        this.lineWidth(scaleFactor);
        this.drawHitMarker(this.ticksLeft, DURATION_TICKS, COLOR);
    }

    @Override
    public void onTick() {
        if (this.ticksLeft > 0) {
            this.ticksLeft--;
        }
    }

    public void hitBullet() {
        this.ticksLeft = DURATION_TICKS;
    }
}
