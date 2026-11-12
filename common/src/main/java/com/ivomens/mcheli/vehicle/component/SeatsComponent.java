package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.aircraft.SeatRackInfo;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.net.SeatListRequestMessage;
import com.ivomens.mcheli.entityview.AiGunner;
import com.ivomens.mcheli.api.vehicle.Crew;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.util.math.Rotations;
import java.util.ArrayList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * The crew seats. The pilot rides the vehicle itself (seat 0); every other seat, crew seat or cargo
 * rack, is a {@link SeatEntity} that the vehicle spawns, finds again after a reload by its unique id,
 * and places on the hull every tick.
 */
@TickOrder(phase = TickPhase.PHYSICS)
public final class SeatsComponent implements VehicleComponent, Crew {

    private static final SeatEntity[] NO_SEATS = new SeatEntity[0];
    /** Seats that are missing are looked for every two seconds. */
    private static final int SEAT_SEARCH_INTERVAL = 40;
    private static final double SEAT_SEARCH_RANGE = 60.0;

    private final AircraftEntity vehicle;
    private final StatusWord status;
    private final IdentityComponent identity;
    private final BodyComponent body;
    private final CrewAimComponent aim;
    private SeatEntity[] seats = NO_SEATS;
    @Nullable
    private SeatInfo[] seatInfos;
    private int seatSearchCount;

    public SeatsComponent(AircraftEntity vehicle, StatusWord status, IdentityComponent identity, BodyComponent body,
            CrewAimComponent aim) {
        this.vehicle = vehicle;
        this.status = status;
        this.identity = identity;
        this.body = body;
        this.aim = aim;
    }

    /** Whether AI gunners may take seats; the crew switches it from the HUD. */
    public boolean gunnersAllowed() {
        return this.status.get(StatusWord.GUNNER);
    }

    /** Server: allows or forbids AI gunners. */
    public void setGunnersAllowed(boolean allowed) {
        this.status.set(StatusWord.GUNNER, allowed);
    }

    public SeatEntity[] all() {
        return this.seats;
    }

    /** Discards the old seat entities and makes room for {@code seatCount - 1} new ones (the pilot has none). */
    public void reset(int seatCount) {
        if (seatCount < 2) {
            return;
        }
        for (SeatEntity seat : this.seats) {
            if (seat != null) {
                seat.discard();
            }
        }
        this.seats = new SeatEntity[seatCount - 1];
    }

    @Nullable
    /** The vehicle is gone for good: so are its seats. */
    public void discardAll() {
        for (SeatEntity seat : this.seats) {
            if (seat != null) {
                seat.discard();
            }
        }
    }

    public SeatEntity seat(int index) {
        return index < this.seats.length ? this.seats[index] : null;
    }

    public void setSeat(int index, SeatEntity seat) {
        if (index < this.seats.length) {
            this.seats[index] = seat;
        }
    }

    /** Number of seat entities (crew seats and racks, the pilot not counted). */
    @Override
    public int count() {
        VehicleSpec info = this.vehicle.spec();
        if (info == null) {
            return 0;
        }
        int seatsAndRacks = info.crew().seatsAndRacks();
        return seatsAndRacks >= 1 ? seatsAndRacks - 1 : 1;
    }

    @Override
    public boolean isValidSeatId(int seatId) {
        return seatId >= 0 && seatId < count() + 1;
    }

    /** Seat layout from the definition (index 0 is the pilot); null while the definition is unknown. */
    @Nullable
    public SeatInfo[] infos() {
        if (this.seatInfos == null) {
            VehicleSpec info = this.vehicle.spec();
            if (info != null) {
                this.seatInfos = info.crew().seats().subList(0, info.crew().seatsAndRacks()).toArray(new SeatInfo[0]);
            }
        }
        return this.seatInfos;
    }

    @Nullable
    public SeatInfo info(int seatId) {
        SeatInfo[] infos = infos();
        return seatId >= 0 && infos != null && seatId < infos.length ? infos[seatId] : null;
    }

    /** 0 for the pilot, 1.. for the seats, -1 if the entity is not aboard. */
    /** The seat entity the rider sits on; null for the pilot and for anyone not aboard. */
    @Nullable
    public SeatEntity seatOf(@Nullable Entity entity) {
        int seatId = seatIdOf(entity);
        return seatId > 0 ? seat(seatId - 1) : null;
    }

