package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.HideEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatRackInfo;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.entityview.AiGunner;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Crew sliding down ropes from a hovering helicopter: lowering the ropes from their hooks, sending
 * the crew down one by one, and a passenger asking to go down the next rope. Switching hover on or
 * off pulls the ropes in.
 */
@TickOrder(phase = TickPhase.FOLLOW_HULL)
public final class RepellingRopes implements VehicleComponent {

    /** Ticks of hovering on the ropes before crew start sliding down. */
    private static final int REPELLING_WARMUP_TICKS = 60;
    private static final int REPELLING_READY_TICKS = 50;
    private static final float MAX_ROPE_LENGTH = 50.0F;
    private static final float SERVER_ROPE_STEP = 0.25F;
    private static final float CLIENT_ROPE_STEP = 0.3F;

    private final AircraftEntity vehicle;
    private final StatusWord status;
    private final SeatsComponent seats;
    private final BodyComponent body;
    private final PositionHistory history;
    private final ControlKeysComponent keys;
    private final LifecycleComponent lifecycle;
    private final FlightModesComponent modes;
    /** How far the ropes hang below their hooks; negative, 0 while not repelling. */
    private float ropeLength;
    private int repellingTicks;
    private int lastRopeIndex;

    public RepellingRopes(AircraftEntity vehicle, StatusWord status, SeatsComponent seats, BodyComponent body,
            PositionHistory history, ControlKeysComponent keys, LifecycleComponent lifecycle,
            FlightModesComponent modes) {
        this.vehicle = vehicle;
        this.status = status;
        this.seats = seats;
        this.body = body;
        this.history = history;
        this.keys = keys;
        this.lifecycle = lifecycle;
        this.modes = modes;
        modes.whenHoverSwitched(this::stopRepelling);
    }

    /** Server: the ropes lower and, once they are down, carry the crew down. */
    @Override
    public void serverTick(TickContext ctx) {
        VehicleSpec info = this.vehicle.spec();
        if (info != null && tickRopes(info, SERVER_ROPE_STEP)) {
            for (int rope = 0; rope < info.crew().repellingHooks().size(); rope++) {
                VehicleParts.RepellingHook hook = info.crew().repellingHooks().get(rope);
                if (this.lifecycle.age() % hook.interval() == 0) {
                    sendNextCrewDownRope(hook, rope);
                }
            }
        }
    }

    /** Client: the ropes are lowered here too, a little faster to hide the sync delay. */
    @Override
    public void clientTick(TickContext ctx) {
        VehicleSpec info = this.vehicle.spec();
        if (info != null) {
            tickRopes(info, CLIENT_ROPE_STEP);
        }
    }

    /** Crew are sliding down the ropes; the pilot looks around freely meanwhile. */
    public boolean isRepelling() {
        return this.status.get(StatusWord.REPELLING);
    }

    /** World position of a rope's hook, or the vehicle position for an unknown rope. */
    public Vec3 ropePosition(int ropeIndex) {
        VehicleSpec info = this.vehicle.spec();
        if (info != null && info.crew().haveRepellingHook() && ropeIndex < info.crew().repellingHooks().size()) {
            return this.body.toWorld(info.crew().repellingHooks().get(ropeIndex).pos());
        }
        return this.vehicle.position();
    }

    public float ropeLength() {
        return this.ropeLength;
    }

    /** The ropes are down: crew may go down them. */
    public boolean canRepell() {
        return isRepelling() && this.repellingTicks > REPELLING_READY_TICKS;
    }

    /** Repelling needs a steady hover: level attitude and hardly any drift over the last half second. */
    public boolean canStartRepelling() {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = Objects.requireNonNull(ac.spec());
        if (info.crew().haveRepellingHook() && this.modes.isHovering() && Math.abs(ac.getXRot()) < 3.0F
                && Math.abs(ac.roll()) < 3.0F) {
            return this.history.halfSecondAgo().distanceTo(ac.position()) < 0.3;
        }
        return false;
    }

