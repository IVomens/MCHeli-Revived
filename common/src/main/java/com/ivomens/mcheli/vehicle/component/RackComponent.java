package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehiclePeers;
import com.ivomens.mcheli.aircraft.IRideableAboard;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatRackInfo;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.util.Terrain;
import com.ivomens.mcheli.vehicle.settings.CarriageRules;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Cargo racks: loading entities (and other vehicles) onto this vehicle's racks and dropping them
 * again, and this vehicle riding a rack of a carrier nearby.
 */
public final class RackComponent implements VehicleComponent {

    /** How far around the vehicle other vehicles are searched for a free rack. */
    private static final double RACK_SEARCH_RANGE = 60.0;
    private static final int RIDE_RACK_CHECK_INTERVAL = 10;

    private final AircraftEntity vehicle;
    private final CrewExitComponent crewExit;
    private final SeatsComponent seats;
    private final BodyComponent body;
    private final EngineComponent engine;
    private final LifecycleComponent lifecycle;
    private final Supplier<CarriageRules> carriage;
    private boolean canRideRack;

    public RackComponent(AircraftEntity vehicle, CrewExitComponent crewExit, SeatsComponent seats, BodyComponent body,
            EngineComponent engine, LifecycleComponent lifecycle, Supplier<CarriageRules> carriage) {
        this.vehicle = vehicle;
        this.crewExit = crewExit;
        this.seats = seats;
        this.body = body;
        this.engine = engine;
        this.lifecycle = lifecycle;
        this.carriage = carriage;
    }

    /** Loads entities near each free rack's entry point onto that rack. */
    public void loadRacks() {
        AircraftEntity ac = this.vehicle;
        if (!this.carriage.get().racksInFlight()) {
            if (this.engine.throttle() > 0.3) {
                return;
            }
            Block below = Terrain.firstBlockY(ac, 1, -3, true);
            if (below == null || below == Blocks.AIR) {
                return;
            }
        }

        int loaded = 0;
        for (int i = 0; i < this.seats.count(); i++) {
            SeatEntity seat = this.seats.seat(i);
            if (this.seats.info(1 + i) instanceof SeatRackInfo rack && seat != null && seat.getRiddenByEntity() == null
                    && loadRack(seat, i, rack)) {
                loaded++;
            }
        }
        if (loaded > 0) {
            ModSounds.playSound(ac.level(), ac.getX(), ac.getY(), ac.getZ(), "random.click", 1.0F, 1.0F);
        }
    }

    private boolean loadRack(SeatEntity seat, int index, SeatRackInfo rack) {
        AircraftEntity ac = this.vehicle;
        Vec3 entry = this.body.toWorld(rack.getEntryPos());
        float range = rack.range;
        List<Entity> candidates = ac.level().getEntities(ac, new AABB(entry, entry).inflate(range, range, range));
        for (Entity entity : candidates) {
            if (!this.seats.canTake(1 + index)) {
                continue;
            }
            boolean canLoad;
            if (entity instanceof IRideableAboard rideable) {
                canLoad = rideable.canRideAircraft(ac, index, rack);
            } else {
                CompoundTag data = Services.ENTITY_DATA.getPersistentData(entity);
                canLoad = entity.getVehicle() == null && data.getBoolean("CanMountEntity");
            }
            if (canLoad) {
                entity.startRiding(seat);
                return true;
            }
        }
        return false;
    }

    /** Unloads the last occupied rack; cargo dropped in the air gets a parachute. */
    public void unloadRack() {
        AircraftEntity ac = this.vehicle;
        for (int i = this.seats.count() - 1; i >= 0; i--) {
            SeatEntity seat = this.seats.seat(i);
            if (!(this.seats.info(i + 1) instanceof SeatRackInfo rack) || seat == null || seat.getRiddenByEntity() == null) {
                continue;
            }
            Entity cargo = seat.getRiddenByEntity();
            Vec3 localPos = rack.unmountPos;
            if (localPos == null) {
                localPos = rack.getEntryPos();
                if (cargo instanceof AircraftEntity) {
                    // push aircraft clear of the carrier, out the end the entry point is closest to
                    double push = localPos.z >= Objects.requireNonNull(ac.spec()).hitBoxes().z() ? 12.0 : -12.0;
                    localPos = localPos.add(0.0, 0.0, push);
                }
            }

            Vec3 pos = this.body.toWorld(localPos);
            seat.setPos(pos.x, pos.y, pos.z);
            cargo.setPos(pos.x, pos.y, pos.z);
            this.crewExit.holdInPlace(cargo, 8);
            cargo.stopRiding();
            if (!Terrain.hasBlockY(ac, 3, -20)) {
                this.crewExit.dropWithParachute(cargo);
            }
            return;
        }
    }

    public boolean canLoadRack() {
        return hasRack(false);
    }

    public boolean canUnloadRack() {
        return hasRack(true);
    }

    private boolean hasRack(boolean occupied) {
        AircraftEntity ac = this.vehicle;
        for (int i = 0; i < this.seats.count(); i++) {
            SeatEntity seat = this.seats.seat(i);
            if (seat != null && (seat.getRiddenByEntity() != null) == occupied && this.seats.info(i + 1) instanceof SeatRackInfo) {
                return true;
            }
        }
        return false;
    }