    @Nullable
    public SeatInfo info(@Nullable Entity rider) {
        return info(seatIdOf(rider));
    }

    @Override
    public int seatIdOf(@Nullable Entity entity) {
        if (entity == null) {
            return -1;
        }
        if (EntityIds.same(this.vehicle.getRiddenByEntity(), entity)) {
            return 0;
        }
        for (int i = 0; i < this.seats.length; i++) {
            SeatEntity seat = this.seats[i];
            if (seat != null && EntityIds.same(seat.getRiddenByEntity(), entity)) {
                return i + 1;
            }
        }
        return -1;
    }

    @Nullable
    @Override
    public Entity riderOf(int seatId) {
        if (seatId == 0) {
            return this.vehicle.getRiddenByEntity();
        }
        int index = seatId - 1;
        if (index < 0 || index >= this.seats.length || this.seats[index] == null) {
            return null;
        }
        return this.seats[index].getRiddenByEntity();
    }

    @Override
    public List<Entity> passengers() {
        List<Entity> passengers = new ArrayList<>();
        if (this.seats != null) {
            for (SeatEntity seat : this.seats) {
                if (seat != null && seat.getRiddenByEntity() != null) {
                    passengers.add(seat.getRiddenByEntity());
                }
            }
        }
        return passengers;
    }

    /** Server: spawns the seat entities, linked to the vehicle by its unique id. */
    public void spawnSeats(String uniqueId) {
        this.seats = new SeatEntity[count()];
        for (int i = 0; i < this.seats.length; i++) {
            AircraftEntity ac = this.vehicle;
            SeatEntity seat = new SeatEntity(ac.level(), ac.getX(), ac.getY(), ac.getZ());
            seat.setParentUniqueID(uniqueId);
            seat.setSeatID(i);
            seat.setParent(ac);
            this.seats[i] = seat;
            ac.level().addFreshEntity(seat);
        }
    }

    /** Keeps seat entities safe from fall damage; true when a seat is missing. */
    private boolean protectSeats() {
        boolean missingSeat = false;
        for (SeatEntity seat : this.seats) {
            if (seat == null) {
                missingSeat = true;
            } else if (!seat.isRemoved()) {
                seat.fallDistance = 0.0F;
            }
        }
        return missingSeat;
    }

    /** Server: every two seconds, links seats that are missing (after a reload the links are lost). */
    @Override
    public void serverTick(TickContext ctx) {
        if (protectSeats()) {
            if (this.seatSearchCount > SEAT_SEARCH_INTERVAL) {
                searchSeats();
                this.seatSearchCount = 0;
            }
            this.seatSearchCount++;
        }
    }

    /** Client: every two seconds, asks the server for the seats that are missing. */
    @Override
    public void clientTick(TickContext ctx) {
        if (protectSeats()) {
            if (this.seatSearchCount > SEAT_SEARCH_INTERVAL) {
                Network.sendToServer(new SeatListRequestMessage(this.vehicle.getId()));
                this.seatSearchCount = 0;
            }
            this.seatSearchCount++;
        }
    }

    /** Re-links seat entities of this vehicle found nearby. */
    public void searchSeats() {
        AircraftEntity ac = this.vehicle;
        List<SeatEntity> nearby = ac.level().getEntitiesOfClass(SeatEntity.class,
                ac.getBoundingBox().inflate(SEAT_SEARCH_RANGE, SEAT_SEARCH_RANGE, SEAT_SEARCH_RANGE));
        for (SeatEntity seat : nearby) {
            if (!seat.isRemoved() && seat.parentUniqueID().equals(this.identity.uniqueId()) && seat.seatID() >= 0
                    && seat.seatID() < count() && this.seats[seat.seatID()] == null) {
                this.seats[seat.seatID()] = seat;
                seat.setParent(ac);
            }
        }
    }

