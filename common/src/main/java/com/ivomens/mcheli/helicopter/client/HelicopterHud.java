package com.ivomens.mcheli.helicopter.client;

import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.client.VehicleHudOverlay;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.aircraft.client.HudStyle;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class HelicopterHud extends VehicleHudOverlay {

    private static final int GUNNER_HIT_MARKER_COLOR = 0xCFFFFFFF;

    public HelicopterHud(Minecraft minecraft, HudStyle seatHud, ScreenProjection projection) {
        super(minecraft, seatHud, projection);
    }

    @Override
    public boolean isDrawGui(Player player) {
        return RiddenVehicle.of(player) instanceof HelicopterEntity;
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        if (!(RiddenVehicle.of(player) instanceof HelicopterEntity heli) || heli.isDestroyed()) {
            return;
        }
        int seatId = heli.seats().seatIdOf(player);
        boolean showHud = showsHud(isThirdPersonView);
        this.lineWidth(scaleFactor);
        if (heli.cameras().mode(player) == 1) {
            this.drawNightVisionNoise();
        }

        if (showHud) {
            this.drawSeatHud(heli, player, gunnerAwareHudSeat(heli, player, seatId));
            this.drawTurretBallistics(heli, player);
        }

        if (!heli.modes().usesGunnerView(player)) {
            if (showHud) {
                this.drawKeyBind(heli, player, seatId);
            }
            this.drawHitMarker(heli, HIT_MARKER_COLOR, seatId);
            return;
        }

        if (showHud) {
            ProjectileEntity tvMissile = heli.cameras().tvMissile();
            if (!heli.cameras().usesMissileCamera(player)) {
                this.drawKeyBind(heli, player, seatId);
            } else if (tvMissile != null) {
                this.drawTvMissileNoise(heli, tvMissile);
            }
        }
        this.drawHitMarker(heli, GUNNER_HIT_MARKER_COLOR, seatId);
    }

    public void drawKeyBind(HelicopterEntity heli, Player player, int seatId) {
        VehicleSpec info = heli.helicopterSpec();
        if (McheliConfig.render().hideKeybind() || info == null) return;

        // the modern HUD has its own layout and leaves the key reference to the controls screen
        if (McheliConfig.useModernHud()) return;
        LayoutTheme theme = beginKeyHints();
        drawAircraftKeyBinds(heli, info, player, seatId, theme);

        boolean freeLookPressed = InputKey.isKeyDown(McheliConfig.keys().freeLook());
        boolean gunnerMode = heli.modes().usesGunnerView(player);

        if (seatId == 0 && info.file().features().gunnerMode() && !freeLookPressed) {
            int color = heli.modes().isHoveringMode() ? theme.inactive() : theme.active();
            drawRightKey(gunnerMode ? "Normal" : "Gunner", McheliConfig.keys().switchMode(), theme.rightX(), color);
        }

        if (seatId > 0 && heli.modes().canSwitchGunnerModeOtherSeat(player)) {
            drawRightKey(gunnerMode ? "Normal" : "Camera", McheliConfig.keys().switchMode(), theme.rightX(), theme.active());
        }

        if (seatId == 0 && !freeLookPressed) {
            int color = gunnerMode ? theme.inactive() : theme.active();
            drawRightKey(gunnerMode ? "Normal" : "Hovering", McheliConfig.keys().switchHovering(), theme.rightX(), color);
        }

        if (seatId == 0) {
            var towChain = heli.tow().towChain();
            if (towChain != null && !towChain.entity().isRemoved()) {
                drawRightKey("Drop", McheliConfig.keys().extra(), theme.rightX(), theme.active());
            } else if (info.file().kindFeatures().foldableBlades() && heli.engine().throttle() <= 0.01
                    && !VehicleMotion.isClearBelow(heli, 1, 2)) {
                drawRightKey("FoldBlade", McheliConfig.keys().extra(), theme.rightX(), theme.active());
            }
        }

        if ((gunnerMode || heli.isUAV()) && info.file().camera().zoom() > 1) {
            drawLeftKey("Zoom", McheliConfig.keys().zoom(), theme.leftX(), theme.active());
        } else if (seatId == 0 && (heli.parts().canUnfoldHatch() || heli.parts().canFoldHatch())) {
            drawLeftKey("OpenHatch", McheliConfig.keys().zoom(), theme.leftX(), theme.active());
        }
    }
}
