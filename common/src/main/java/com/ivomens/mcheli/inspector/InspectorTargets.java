package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.api.vehicle.VehicleHitBox;
import com.ivomens.mcheli.api.vehicle.VehicleSeat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/** Which vehicle the inspector opens on: the one the player is aboard, else the one they look at. */
public final class InspectorTargets {

    /** How far the player can look for a vehicle to inspect. */
    public static final double RANGE = 64.0;

    private InspectorTargets() {
    }

    @Nullable
    public static AircraftEntity find(Entity player) {
        AircraftEntity ridden = RiddenVehicle.of(player);
        return ridden != null ? ridden : lookedAt(player, RANGE);
    }

    /** The vehicle a seat, a hit box or the vehicle itself belongs to; null for anything else. */
    @Nullable
    public static AircraftEntity vehicleOf(@Nullable Entity entity) {
        if (entity instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        if (entity instanceof VehicleSeat seat && seat.getParent() instanceof AircraftEntity parent) {
            return parent;
        }
        return entity instanceof VehicleHitBox box ? vehicleOf(box.owner()) : null;
    }

    /** The nearest vehicle whose box the player's line of sight crosses within {@code range}. */
    @Nullable
    public static AircraftEntity lookedAt(Entity player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));
        AABB area = new AABB(eye, end).inflate(1.0);
        AircraftEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : player.level().getEntities(player, area, candidate -> vehicleOf(candidate) != null)) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(0.3).clip(eye, end);
            double distance = hit.map(eye::distanceToSqr).orElse(Double.MAX_VALUE);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = vehicleOf(entity);
            }
        }
        return best;
    }
}
