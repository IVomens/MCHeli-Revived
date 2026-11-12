package com.ivomens.mcheli.world.uavstation;

import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.vehicle.uav.IUavStation.StationType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where a station's operator sits: a little in front of it. At a small station the operator sits on
 * the ground, lower still when the block in front is not solid.
 */
final class OperatorSeat {

    private static final double FORWARD = 0.9;

    private OperatorSeat() {
    }

    static double mountedYOffset(UavStationEntity station) {
        if (station.getStationType() == StationType.SMALL && station.getRiddenByEntity() != null) {
            double px = -Math.sin(station.getYRot() * Math.PI / 180.0) * FORWARD;
            double pz = Math.cos(station.getYRot() * Math.PI / 180.0) * FORWARD;
            BlockPos below = BlockPos.containing(station.getX() + px, station.getY() - 0.5,
                    station.getZ() + pz);
            BlockState state = station.level().getBlockState(below);
            return state.isSolidRender(station.level(), below) ? -0.4 : -0.9;
        }
        return 0.35;
    }

    static Vec3 riderPosition(UavStationEntity station, Entity passenger) {
        double x = -Math.sin(station.getYRot() * Math.PI / 180.0) * FORWARD;
        double z = Math.cos(station.getYRot() * Math.PI / 180.0) * FORWARD;
        double y = station.getY() + mountedYOffset(station) - passenger.getVehicleAttachmentPoint(station).y
                + McheliEntity.GLOBAL_SEAT_OFFSET + McheliEntity.GLOBAL_Y_OFFSET;
        return new Vec3(station.getX() + x, y, station.getZ() + z);
    }
}
