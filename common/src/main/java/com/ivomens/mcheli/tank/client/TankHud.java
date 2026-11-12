package com.ivomens.mcheli.tank.client;

import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.client.VehicleHudOverlay;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.aircraft.client.HudStyle;
import com.ivomens.mcheli.tank.TankEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class TankHud extends VehicleHudOverlay {

    public TankHud(Minecraft minecraft, HudStyle seatHud, ScreenProjection projection) {
        super(minecraft, seatHud, projection);
    }

    @Override
    public boolean isDrawGui(Player player) {
        return RiddenVehicle.of(player) instanceof TankEntity;
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        if (!(RiddenVehicle.of(player) instanceof TankEntity tank) || tank.isDestroyed()) {
            return;
        }
        int seatId = tank.seats().seatIdOf(player);
        this.lineWidth(scaleFactor);
        if (tank.cameras().mode(player) == 1) {
            this.drawNightVisionNoise();
        }

        boolean showHud = showsHud(isThirdPersonView);
        if (showHud) {
            this.drawSeatHud(tank, player, seatId);
            this.drawTurretBallistics(tank, player);
        }
        this.drawDebugInfo(tank);
        if (showHud) {
            if (tank.cameras().tvMissile() == null || !tank.modes().usesGunnerView(player) && !tank.isUAV()) {
                this.drawKeybind(tank, player, seatId);
            } else {
                this.drawTvMissileNoise(tank, tank.cameras().tvMissile());
            }
        }
        this.drawHitMarker(tank, HIT_MARKER_COLOR, seatId);
    }

    public void drawKeybind(TankEntity tank, Player player, int seatId) {
        VehicleSpec info = tank.tankSpec();
        if (McheliConfig.render().hideKeybind() || info == null) return;

        // the modern HUD has its own layout and leaves the key reference to the controls screen
        if (McheliConfig.useModernHud()) return;
        LayoutTheme theme = beginKeyHints();
        drawAircraftKeyBinds(tank, info, player, seatId, theme);

        if (seatId == 0 && tank.keys().hasBrake()) {
            drawRightKey("Brake", McheliConfig.keys().switchHovering(), theme.rightX(), theme.active());
        }

        if (seatId > 0 && tank.modes().canSwitchGunnerModeOtherSeat(player)) {
            String mode = tank.modes().usesGunnerView(player) ? "Normal" : "Camera";
            drawRightKey(mode, McheliConfig.keys().switchMode(), theme.rightX(), theme.active());
        }

        if (tank.modes().usesGunnerView(player) && info.file().camera().zoom() > 1) {
            drawLeftKey("Zoom", McheliConfig.keys().zoom(), theme.leftX(), theme.active());
        } else if (seatId == 0 && (tank.parts().canUnfoldHatch() || tank.parts().canFoldHatch())) {
            drawLeftKey("OpenHatch", McheliConfig.keys().zoom(), theme.leftX(), theme.active());
        }
    }
}
