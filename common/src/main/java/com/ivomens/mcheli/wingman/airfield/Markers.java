package com.ivomens.mcheli.wingman.airfield;

import com.ivomens.mcheli.core.wingman.airfield.Marker;
import com.ivomens.mcheli.wingman.block.WingmanMarkerTileEntity;
import net.minecraft.core.BlockPos;

/** Converts between the airfield's {@link Marker}s and the marker blocks of a level. */
public final class Markers {

    private Markers() {
    }

    /** The marker a block entity at this position describes. */
    public static Marker of(BlockPos pos, WingmanMarkerTileEntity block) {
        return new Marker(pos.getX(), pos.getY(), pos.getZ(), block.getMarkerType(), block.getMarkerId(),
                block.getBaseId(), block.getParkingHeading());
    }

    /** The block the marker stands on. */
    public static BlockPos pos(Marker marker) {
        return new BlockPos(marker.blockX(), marker.blockY(), marker.blockZ());
    }
}
