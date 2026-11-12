package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * The mouse of a passenger in a seat: free look, a seat that fixes the view, or a view locked to a ground
 * vehicle. The gunner's gun follows the look through the rate-limited weapon rotation.
 */
final class SeatMouse {

    private final MouseStick stick;
    private final RiderView view;
    private final RenderFramePlacement placement;

    SeatMouse(MouseStick stick, RiderView view, RenderFramePlacement placement) {
        this.stick = stick;
        this.view = view;
        this.placement = placement;
    }

    /** The vehicle of the seat the player sits in, or null when not seated. */
    @Nullable
    AircraftEntity parentOf(Player player) {
        return player.getVehicle() instanceof SeatEntity seat ? seat.getParent() : null;
    }

    /**
     * Tanks and ground vehicles without free look keep the view on the vehicle, unless the player looks
     * through a gunner view or (tanks) aims a turret on its own; a ground vehicle only while no weapon is
     * selected.
     */
    static boolean locksView(AircraftEntity aircraft, Player player) {
        if (!(aircraft instanceof TankEntity) && !(aircraft instanceof GroundVehicleEntity) ||
                aircraft.modes().canSwitchFreeLook() ||
                aircraft.modes().usesGunnerView(player)) {
            return false;
        }

        if (aircraft instanceof GroundVehicleEntity) {
            return aircraft.weapons().currentId(player) < 0;
        }

        return !DetachedTurretAim.isUsed(aircraft, player);
    }

    void steer(AircraftEntity aircraft, Player player, float partialTicks) {
        SeatInfo seatInfo = aircraft.seats().info(player);
        boolean fixRot = seatInfo != null && seatInfo.fixRot && aircraft.modes().usesGunnerView(player) &&
                aircraft.modes().isGunnerLookMode(player);
        if (fixRot) {
            this.stick.zero();
        }

        WeaponGroup weaponSet = aircraft.weapons().current(player);
        if (locksView(aircraft, player)) {
            RiderView.lockToVehicle(aircraft, player);
            this.placement.place(aircraft, partialTicks, player);
            this.view.level();
            return;
        }

        // The detached turret aim belongs to the pilot. Seat gunners turn freely and their gun follows
        // through the rate-limited stepping of the weapon rotation.
        if (!DetachedTurretAim.isUsed(aircraft, player)) {
            this.stick.scaleDeltaY(weaponSet != null && weaponSet.spec() != null ?
                    weaponSet.spec().camera().rotationSpeedPitch() : 1.0);
        }

        // Turn and place the camera at the frame's interpolated attitude, then put the tick's attitude back.
        float yaw = aircraft.getYRot();
        float pitch = aircraft.getXRot();
        float roll = aircraft.roll();
        this.stick.turn(player);
        aircraft.setYRot(Mth.lerp(partialTicks, aircraft.yRotO, aircraft.getYRot()));
        aircraft.setXRot(Mth.lerp(partialTicks, aircraft.xRotO, aircraft.getXRot()));
        aircraft.rollAngle().set(aircraft.rollAngle().interpolated(partialTicks));
        if (fixRot) {
            RiderView.fixToSeat(aircraft, seatInfo, player);
        }
        this.placement.place(aircraft, partialTicks, player);
        aircraft.setYRot(yaw);
        aircraft.setXRot(pitch);
        aircraft.rollAngle().set(roll);

        this.stick.scaleStick(0.9);
        this.view.updateRoll(aircraft, player);
    }
}
