package com.ivomens.mcheli.ship;

import com.ivomens.mcheli.aircraft.AircraftEntityTypes;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.plane.AbstractPlaneEntity;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** Ships share the plane flight model; they differ in how deep they sit in the water. */
public class ShipEntity extends AbstractPlaneEntity {

    public ShipEntity(EntityType<? extends ShipEntity> type, Level level) {
        super(type, level);
    }

    public ShipEntity(Level level) {
        this(AircraftEntityTypes.SHIP.get(), level);
    }

    @Override
    public VehicleKind kind() {
        return VehicleKind.SHIP;
    }

    @Nullable
    @Override
    protected VehicleSpec findSpec(String typeName) {
        return VehicleTypes.get(VehicleKind.SHIP, typeName).orElse(null);
    }

    /**
     * Submerged fraction of the hull, in fifths like the generic aircraft check, but taken from the
     * water height vanilla already computes every tick instead of scanning block slices.
     */
    @Override
    public double getWaterDepth() {
        if (spec() == null) {
            return 0.0;
        }

        double floatOffset = spec().file().physics().floatOffset();
        double fluidHeight = isInWater() ? getFluidHeight(FluidTags.WATER) : 0.0;
        if (fluidHeight <= 0.0) {
            // water below the hull only matters when the float line sits under the hull
            return floatOffset < 0.0 ? super.getWaterDepth() : 0.0;
        }

        double height = getBoundingBox().maxY - getBoundingBox().minY;
        if (height <= 0.0) {
            return 1.0;
        }

        double submerged = fluidHeight - floatOffset + 0.125;
        double depth = Math.ceil(submerged / height * 5.0) / 5.0;
        return Mth.clamp(depth, 0.0, 1.0);
    }
}