    /** Moves every seat entity to its place on the vehicle placed at the given origin. */
    public void place(double px, double py, double pz, boolean setPrevPos) {
        AircraftEntity ac = this.vehicle;
        SeatInfo[] infos = infos();
        py += McheliEntity.GLOBAL_SEAT_OFFSET;
        for (int i = 1; i <= this.seats.length; i++) {
            SeatEntity seat = this.seats[i - 1];
            if (seat == null || seat.isRemoved()) {
                continue;
            }
            seat.xo = seat.getX();
            seat.yo = seat.getY();
            seat.zo = seat.getZ();
            SeatInfo seatInfo = i < infos.length ? infos[i] : infos[0];
            Vec3 pos = seatPosition(seatInfo.pos.x, seatInfo.pos.y - 0.5, seatInfo.pos.z, px, py, pz,
                    seatInfo.rotSeat);
            seat.setPos(pos.x, pos.y, pos.z);
            seat.setXRot(ac.getXRot());
            seat.setYRot(ac.getYRot());
            if (setPrevPos) {
                seat.xo = seat.getX();
                seat.yo = seat.getY();
                seat.zo = seat.getZ();
            }
            if (seatInfo instanceof SeatRackInfo) {
                seat.updateRotation(seat.getRiddenByEntity(), seatInfo.fixYaw + ac.getYRot(), seatInfo.fixPitch);
            }
            seat.updatePosition(seat.getRiddenByEntity());
        }
    }

    /** A seat's world position for the vehicle placed at (px, py, pz); turret seats turn with their rider. */
    private Vec3 seatPosition(double x, double y, double z, double px, double py, double pz, boolean turret) {
        if (turret && this.vehicle.spec() != null) {
            return turretOffset(new Vec3(x, y, z)).add(px, py, pz);
        }
        return this.body.toWorld(x, y, z, px, py, pz);
    }

    /**
     * A point on a turret seat in world offsets: turned around the turret by where the rider looks,
     * then by the airframe attitude.
     */
    public Vec3 turretOffset(Vec3 localPos) {
        AircraftEntity ac = this.vehicle;
        Vec3 turretPos = Objects.requireNonNull(ac.spec()).file().turretPosition();
        Vec3 rotatedRelative = Rotations.rotate(localPos.subtract(turretPos), ac.getYRot() - turretYaw(), 0.0F, 0.0F);
        return Rotations.rotate(rotatedRelative.add(turretPos), -ac.getYRot(), -ac.getXRot(), -ac.roll());
    }

    private float turretYaw() {
        AircraftEntity ac = this.vehicle;
        if (this.aim.isDetachedActive()) {
            return this.aim.detachedYaw();
        }
        if (ac.getRiddenByEntity() != null) {
            return ac.getRiddenByEntity().getYRot();
        }
        return this.aim.riderYaw();
    }

    /** Server: spawns the seat entities, linked to the vehicle by its unique id. */
    public void create(String uniqueId) {
        if (uniqueId.isEmpty()) {
            return;
        }
        this.identity.setUniqueId(uniqueId);
        spawnSeats(uniqueId);
    }

    public boolean areCreated() {
        return !this.identity.uniqueId().isEmpty();
    }