    /** Rides the first free rack of a nearby aircraft whose entry point we are close to. */
    public void rideNearbyRack() {
        if (this.vehicle.getVehicle() != null) {
            return;
        }
        RackSlot slot = findRideableRack();
        if (slot != null) {
            AircraftEntity ac = this.vehicle;
            ModSounds.playSound(ac.level(), ac.getX(), ac.getY(), ac.getZ(), "random.click", 1.0F, 1.0F);
            ac.startRiding(slot.seat);
        }
    }

    public boolean canRideRack() {
        return this.vehicle.getVehicle() == null && this.canRideRack;
    }

    /** Refreshes the "rack in reach" HUD hint. Only the pilot sees it, so unmanned aircraft skip the search. */
    public void tickRideRackStatus() {
        AircraftEntity ac = this.vehicle;
        if (this.lifecycle.age() % RIDE_RACK_CHECK_INTERVAL != 0) {
            return;
        }
        this.canRideRack = ac.getVehicle() == null && ac.getRiddenByEntity() != null && findRideableRack() != null;
    }

    @Nullable
    private RackSlot findRideableRack() {
        AircraftEntity self = this.vehicle;
        AABB area = this.body.hullBox().inflate(RACK_SEARCH_RANGE);
        // a vehicle fits few racks, if any; the search would find nothing to ride where none of them is near
        if (!AircraftIndex.of(self.level()).mayFindAircraft(area, 0.0, this::mayFitRackOf)) {
            return null;
        }
        List<AircraftEntity> carriers = self.level().getEntitiesOfClass(AircraftEntity.class, area, e -> e != self);
        for (AircraftEntity carrier : carriers) {
            if (carrier.spec() == null) {
                continue;
            }
            SeatsComponent carrierSeats = VehiclePeers.seats(carrier);
            for (int i = 0; i < carrierSeats.count(); i++) {
                if (!(carrierSeats.info(1 + i) instanceof SeatRackInfo rack) || !carrierSeats.canTake(1 + i)) {
                    continue;
                }
                SeatEntity seat = carrierSeats.seat(i);
                if (seat == null || seat.getRiddenByEntity() != null) {
                    continue;
                }
                Vec3 entry = VehiclePeers.body(carrier).toWorld(rack.getEntryPos());
                float r = rack.range;
                if (Math.abs(self.getX() - entry.x) <= r && Math.abs(self.getY() - entry.y) <= r
                        && Math.abs(self.getZ() - entry.z) <= r && canRideAircraft(carrier, i, rack)) {
                    return new RackSlot(seat);
                }
            }
        }
        return null;
    }

    /**
     * Whether some rack of the carrier names this aircraft, or this aircraft names the carrier for one of
     * its racks: what {@link #canRideAircraft} asks first, so a carrier that fails it has no rack to ride.
     */
    private boolean mayFitRackOf(AircraftEntity carrier) {
        VehicleSpec selfInfo = this.vehicle.spec();
        VehicleSpec carrierInfo = carrier.spec();
        if (carrier == this.vehicle || selfInfo == null || carrierInfo == null) {
            return false;
        }
        SeatsComponent carrierSeats = VehiclePeers.seats(carrier);
        for (int i = 0; i < carrierSeats.count(); i++) {
            if (carrierSeats.info(1 + i) instanceof SeatRackInfo rack && namesThis(rack, selfInfo)) {
                return true;
            }
        }
        for (VehicleParts.RideRack rideRack : selfInfo.crew().rideRacks()) {
            if (rideRack.name().equalsIgnoreCase(carrierInfo.name())) {
                return true;
            }
        }
        return false;
    }

    private static boolean namesThis(SeatRackInfo rack, VehicleSpec info) {
        for (String name : rack.names) {
            if (name.equalsIgnoreCase(info.name()) || name.equalsIgnoreCase(info.kindName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this aircraft fits the carrier's rack: the rack lists our name or kind, or our own
     * info names that carrier and rack. An aircraft carrying another aircraft cannot be racked.
     */
    public boolean canRideAircraft(AircraftEntity carrier, int seatIndex, SeatRackInfo rack) {
        AircraftEntity self = this.vehicle;
        VehicleSpec selfInfo = self.spec();
        if (selfInfo == null || carrier.getVehicle() != null || self.getVehicle() != null) {
            return false;
        }

        for (String name : rack.names) {
            if (name.equalsIgnoreCase(selfInfo.name()) || name.equalsIgnoreCase(selfInfo.kindName())) {
                return !carriesRideable();
            }
        }

        VehicleSpec carrierInfo = carrier.spec();
        for (VehicleParts.RideRack rideRack : selfInfo.crew().rideRacks()) {
            int index = Objects.requireNonNull(carrierInfo).crew().riderSeats() - 1 + rideRack.rackID() - 1;
            if (index != seatIndex || !rideRack.name().equalsIgnoreCase(carrierInfo.name())) {
                continue;
            }
            SeatEntity seat = VehiclePeers.seats(carrier).seat(index);
            if (seat != null && seat.getRiddenByEntity() == null) {
                return !carriesRideable();
            }
        }
        return false;
    }

    private boolean carriesRideable() {
        for (SeatEntity seat : this.seats.all()) {
            if (seat != null && seat.getRiddenByEntity() instanceof IRideableAboard) {
                return true;
            }
        }
        return false;
    }

    private record RackSlot(SeatEntity seat) {
    }
}
