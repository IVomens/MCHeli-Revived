package com.ivomens.mcheli.vehicle.client;

import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.client.VehicleHudOverlay;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.config.client.KeyNames;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.aircraft.client.HudStyle;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class VehicleHud extends VehicleHudOverlay {

    private static final int VEHICLE_HIT_MARKER_COLOR = 0x00C90E;

    public VehicleHud(Minecraft minecraft, HudStyle seatHud, ScreenProjection projection) {
        super(minecraft, seatHud, projection);
    }

    @Override
    public boolean isDrawGui(Player player) {
        // Also covers players in additional seats, who ride a SeatEntity rather than the vehicle.
        return RiddenVehicle.of(player) instanceof GroundVehicleEntity;
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        if (!(RiddenVehicle.of(player) instanceof GroundVehicleEntity vehicle)
                || vehicle.isDestroyed()) {
            return;
        }
        int seatId = vehicle.seats().seatIdOf(player);
        this.lineWidth(scaleFactor);
        if (vehicle.cameras().mode(player) == 1) {
            this.drawNightVisionNoise();
        }
        if (vehicle.modes().usesGunnerView(player) && vehicle.cameras().tvMissile() != null) {
            this.drawTvMissileNoise(vehicle, vehicle.cameras().tvMissile());
        }

        if (showsHud(isThirdPersonView)) {
            this.drawSeatHud(vehicle, player, seatId);
            this.drawTurretBallistics(vehicle, player);
            this.drawKeyBind(vehicle, player);
        }
        this.drawHitMarker(vehicle, VEHICLE_HIT_MARKER_COLOR, seatId);
    }

    public void drawKeyBind(GroundVehicleEntity vehicle, Player player) {
        VehicleSpec info = vehicle.groundVehicleSpec();
        if (McheliConfig.render().hideKeybind() || info == null) return;

        // the modern HUD has its own layout and leaves the key reference to the controls screen
        if (McheliConfig.useModernHud()) return;
        LayoutTheme theme = beginKeyHints();

        if (vehicle.countermeasures().haveFlare()) {
            int color = vehicle.countermeasures().isFlarePreparation() ? theme.inactive() : theme.active();
            drawRightKey("Flare", McheliConfig.keys().flare(), theme.rightX(), color);
        }
        String gunnerHint = "Gunner %s : %s + %s".formatted(
                vehicle.seats().gunnersAllowed() ? "ON" : "OFF",
                KeyNames.getDescOrName(McheliConfig.keys().freeLook()),
                KeyNames.getDescOrName(McheliConfig.keys().cameraMode()));
        drawLeftText(gunnerHint, theme);

        var towChain = vehicle.tow().towChain();
        if (vehicle.getSizeInventory() <= 0 || (towChain != null && !towChain.entity().isRemoved())) {
            drawRightKey("Drop", McheliConfig.keys().extra(), theme.rightX(), theme.active());
        }
        if (vehicle.cameras().camera().getCameraZoom() > 1.0F) {
            drawLeftKey("Zoom", McheliConfig.keys().zoom(), theme.leftX(), theme.active());
        }
        if (vehicle.weapons().count() > 1) {
            drawLeftKey("Weapon", McheliConfig.keys().switchWeapon2(), theme.leftX(), theme.active());
        }
        WeaponGroup weaponSet = vehicle.weapons().current(player);
        if (weaponSet != null && weaponSet.currentMount() != null && weaponSet.currentMount().stats().modes() > 0) {
            drawLeftKey("WeaponMode", McheliConfig.keys().switchWeaponMode(), theme.leftX(), theme.active());
        }
        if (info.file().features().nightVision()) {
            drawLeftKey("CameraMode", McheliConfig.keys().cameraMode(), theme.leftX(), theme.active());
        }
        if (vehicle.seats().count() >= 2) {
            drawLeftKey("Dismount", McheliConfig.keys().unmount(), theme.leftX(), theme.active());
        }
        drawLeftText("Dismount all : LShift", theme);
    }

    private void drawLeftText(String text, LayoutTheme theme) {
        drawString(text, theme.leftX(), this.currentLeftY, theme.active());
        this.currentLeftY += 10;
    }
}