    /** Server: AI crew with "regeneration" vehicles get a regeneration effect while someone flies. */
    public void regenerateCrew() {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (ac.isDestroyed() || info == null || !info.file().regeneration() || ac.getRiddenByEntity() == null) {
            return;
        }
        for (SeatEntity seat : this.seats) {
            if (seat == null || seat.isRemoved()) {
                continue;
            }
            Entity rider = seat.getRiddenByEntity();
            if (rider instanceof LivingEntity living && !rider.isRemoved()) {
                MobEffectInstance effect = living.getEffect(MobEffects.REGENERATION);
                if (effect == null || effect.getDuration() < 500) {
                    living.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 250, 0, true, true));
                }
            }
        }
    }

    /** World position of the pilot for the given vehicle origin. */
    public Vec3 pilotPosition(double px, double py, double pz) {
        SeatInfo[] infos = infos();
        if (infos != null && infos.length > 0 && infos[0] != null) {
            SeatInfo pilotSeat = infos[0];
            return seatPosition(pilotSeat.pos.x, pilotSeat.pos.y - 0.5, pilotSeat.pos.z,
                    px, py + McheliEntity.GLOBAL_Y_OFFSET, pz, pilotSeat.rotSeat);
        }
        return this.body.toWorld(0.0, -1.0, 0.0, px, py, pz);
    }

    @Override
    public boolean isAboard(@Nullable Entity entity) {
        return entity != null && isAboard(entity.getId());
    }

    public boolean isAboard(int entityId) {
        if (EntityIds.of(this.vehicle.getRiddenByEntity()) == entityId) {
            return true;
        }
        for (SeatEntity seat : this.seats) {
            if (seat != null && seat.getRiddenByEntity() != null
                    && EntityIds.of(seat.getRiddenByEntity()) == entityId) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPlayerAboard() {
        return firstPlayer() != null;
    }

    @Nullable
    public Player firstPlayer() {
        if (this.vehicle.getRiddenByEntity() instanceof Player pilot) {
            return pilot;
        }
        for (SeatEntity seat : this.seats) {
            if (seat != null && seat.getRiddenByEntity() instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    public boolean hasAllyOf(@Nullable LivingEntity player) {
        if (player == null || player.getTeam() == null) {
            return false;
        }
        Entity pilot = this.vehicle.getRiddenByEntity();
        if (pilot instanceof LivingEntity && player.isAlliedTo(pilot)) {
            return true;
        }
        for (SeatEntity seat : this.seats) {
            if (seat != null && seat.getRiddenByEntity() instanceof LivingEntity && player.isAlliedTo(seat.getRiddenByEntity())) {
                return true;
            }
        }
        return false;
    }

    /** True if anyone aboard is in a team the player is not allied with. */
    public boolean hasEnemyOf(Player player) {
        for (int seatId = 0; seatId < 1 + count(); seatId++) {
            Entity rider = riderOf(seatId);
            if ((rider instanceof Player || rider instanceof AiGunner) && rider.getTeam() != null && !rider.isAlliedTo(player)) {
                return true;
            }
        }
        return false;
    }

    /** False when the seat belongs to an exclusion group in which another seat is already taken. */
    public boolean canTake(int seatId) {
        VehicleSpec info = this.vehicle.spec();
        if (info == null) {
            return false;
        }
        for (Integer[] group : info.crew().exclusions()) {
            if (!containsSeat(group, seatId)) {
                continue;
            }
            for (int otherSeat : group) {
                if (riderOf(otherSeat) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean containsSeat(Integer[] group, int seatId) {
        for (Integer id : group) {
            if (id != null && id == seatId) {
                return true;
            }
        }
        return false;
    }

    public void switchNext(@Nullable Entity entity) {
        if (entity == null || this.seats.length == 0 || !isAboard(entity)) {
            return;
        }
        boolean found = false;
        for (int i = 0; i < this.seats.length; i++) {
            SeatEntity seat = this.seats[i];
            if (seat == null) {
                continue;
            }
            if (info(i + 1) instanceof SeatRackInfo) {
                break;
            }
            if (EntityIds.same(seat.getRiddenByEntity(), entity)) {
                found = true;
            } else if (found && seat.getRiddenByEntity() == null) {
                entity.startRiding(seat);
                return;
            }
        }
        // wrapped around: the first free crew seat
        for (int i = 0; i < this.seats.length; i++) {
            SeatEntity seat = this.seats[i];
            if (seat != null && seat.getRiddenByEntity() == null) {
                if (!(info(i + 1) instanceof SeatRackInfo)) {
                    entity.startRiding(seat);
                }
                return;
            }
        }
    }

    public void switchPrevious(@Nullable Entity entity) {
        if (entity == null || this.seats.length == 0 || !isAboard(entity)) {
            return;
        }
        boolean found = false;
        for (int i = this.seats.length - 1; i >= 0; i--) {
            SeatEntity seat = this.seats[i];
            if (seat == null) {
                continue;
            }
            if (EntityIds.same(seat.getRiddenByEntity(), entity)) {
                found = true;
            } else if (found && seat.getRiddenByEntity() == null) {
                entity.startRiding(seat);
                return;
            }
        }
        // wrapped around: the last free crew seat
        for (int i = this.seats.length - 1; i >= 0; i--) {
            SeatEntity seat = this.seats[i];
            if (!(info(i + 1) instanceof SeatRackInfo) && seat != null && seat.getRiddenByEntity() == null) {
                entity.startRiding(seat);
                return;
            }
        }
    }
}
