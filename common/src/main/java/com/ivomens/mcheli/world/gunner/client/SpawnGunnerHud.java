package com.ivomens.mcheli.world.gunner.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.client.overlay.HudCanvas.LineMode;
import com.ivomens.mcheli.client.overlay.HudOverlay;
import com.ivomens.mcheli.world.gunner.GunnerEntity;
import com.ivomens.mcheli.world.gunner.SpawnGunnerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Shows which gunner / seat the held spawn-gunner item points at. */
public class SpawnGunnerHud extends HudOverlay {

    private static final int GUNNER_COLOR = 0xFF808000;
    private static final int TAKEN_COLOR = 0xFFFF0000;
    private static final int FREE_COLOR = 0xFF00FFFF;

    /** Target under the crosshair, refreshed once per client tick. */
    private Entity cachedTarget;

    public SpawnGunnerHud(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public boolean isDrawGui(Player player) {
        return player != null && player.level() != null && !player.getMainHandItem().isEmpty()
                && player.getMainHandItem().getItem() instanceof SpawnGunnerItem;
    }

    @Override
    public void onTick() {
        Player player = this.mc.player;
        this.cachedTarget = player != null && this.isDrawGui(player)
                ? SpawnGunnerItem.findTarget(player.level(), player)
                : null;
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        if (!isThirdPersonView && this.isDrawGui(player)) {
            this.lineWidth(scaleFactor);
            this.draw(this.cachedTarget);
        }
    }

    private void box(double x, double y, int color, boolean crossed) {
        int half = 10;
        this.drawLine(new double[]{x - half, y - half, x + half, y - half, x + half, y + half, x - half, y + half},
                color, LineMode.LINE_LOOP);
        if (crossed) {
            this.drawLine(new double[]{x - half, y - half, x + half, y + half, x + half, y - half, x - half, y + half}, color);
        }
    }

    private void draw(Entity entity) {
        if (entity == null) return;
        double px = this.centerX;
        double py = this.centerY;
        if (entity instanceof GunnerEntity gunner) {
            String seatName = "";
            if (gunner.getVehicle() instanceof SeatEntity seat) {
                seatName = "(seat " + (seat.seatID() + 2) + ")";
            } else if (gunner.getVehicle() instanceof AircraftEntity) {
                seatName = "(seat 1)";
            }
            this.drawCenteredString(gunner.getTeamName() + " Gunner " + seatName, (int) px, (int) py + 20, GUNNER_COLOR);
            this.box(px, py, GUNNER_COLOR, false);
        } else if (entity instanceof SeatEntity seat) {
            boolean taken = seat.getRiddenByEntity() != null;
            int color = taken ? TAKEN_COLOR : FREE_COLOR;
            this.drawCenteredString("seat " + (seat.seatID() + 2), (int) px, (int) py + 20, color);
            this.box(px, py, color, taken);
        } else if (entity instanceof AircraftEntity ac) {
            boolean taken = ac.getRiddenByEntity() != null;
            int color = taken ? TAKEN_COLOR : FREE_COLOR;
            this.drawCenteredString("seat 1", (int) px, (int) py + 20, color);
            this.box(px, py, color, taken);
        }
    }
}
