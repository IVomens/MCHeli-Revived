package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.util.math.Rotations;
import com.ivomens.mcheli.vehicle.client.camera.VehicleCamera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client, every frame: puts the crew, the seats and the camera where the vehicle is drawn this frame
 * (between its last two ticks), so riders do not lag behind the hull.
 */
final class RenderFramePlacement {

    private final VehicleCamera camera;

    RenderFramePlacement(VehicleCamera camera) {
        this.camera = camera;
    }

    void place(AircraftEntity vehicle, float partialTicks, Player viewer) {
        double x = vehicle.xOld + (vehicle.getX() - vehicle.xOld) * partialTicks;
        double y = vehicle.yOld + (vehicle.getY() - vehicle.yOld) * partialTicks;
        double z = vehicle.zOld + (vehicle.getZ() - vehicle.zOld) * partialTicks;
        Entity pilot = vehicle.getRiddenByEntity();
        if (pilot != null && vehicle.hasPassenger(pilot) && !pilot.isRemoved()) {
            Vec3 seat = vehicle.seats().pilotPosition(x, y, z);
            pilot.setPos(seat.x, seat.y, seat.z);
        }
        vehicle.seats().place(x, y, z, true);
        for (int seatId = 0; seatId < vehicle.seats().count() + 1; seatId++) {
            Entity rider = vehicle.seats().riderOf(seatId);
            if (rider != null) {
                rider.xOld = rider.xo = rider.getX();
                rider.yOld = rider.yo = rider.getY();
                rider.zOld = rider.zo = rider.getZ();
            }
        }

        ProjectileEntity missile = vehicle.cameras().tvMissile();
        if (missile != null && LocalClient.get().isLocalPlayer(missile.shooter())) {
            this.camera.placeViewDummy(missile.xo + (missile.getX() - missile.xo) * partialTicks,
                    missile.yo + (missile.getY() - missile.yo) * partialTicks,
                    missile.zo + (missile.getZ() - missile.zo) * partialTicks);
            return;
        }
        VehicleParts.CameraPosition camera = vehicle.cameras().positionForLocalPlayer();
        if (camera != null && camera.pos() != null) {
            SeatInfo seatInfo = vehicle.seats().info(viewer);
            Vec3 offset = seatInfo != null && seatInfo.rotSeat
                    ? vehicle.seats().turretOffset(camera.pos())
                    : Rotations.rotate(camera.pos(), -vehicle.getYRot(), -vehicle.getXRot(), -vehicle.roll());
            this.camera.placeViewDummy(x + offset.x, y + offset.y, z + offset.z);
        }
    }
}
