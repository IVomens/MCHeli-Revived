package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import com.ivomens.mcheli.vehicle.client.camera.VehicleCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Keeps the rider's view consistent with the vehicle every frame: the screen roll, the player's rotation
 * when a seat fixes the view, the rider look the vehicle aims with, and the rotation of the view
 * stand-in.
 */
final class RiderView {

    private final VehicleCamera camera;

    RiderView(VehicleCamera camera) {
        this.camera = camera;
    }

    /** Rolls the view with the airframe; looking sideways the roll shows up as pitch instead. */
    void updateRoll(AircraftEntity aircraft, Player player) {
        float roll = Mth.wrapDegrees(aircraft.roll());
        float yaw = Mth.wrapDegrees(aircraft.getYRot() - player.getYRot());
        roll *= Mth.cos((float) (yaw * Math.PI / 180.0));

        if ((aircraft instanceof TankEntity || aircraft instanceof GroundVehicleEntity) &&
                player.getVehicle() instanceof SeatEntity &&
                !aircraft.modes().usesGunnerView(player)) {
            roll = 0.0F;
        }

        if (aircraft.cameras().tvMissile() != null && aircraft.cameras().tvMissile().shooter() == Minecraft.getInstance().player &&
                aircraft.modes().usesGunnerView(player)) {
            roll = 0.0F;
        }

        this.camera.setRoll(roll);
        unwrapViewDummyYaw(player);
    }

    /** No roll: the view stays level. */
    void level() {
        this.camera.setRoll(0.0F);
    }

    /** The player looks exactly where the vehicle points. */
    static void lockToVehicle(AircraftEntity aircraft, Player player) {
        player.setYRot(aircraft.getYRot());
        player.yRotO = aircraft.getYRot();
        player.setXRot(aircraft.getXRot());
        player.xRotO = aircraft.getXRot();
    }

    /** The player looks along the seat's fixed direction relative to the vehicle. */
    static void fixToSeat(AircraftEntity aircraft, SeatInfo seatInfo, Player player) {
        player.setYRot(aircraft.getYRot() + seatInfo.fixYaw);
        player.setXRot(aircraft.getXRot() + seatInfo.fixPitch);

        // Past straight up or down, mirror the pitch back and look the other way.
        if (player.getXRot() > 90.0F) {
            player.xRotO -= (player.getXRot() - 90.0F) * 2.0F;
            player.setXRot(player.getXRot() - (player.getXRot() - 90.0F) * 2.0F);
            player.yRotO += 180.0F;
            player.setYRot(player.getYRot() + 180.0F);
        } else if (player.getXRot() < -90.0F) {
            player.xRotO -= (player.getXRot() + 90.0F) * 2.0F;
            player.setXRot(player.getXRot() - (player.getXRot() + 90.0F) * 2.0F);
            player.yRotO += 180.0F;
            player.setYRot(player.getYRot() + 180.0F);
        }
    }

    /** The pilot's look (or the slewed turret) becomes the vehicle's rider look; the guns follow it. */
    static void updateRiderLook(AircraftEntity aircraft, Player player) {
        if (aircraft.seats().seatIdOf(player) == 0 && !aircraft.isDestroyed()) {
            if (aircraft.aim().isDetachedActive()) {
                aircraft.aim().setRiderLook(aircraft.aim().prevDetachedYaw(), aircraft.aim().detachedYaw(),
                        aircraft.aim().prevDetachedPitch(), aircraft.aim().detachedPitch());
            } else {
                aircraft.aim().setRiderLook(player.yRotO, player.getYRot(), player.xRotO, player.getXRot());
            }
        }
        aircraft.weapons().aiming().updateRotations();
    }

    /**
     * The view stand-in turns with the player; a weapon that fixes the camera pitch keeps it level.
     *
     * @param aircraft the vehicle the player controls, or null for other stand-in views (GLTD, UAV station)
     */
    void turnViewDummy(@Nullable AircraftEntity aircraft, Player player) {
        Entity viewEntity = this.camera.viewDummy(player.level());
        if (viewEntity == null) {
            return;
        }
        viewEntity.setYRot(player.getYRot());
        viewEntity.yRotO = player.yRotO;
        if (aircraft != null) {
            WeaponGroup weaponSet = aircraft.weapons().current(player);
            if (weaponSet != null && weaponSet.spec() != null && weaponSet.spec().camera().fixPitch()) {
                viewEntity.setXRot(0.0F);
                viewEntity.xRotO = 0.0F;
            }
        }
    }

    /** Keeps the stand-in's previous yaw on the same side of the wrap, so it interpolates the short way. */
    private void unwrapViewDummyYaw(Entity entity) {
        Entity viewEntity = this.camera.viewDummy(entity.level());
        if (viewEntity != null) {
            if (viewEntity.getYRot() - viewEntity.yRotO > 180.0F) {
                viewEntity.yRotO += 360.0F;
            } else if (viewEntity.getYRot() - viewEntity.yRotO < -180.0F) {
                viewEntity.yRotO -= 360.0F;
            }
        }
    }
}
