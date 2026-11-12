package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.aircraft.SeatRackInfo;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.util.Terrain;
import com.ivomens.mcheli.aircraft.parachute.ParachuteEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Getting crew out of a vehicle: dismount positions (and holding a rider there for a few ticks so it
 * does not fall through the hull), parachute drops through the hatch, the crew-drop key (which hands
 * over to the {@link RepellingRopes} on a vehicle with rope hooks), and seating nearby mobs when a
 * pilot boards.
 */
@TickOrder(phase = TickPhase.FOLLOW_HULL, after = PartsComponent.class)
public final class CrewExitComponent implements VehicleComponent {

    /** The hatch must be (nearly) fully open for crew to jump. */
    private static final float HATCH_OPEN_ANGLE = 89.0F;

    private final AircraftEntity vehicle;
    private final SeatsComponent seats;
    private final BodyComponent body;
    private final PositionHistory history;
    private final LifecycleComponent lifecycle;
    private final PartsComponent parts;
    private final RepellingRopes ropes;
    /** Entities that just left the aircraft, held in place for a few ticks so they do not fall through it. */
    private final List<UnmountReserve> unmountReserves = new ArrayList<>();

    private boolean dropping;

    public CrewExitComponent(AircraftEntity vehicle, SeatsComponent seats, BodyComponent body,
            PositionHistory history, LifecycleComponent lifecycle, PartsComponent parts, RepellingRopes ropes) {
        this.vehicle = vehicle;
        this.seats = seats;
        this.body = body;
        this.history = history;
        this.lifecycle = lifecycle;
        this.parts = parts;
        this.ropes = ropes;
        // closing the hatch ends a parachute drop
        parts.whenHatchCloses(this::stopDrop);
    }

    /** Server: the next crew member jumps from the hatch. */
    @Override
    public void serverTick(TickContext ctx) {
        tickParachuteDrop();
    }

    /** Client: the parachute drop is predicted here too. */
    @Override
    public void clientTick(TickContext ctx) {
        tickParachuteDrop();
    }

    public void tickUnmountReserves() {
        Iterator<UnmountReserve> iterator = this.unmountReserves.iterator();
        while (iterator.hasNext()) {
            UnmountReserve reserve = iterator.next();
            Entity entity = reserve.entity;
            if (entity != null && !entity.isRemoved() && !entity.isPassenger()) {
                // players are placed by the vanilla dismount location; hold everything else in place
                if (!(entity instanceof Player)) {
                    entity.setPos(reserve.x, reserve.y, reserve.z);
                }
                entity.fallDistance = this.vehicle.fallDistance;
            }
            if (reserve.ticksLeft > 0) {
                reserve.ticksLeft--;
            }
            if (reserve.ticksLeft == 0) {
                iterator.remove();
            }
        }
    }

    /** Local unmount position for a seat (or rack): the seat's own, the aircraft's, or beside the seat. */
    private Vec3 getLocalUnmountPos(@Nullable SeatInfo seatInfo) {
        VehicleSpec info = this.vehicle.spec();
        if (info == null || seatInfo == null) {
            return Vec3.ZERO;
        }
        if (seatInfo.unmountPos != null) {
            return seatInfo.unmountPos;
        }
        if (info.file().unmountPosition() != null) {
            return info.file().unmountPosition();
        }
        double x = seatInfo.pos.x >= 0.0 ? seatInfo.pos.x + 3.0 : seatInfo.pos.x - 3.0;
        return new Vec3(x, 2.0, seatInfo.pos.z);
    }

