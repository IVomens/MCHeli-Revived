package com.ivomens.mcheli.plane.client;

import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.client.VehicleHudOverlay;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.aircraft.client.HudStyle;
import com.ivomens.mcheli.plane.AbstractPlaneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** HUD and key hints for planes and ships. */
public abstract class AbstractPlaneHud<T extends AbstractPlaneEntity> extends VehicleHudOverlay {

    private final Class<T> entityType;
    private final boolean drawsDebugInfo;

    protected AbstractPlaneHud(Minecraft minecraft, HudStyle seatHud, ScreenProjection projection, Class<T> entityType, boolean drawsDebugInfo) {
        super(minecraft, seatHud, projection);
        this.entityType = entityType;
        this.drawsDebugInfo = drawsDebugInfo;
    }

    @Override
    public boolean isDrawGui(Player player) {
        return entityType.isInstance(RiddenVehicle.of(player));
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        AircraftEntity aircraft = RiddenVehicle.of(player);
        if (!entityType.isInstance(aircraft) || aircraft.isDestroyed()) {
            return;
        }
        T plane = entityType.cast(aircraft);
        int seatId = plane.seats().seatIdOf(player);
        boolean showHud = showsHud(isThirdPersonView);
        this.lineWidth(scaleFactor);
        if (plane.cameras().mode(player) == 1) {
            this.drawNightVisionNoise();
        }

        if (showHud) {
            this.drawSeatHud(plane, player, gunnerAwareHudSeat(plane, player, seatId));
            this.drawTurretBallistics(plane, player);
        }
        if (drawsDebugInfo) {
            this.drawDebugInfo(plane);
        }
        if (showHud) {
            if (plane.cameras().tvMissile() == null || !plane.modes().usesGunnerView(player) && !plane.isUAV()) {
                this.drawKeybind(plane, player, seatId);
            } else {
                this.drawTvMissileNoise(plane, plane.cameras().tvMissile());
            }
        }
        this.drawHitMarker(plane, HIT_MARKER_COLOR, seatId);
    }

    public void drawKeybind(T plane, Player player, int seatId) {
        VehicleSpec info = plane.planeSpec();
        if (McheliConfig.render().hideKeybind() || info == null) return;

        // the modern HUD has its own layout and leaves the key reference to the controls screen
        if (McheliConfig.useModernHud()) return;
        LayoutTheme theme = beginKeyHints();
        drawAircraftKeyBinds(plane, info, player, seatId, theme);

        boolean freeLookPressed = InputKey.isKeyDown(McheliConfig.keys().freeLook());
        boolean gunnerMode = plane.modes().usesGunnerView(player);

        if (seatId == 0 && info.file().features().gunnerMode() && !freeLookPressed) {
            int color = plane.modes().isHoveringMode() ? theme.inactive() : theme.active();
            drawRightKey(gunnerMode ? "Normal" : "Gunner", McheliConfig.keys().switchMode(), theme.rightX(), color);
        }

        if (seatId > 0 && plane.modes().canSwitchGunnerModeOtherSeat(player)) {
            drawRightKey(gunnerMode ? "Normal" : "Camera", McheliConfig.keys().switchMode(), theme.rightX(), theme.active());
        }

        if (seatId == 0 && info.file().kindFeatures().vtol().enabled() && !freeLookPressed) {
            int vtolMode = plane.nozzles().mode();
            // mode 1 is the transition, which cannot be interrupted
            if (vtolMode != 1) {
                drawRightKey(vtolMode == 0 ? "VTOL" : "Normal", McheliConfig.keys().extra(), theme.rightX(), theme.active());
            }
        }

        if (plane.ejectionSeats().canEjectSeat(player)) {
            drawRightKey("Eject seat", McheliConfig.keys().switchHovering(), theme.rightX(), theme.active());
        }

        if (gunnerMode && info.file().camera().zoom() > 1) {
            drawLeftKey("Zoom", McheliConfig.keys().zoom(), theme.leftX(), theme.active());
        } else if (seatId == 0) {
            if (plane.wings().canFold() || plane.wings().canUnfold()) {
                drawLeftKey("FoldWing", McheliConfig.keys().zoom(), theme.leftX(), theme.active());
            } else if (plane.parts().canUnfoldHatch() || plane.parts().canFoldHatch()) {
                drawLeftKey("OpenHatch", McheliConfig.keys().zoom(), theme.leftX(), theme.active());
            }
        }
    }
}