    void startRepelling() {
        this.status.set(StatusWord.REPELLING, true);
        this.keys.releaseAll();
        this.repellingTicks = 0;
    }

    public void stopRepelling() {
        this.status.set(StatusWord.REPELLING, false);
    }

    /** A passenger asked to leave while the ropes are down: they go down the next rope. */
    void sendDownNextRope(SeatEntity seat, Entity entity, VehicleSpec info) {
        this.lastRopeIndex = (this.lastRopeIndex + 1) % info.crew().repellingHooks().size();
        Vec3 hookPos = info.crew().repellingHooks().get(this.lastRopeIndex).pos();
        Vec3 dropPos = this.body.toWorld(hookPos, this.history.halfSecondAgo()).add(0.0, -2.0, 0.0);
        seat.setPos(dropPos.x, dropPos.y, dropPos.z);
        entity.stopRiding();
        entity.setPos(dropPos.x, dropPos.y, dropPos.z);
        slideDownRope(entity, dropPos, this.lastRopeIndex);
    }

    /** Lowers the ropes towards the ground; true once the crew may start going down. */
    private boolean tickRopes(VehicleSpec info, float step) {
        AircraftEntity ac = this.vehicle;
        if (info.crew().haveRepellingHook()) {
            if (isRepelling()) {
                int altitude = airBelow(ac);
                if (this.ropeLength > -MAX_ROPE_LENGTH && this.ropeLength > -altitude) {
                    this.ropeLength -= step;
                }
            } else {
                this.ropeLength = 0.0F;
            }
        }
        if (!isRepelling()) {
            this.repellingTicks = 0;
        } else if (this.repellingTicks < REPELLING_WARMUP_TICKS) {
            this.repellingTicks++;
        } else {
            return true;
        }
        return false;
    }

    private void sendNextCrewDownRope(VehicleParts.RepellingHook hook, int rope) {
        for (int i = 1; i < this.seats.count(); i++) {
            SeatEntity seat = this.seats.seat(i);
            if (seat == null) {
                continue;
            }
            Entity rider = seat.getRiddenByEntity();
            if (rider == null || rider instanceof Player || rider instanceof AiGunner
                    || this.seats.info(i + 1) instanceof SeatRackInfo) {
                continue;
            }
            Vec3 dropPos = this.body.toWorld(hook.pos(), this.history.halfSecondAgo());
            seat.setPos(dropPos.x, dropPos.y - 2.0, dropPos.z);
            rider.stopRiding();
            slideDownRope(rider, dropPos, rope);
            this.lastRopeIndex = rope;
            return;
        }
    }

    /** Hands the entity to a {@link HideEntity} that slides it down the rope. */
    private void slideDownRope(Entity entity, Vec3 dropPos, int rope) {
        entity.setPos(dropPos.x, dropPos.y - 2.0, dropPos.z);
        HideEntity carrier = new HideEntity(this.vehicle.level(), entity.getX(), entity.getY(), entity.getZ());
        carrier.setParent(this.vehicle, entity, rope);
        carrier.setDeltaMovement(Vec3.ZERO);
        entity.setDeltaMovement(Vec3.ZERO);
        carrier.fallDistance = entity.fallDistance = 0.0F;
        this.vehicle.level().addFreshEntity(carrier);
    }

    /** Blocks of air below the vehicle (at most 256), without looking below the world. */
    private static int airBelow(Entity vehicle) {
        double posX = vehicle.getX();
        double posY = vehicle.getY();
        double posZ = vehicle.getZ();
        int minY = vehicle.level().getMinBuildHeight();
        int maxY = vehicle.level().getMaxBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int depth = 0;
        while (depth < 256 && posY - depth > minY) {
            if (posY - depth < maxY) {
                pos.set(Mth.floor(posX), Mth.floor(posY) - depth, Mth.floor(posZ));
                if (!vehicle.level().getBlockState(pos).isAir()) {
                    break;
                }
            }
            depth++;
        }
        return depth;
    }
}