    /**
     * Where a crew member leaves the aircraft: the seat's unmount position, pulled back in front of
     * any wall between the rider and that spot. Null without seat info.
     */
    @Nullable
    public Vec3 getUnmountPosition(@Nullable Entity rider, @Nullable SeatInfo seatInfo) {
        if (rider == null || seatInfo == null || this.vehicle.spec() == null) {
            return null;
        }
        Vec3 desired = this.body.toWorld(getLocalUnmountPos(seatInfo));
        Vec3 from = rider.position();
        BlockHitResult trace = this.vehicle.level().clip(
                new ClipContext(from, desired, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rider));
        if (trace.getType() == HitResult.Type.BLOCK) {
            return trace.getLocation().subtract(desired.subtract(from).normalize());
        }
        return desired;
    }

    public void setUnmountPosition(@Nullable Entity rider, @Nullable SeatInfo seatInfo) {
        Vec3 position = getUnmountPosition(rider, seatInfo);
        if (position == null) {
            return;
        }
        if (!(rider instanceof Player)) {
            rider.setPos(position.x, position.y, position.z);
        }
        this.unmountReserves.add(new UnmountReserve(rider, position.x, position.y, position.z, UnmountReserve.DEFAULT_TICKS));
    }

    /** Holds an entity that just left at a spot for a few ticks. */
    public void holdInPlace(Entity entity, int ticks) {
        this.unmountReserves.add(new UnmountReserve(entity, entity.getX(), entity.getY(), entity.getZ(), ticks));
    }

    public void startDrop() {
        this.dropping = true;
        if (this.parts.hasHatch()) {
            this.parts.foldHatch(true, true);
        }
    }

    public void stopDrop() {
        this.dropping = false;
    }

    /** The crew-drop key: toggles repelling or the parachute drop, or simply dismounts on the ground. */
    public void toggleCrewDrop() {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (info == null) {
            return;
        }
        if (info.crew().haveRepellingHook()) {
            if (this.ropes.isRepelling()) {
                this.ropes.stopRepelling();
            } else if (Terrain.hasBlockY(ac, 3, -4)) {
                unmountCrew(false);
            } else if (this.ropes.canStartRepelling()) {
                this.ropes.startRepelling();
            }
        } else if (this.dropping) {
            stopDrop();
        } else if (info.dropsCrew() && !Terrain.hasBlockY(ac, 3, -10)) {
            startDrop();
        } else {
            unmountCrew(false);
        }
    }

    /** While dropping, one crew member jumps every mobDropOption.interval ticks once the hatch is open. */
    private void tickParachuteDrop() {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (info == null || !this.dropping) {
            return;
        }
        if (Terrain.hasBlockY(ac, 3, -10)) {
            stopDrop();
        } else if ((!this.parts.hasHatch() || this.parts.hatchRotation() > HATCH_OPEN_ANGLE)
                && this.lifecycle.age() % info.crew().mobDrop().interval == 0 && !unmountCrew(true)) {
            stopDrop();
        }
    }

    /**
     * Dismounts every non-player crew member outside the racks. With {@code byParachute} only the
     * first one beyond the copilot seat jumps, with a parachute. Returns whether anyone was found.
     */
    public boolean unmountCrew(boolean byParachute) {
        AircraftEntity ac = this.vehicle;
        SeatEntity[] seats = this.seats.all();
        SeatInfo[] seatInfos = this.seats.infos();
        boolean found = false;

        for (int i = 0; i < seats.length; i++) {
            SeatEntity seat = seats[i];
            if (seat == null) {
                continue;
            }
            Entity occupant = seat.getRiddenByEntity();
            if (occupant == null || occupant instanceof Player) {
                continue;
            }
            SeatInfo seatInfo = seatInfos[i + 1];
            if (seatInfo instanceof SeatRackInfo) {
                continue;
            }
            found = true;

            if (byParachute && this.seats.seatIdOf(occupant) > 1) {
                Vec3 localPos = seatInfo != null && seatInfo.unmountPos != null
                        ? seatInfo.unmountPos : Objects.requireNonNull(ac.spec()).crew().mobDrop().pos;
                Vec3 dropPos = this.body.toWorld(localPos, this.history.halfSecondAgo());
                seat.setPos(dropPos.x, dropPos.y, dropPos.z);
                occupant.stopRiding();
                occupant.setPos(dropPos.x, dropPos.y, dropPos.z);
                dropWithParachute(occupant);
                break;
            }
            setUnmountPosition(seat, seatInfo);
            occupant.stopRiding();
            setUnmountPosition(occupant, seatInfo);
        }
        return found;
    }

