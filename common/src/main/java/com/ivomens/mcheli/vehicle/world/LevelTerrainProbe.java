package com.ivomens.mcheli.vehicle.world;

import com.ivomens.mcheli.core.sim.port.TerrainProbe;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Answers terrain probes from the level around a vehicle, at wherever the vehicle is when asked.
 * <p>
 * Each probe scans blocks afresh, as the legacy code did. The models ask few enough questions per
 * step that a shared per-step sample would not pay for itself, and every answer must stay exactly
 * what the scan returns.
 */
public final class LevelTerrainProbe implements TerrainProbe {

    /** The hull is cut into this many horizontal slices; the depth is the share of wet slices. */
    private static final int WATER_SLICES = 5;
    /** Slices are taken this far below the hull, so a hull resting on the surface counts as floating. */
    private static final double WATER_SLICE_OFFSET = -0.125;

    private final AircraftEntity vehicle;

    public LevelTerrainProbe(AircraftEntity vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public boolean isClearBelow(int size, int depth) {
        return VehicleMotion.isClearBelow(this.vehicle, size, depth);
    }

    /** The vehicle's own water probe: ships read vanilla's fluid height, everything else scans slices. */
    @Override
    public double waterDepth() {
        return this.vehicle.getWaterDepth();
    }

    @Override
    public boolean isAboveWaterOrAir() {
        return VehicleMotion.isAboveWaterOrAir(this.vehicle);
    }

    /** Share of the entity's box slices that hold water, with the slices raised by {@code floatOffset}. */
    public static double slicedWaterDepth(Entity entity, double floatOffset) {
        AABB box = entity.getBoundingBox();
        double height = box.maxY - box.minY;
        double depth = 0.0;
        for (int i = 0; i < WATER_SLICES; i++) {
            double yMin = box.minY + height * i / WATER_SLICES + WATER_SLICE_OFFSET + floatOffset;
            double yMax = box.minY + height * (i + 1) / WATER_SLICES + WATER_SLICE_OFFSET + floatOffset;
            if (isWaterInBox(entity.level(), box.minX, yMin, box.minZ, box.maxX, yMax, box.maxZ)) {
                depth += 1.0 / WATER_SLICES;
            }
        }
        return depth;
    }

    /** True if any block overlapping the box holds water; unloaded chunks count as dry. */
    private static boolean isWaterInBox(Level level, double minX, double minY, double minZ, double maxX, double maxY,
                                        double maxZ) {
        int x0 = Mth.floor(minX);
        int x1 = Mth.ceil(maxX);
        int y0 = Mth.floor(minY);
        int y1 = Mth.ceil(maxY);
        int z0 = Mth.floor(minZ);
        int z1 = Mth.ceil(maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                pos.set(x, y0, z);
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                for (int y = y0; y < y1; y++) {
                    pos.setY(y);
                    if (level.getFluidState(pos).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
