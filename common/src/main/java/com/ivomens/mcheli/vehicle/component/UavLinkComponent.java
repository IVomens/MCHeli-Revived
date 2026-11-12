package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.ivomens.mcheli.vehicle.uav.UAVTracker;
import com.ivomens.mcheli.vehicle.uav.UavControl;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.vehicle.settings.UavRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A UAV's link to the station that flies it: which station controls it now (synced by entity id),
 * which station it is paired with (saved), the control range and the signal strength.
 */
@TickOrder(phase = TickPhase.SYSTEMS)
public final class UavLinkComponent implements VehicleComponent {

    /** The server records where a UAV is this often, so an unloaded UAV can be found again. */
    private static final int POSITION_RECORD_INTERVAL = 100;

    private final AircraftEntity vehicle;
    private final SyncedValue<Integer> stationId;
    private final FlightModesComponent modes;
    private final EngineComponent engine;
    private final Supplier<UavRules> rules;
    @Nullable
    private IUavStation station;
    /** Entity UUID of the station this UAV is paired to (one station per UAV), or null if unpaired. */
    @Nullable
    private UUID pairedStation;

    public UavLinkComponent(AircraftEntity vehicle, SyncedValue<Integer> stationId, FlightModesComponent modes,
            EngineComponent engine, Supplier<UavRules> rules) {
        this.vehicle = vehicle;
        this.stationId = stationId;
        this.modes = modes;
        this.engine = engine;
        this.rules = rules;
    }

    /** Small UAVs can be flown from a tablet without a station block. */
    public boolean isSmall() {
        VehicleSpec info = this.vehicle.spec();
        return info != null && info.file().uav().small();
    }

    /** The station flying this UAV; null for anything that is not a UAV. */
    @Nullable
    public IUavStation station() {
        return this.vehicle.isUAV() ? this.station : null;
    }

    /** Links the station; the server also tells clients which entity it is. */
    public void link(@Nullable IUavStation station) {
        this.station = station;
        this.stationId.set(station instanceof Entity stationEntity ? EntityIds.of(stationEntity) : 0);
    }

    @Nullable
    public UUID pairedStation() {
        return this.pairedStation;
    }

    public void setPairedStation(@Nullable UUID stationId) {
        this.pairedStation = stationId;
    }

    @Override
    public void describe(StateSink state) {
        state.put("uav", this.vehicle.isUAV());
        state.put("station", this.station instanceof Entity entity ? entity.getName().getString() : "-");
        state.put("synced station id", this.stationId.get());
        state.put("paired station", this.pairedStation == null ? "-" : this.pairedStation.toString());
    }

    public int controlRange() {
        VehicleSpec info = this.vehicle.spec();
        return info != null && info.file().uav().range() >= 0 ? info.file().uav().range()
                : this.rules.get().controllerRange();
    }

    private boolean isUnlimited(int range) {
        return UavRules.isUnlimited(range);
    }

    /** 1 next to the station, falling to 0 at the edge of the control range. */
    public double signalStrength() {
        IUavStation station = station();
        if (!this.vehicle.isUAV() || station == null) {
            return 0.0;
        }
        int range = controlRange();
        if (isUnlimited(range)) {
            return 1.0;
        }
        return Mth.clamp(1.0 - horizontalDistanceTo(station) / range, 0.0, 1.0);
    }

    private double horizontalDistanceTo(IUavStation station) {
        Vec3 stationPos = station.getPos();
        double dx = this.vehicle.getX() - stationPos.x;
        double dz = this.vehicle.getZ() - stationPos.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Server: records the position now and then and drops the link out of range. The vehicle's own
     * UAV range wins over the global controller range; a negative or "unlimited" range never cuts it.
     */
    @Override
    public void serverTick(TickContext ctx) {
        if (!this.vehicle.isUAV()) {
            return;
        }
        if (this.vehicle.tickCount % POSITION_RECORD_INTERVAL == 0) {
            UAVTracker.saveUAVPos(this.vehicle.level(), this.vehicle);
        }
        if (this.station != null) {
            int range = controlRange();
            Vec3 stationPos = this.station.getPos();
            double dx = this.vehicle.getX() - stationPos.x;
            double dz = this.vehicle.getZ() - stationPos.z;
            if (!isUnlimited(range) && dx * dx + dz * dz > (double) range * range) {
                loseSignal();
            }
        }
        forgetRemovedStation();
    }

    /**
     * Signal lost: the UAV falls (or coasts to a stop) instead of exploding. The operator is released
     * as well: once the link is gone their client stops tracking this far-off UAV, and a station
     * still pointing at it would leave them stuck in a frozen camera.
     */
    private void loseSignal() {
        IUavStation lost = this.station;
        this.modes.stopHoveringForLostLink();
        this.engine.setThrottle(0.0);
        UavControl.disconnect(lost);
        link(null);
    }

    /** Client: links to the station entity named in the synced data. */
    @Override
    public void clientTick(TickContext ctx) {
        if (!this.vehicle.isUAV()) {
            return;
        }
        int id = this.stationId.get();
        if (id > 0) {
            if (this.station == null && this.vehicle.level().getEntity(id) instanceof IUavStation found) {
                this.station = found;
                this.station.setControlled(this.vehicle);
            }
        } else if (this.station != null) {
            this.station.setControlled(null);
            this.station = null;
        }
        forgetRemovedStation();
    }

    private void forgetRemovedStation() {
        if (this.station instanceof Entity stationEntity && stationEntity.isRemoved()) {
            this.station = null;
        }
    }

    @Override
    public void save(CompoundTag tag) {
        if (this.pairedStation != null) {
            tag.putUUID(VehicleNbtCodec.PAIRED_STATION, this.pairedStation);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        this.pairedStation = tag.hasUUID(VehicleNbtCodec.PAIRED_STATION) ? tag.getUUID(VehicleNbtCodec.PAIRED_STATION) : null;
    }
}
