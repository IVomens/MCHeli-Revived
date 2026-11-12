package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.airfield.AirfieldData;
import com.ivomens.mcheli.wingman.airfield.Markers;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.wingman.block.WingmanMarkerTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Applies the marker screen's changes. The screen opens on a right click, so changes from further
 * away than {@link #MAX_EDIT_DISTANCE}, or to unloaded or non-marker blocks, are ignored.
 */
public final class UpdateMarkerHandler implements ServerMessageHandler<UpdateMarkerMessage> {

    private static final double MAX_EDIT_DISTANCE = 64.0;

    @Override
    public void handle(UpdateMarkerMessage message, ServerPlayer sender) {
        ServerLevel level = sender.serverLevel();
        BlockPos pos = message.pos();
        if (!level.hasChunkAt(pos) || sender.distanceToSqr(pos.getCenter()) > MAX_EDIT_DISTANCE * MAX_EDIT_DISTANCE) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof WingmanMarkerTileEntity marker)) {
            return;
        }
        MarkerSettings settings = message.settings();
        MarkerType type = settings.type();
        marker.setMarkerType(type);
        marker.setMarkerId(settings.id());
        marker.setBaseId(type == MarkerType.BASE ? "" : settings.baseId());
        marker.setParkingHeading(type == MarkerType.PARKING ? settings.parkingHeading() : -1);
        AirfieldData.of(level).putMarker(Markers.of(pos, marker));
    }
}