    private boolean canJumpOut(Entity entity) {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        return info != null && info.dropsCrew() && this.seats.seatIdOf(entity) > 1
                && (!this.parts.hasHatch() || this.parts.hatchRotation() >= HATCH_OPEN_ANGLE);
    }

    public boolean canParachute(Entity entity) {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        return info != null && info.dropsCrew() && this.seats.seatIdOf(entity) > 1
                && !Terrain.hasBlockY(ac, 3, -13);
    }

    /** A passenger asks to leave: slide down the next rope while repelling, otherwise jump with a parachute. */
    public void unmount(Entity entity) {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (info == null) {
            return;
        }
        boolean repelling = this.ropes.canRepell() && info.crew().haveRepellingHook();
        if (!repelling && !canJumpOut(entity)) {
            return;
        }
        SeatEntity seat = this.seats.seatOf(entity);
        if (seat == null) {
            return;
        }
        if (repelling) {
            this.ropes.sendDownNextRope(seat, entity, info);
            return;
        }
        SeatInfo seatInfo = this.seats.info(seat.seatID() + 1);
        Vec3 localPos = seatInfo != null && seatInfo.unmountPos != null ? seatInfo.unmountPos
                : info.crew().mobDrop().pos;
        Vec3 dropPos = this.body.toWorld(localPos, this.history.halfSecondAgo());
        seat.setPos(dropPos.x, dropPos.y, dropPos.z);
        entity.stopRiding();
        entity.setPos(dropPos.x, dropPos.y, dropPos.z);
        dropWithParachute(entity);
    }

    public void dropWithParachute(Entity entity) {
        entity.setDeltaMovement(this.vehicle.getDeltaMovement());
        spawnParachute(entity, ParachuteEntity.TYPE_CANOPY);
    }

    void spawnParachute(Entity user, int type) {
        ParachuteEntity parachute = new ParachuteEntity(this.vehicle.level(), user.getX(), user.getY(), user.getZ());
        parachute.setYRot(user.getYRot());
        parachute.setDeltaMovement(user.getDeltaMovement());
        parachute.fallDistance = user.fallDistance;
        parachute.setUser(user);
        parachute.setType(type);
        this.vehicle.level().addFreshEntity(parachute);
    }

    /** Seats nearby mobs in the free crew seats (racks excluded). */
    public void mountNearbyMobs() {
        AircraftEntity ac = this.vehicle;
        List<LivingEntity> mobs = ac.level().getEntitiesOfClass(LivingEntity.class,
                ac.getBoundingBox().inflate(3.0, 2.0, 3.0));
        for (Entity mob : mobs) {
            if (mob instanceof Player || mob.getVehicle() != null) {
                continue;
            }
            int seatId = 1;
            for (SeatEntity seat : this.seats.all()) {
                if (seat != null && seat.getRiddenByEntity() == null && !this.seats.isAboard(mob)
                        && this.seats.canTake(seatId)) {
                    if (this.seats.info(seatId) instanceof SeatRackInfo) {
                        break;
                    }
                    mob.startRiding(seat);
                }
                seatId++;
            }
        }
    }

    private static final class UnmountReserve {

        static final int DEFAULT_TICKS = 5;

        final Entity entity;
        final double x;
        final double y;
        final double z;
        int ticksLeft;

        UnmountReserve(Entity entity, double x, double y, double z, int ticks) {
            this.entity = entity;
            this.x = x;
            this.y = y;
            this.z = z;
            this.ticksLeft = ticks;
        }
    }
}
