package com.ivomens.mcheli.gametest.snapshot;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.gametest.golden.SeededRandoms;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Places vehicles the way their item does, with seeded random generators. */
public final class Vehicles {

    private Vehicles() {
    }

    /** Spawns the vehicle at {@code position} facing {@code yaw}; null when it cannot be created. */
    @Nullable
    public static AircraftEntity spawn(ServerLevel level, @Nullable VehicleSpec info, Vec3 position, float yaw,
                                       long seed) {
        if (info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            return null;
        }
        AircraftEntity vehicle = item.createAircraft(level, position.x, position.y, position.z, new ItemStack(item));
        if (vehicle == null) {
            return null;
        }
        vehicle.initRotationYaw(yaw);
        SeededRandoms.seedVehicle(vehicle, seed);
        level.addFreshEntity(vehicle);
        return vehicle;
    }

    /** Position and motion with full precision, for golden files. */
    public static String vec(Vec3 v) {
        return v.x + " " + v.y + " " + v.z;
    }
}
