package com.ivomens.mcheli.wingman.block;

import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.wingman.McHeliWingman;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Marker settings, synced to the client so the configuration screen can show them. */
public class WingmanMarkerTileEntity extends BlockEntity {

    private MarkerType markerType = MarkerType.PARKING;
    /** User-chosen id the routes and missions refer to. */
    private String markerId = "";
    /** Id of the owning base; empty for BASE markers. */
    private String baseId = "";
    /** PARKING only: -1 any, 0 north, 1 east, 2 south, 3 west. */
    private int parkingHeading = -1;

    public WingmanMarkerTileEntity(BlockPos pos, BlockState state) {
        super(McHeliWingman.MARKER_TE.get(), pos, state);
    }

    public MarkerType getMarkerType() {
        return markerType;
    }

    public String getMarkerId() {
        return markerId;
    }

    public String getBaseId() {
        return baseId;
    }

    public int getParkingHeading() {
        return parkingHeading;
    }

    public void setMarkerType(MarkerType type) {
        markerType = type;
        setChanged();
        syncBlockState();
        sync();
    }

    public void setMarkerId(String id) {
        markerId = id != null ? id : "";
        setChanged();
        sync();
    }

    public void setBaseId(String id) {
        baseId = id != null ? id : "";
        setChanged();
        sync();
    }

    public void setParkingHeading(int heading) {
        parkingHeading = heading;
        setChanged();
        sync();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("markerType", markerType.name());
        tag.putString("markerId", markerId);
        tag.putString("baseId", baseId);
        tag.putInt("parkingHeading", parkingHeading);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            markerType = MarkerType.valueOf(tag.getString("markerType"));
        } catch (IllegalArgumentException e) {
            markerType = MarkerType.PARKING;
        }
        markerId = tag.getString("markerId");
        baseId = tag.getString("baseId");
        parkingHeading = tag.contains("parkingHeading") ? tag.getInt("parkingHeading") : -1;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    /** The marker type is also a block-state property (selects the model); keep it in step. */
    private void syncBlockState() {
        if (level == null || level.isClientSide) return;
        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(WingmanMarkerBlock.TYPE) && state.getValue(WingmanMarkerBlock.TYPE) != markerType) {
            level.setBlock(worldPosition, state.setValue(WingmanMarkerBlock.TYPE, markerType), 3);
        }
    }

    /** Sends the changed data to clients. */
    private void sync() {
        if (level != null && !level.isClientSide) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }
}
