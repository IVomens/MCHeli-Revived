package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * Lets a parked vehicle stop ticking until something can change it.
 * <p>
 * An airbase or hangar can hold hundreds of vehicles, and every one of them used to run its full
 * physics, wheel, collision and animation step each tick while standing still. A vehicle that has
 * been empty, unpowered, undamaged and motionless for {@link #SETTLE_TICKS} ticks goes to rest;
 * while resting its tick returns immediately. It wakes when:
 * <ul>
 *   <li>someone boards it or interacts with it, or it takes damage,</li>
 *   <li>anything gives it velocity (explosions, tow chains, pistons...),</li>
 *   <li>the server sends the client a new position or new synced data,</li>
 *   <li>the periodic check finds the ground under it gone, or another rest condition broken.</li>
 * </ul>
 * Work that a parked vehicle still has to do (auto-repair, refuelling from its inventory, a burning
 * wreck) keeps it awake.
 */
public final class RestComponent implements VehicleComponent {

    /** How long a vehicle must stay still before it rests. */
    private static final int SETTLE_TICKS = 40;
    /** How often a resting vehicle re-checks its conditions. */
    private static final int CHECK_INTERVAL = 20;
    private static final double STILL_SPEED_SQR = 1.0E-4;
    /** Settling on uneven ground leaves tiny pitch and roll corrections that nobody can see. */
    private static final float STILL_DEGREES = 0.01F;

    private final AircraftEntity aircraft;
    private final RestState state;
    private final EngineComponent engine;
    private final TowComponent tow;
    private final SeatsComponent seats;
    private final UavLinkComponent uavLink;
    private final DamageComponent damage;
    private final FuelComponent fuel;

    public RestComponent(AircraftEntity aircraft, RestState state, EngineComponent engine, TowComponent tow,
            SeatsComponent seats, UavLinkComponent uavLink, DamageComponent damage, FuelComponent fuel) {
        this.aircraft = aircraft;
        this.state = state;
        this.engine = engine;
        this.tow = tow;
        this.seats = seats;
        this.uavLink = uavLink;
        this.damage = damage;
        this.fuel = fuel;
    }

    public boolean isResting() {
        return this.state.isResting();
    }

    public void wake() {
        this.state.wake();
    }

    /** Called at the start of every tick; true when the whole tick can be skipped. */
    public boolean skipTick() {
        if (!this.state.isResting()) {
            return false;
        }
        if (this.aircraft.tickCount % CHECK_INTERVAL == 0 && !canRest()) {
            wake();
            return false;
        }
        return true;
    }

    /** Called at the end of an ordinary tick. */
    public void afterTick() {
        this.state.countStillTick(canRest() && isStill(), SETTLE_TICKS);
    }

    /** What keeps the vehicle awake, or "resting" / "settling"; for tests and profiling. */
    public String status() {
        AircraftEntity ac = this.aircraft;
        if (ac.spec() == null) return "noinfo";
        if (ac.isDestroyed()) return "destroyed";
        if (ac.isOnFire()) return "fire";
        if (this.engine.throttle() > 0) return "throttle";
        if (this.tow.towChain() != null) return "tow";
        if (hasAnyoneAboard()) return "aboard";
        if (!ac.onGround()) return "air";
        if (!hasGround(ac)) return "noground";
        if (this.uavLink.station() != null) return "uav";
        if (this.damage.damageTaken() != 0) return "damaged";
        if (this.fuel.hasPendingWork()) return "fuel";
        if (ac.getDeltaMovement().lengthSqr() >= STILL_SPEED_SQR) return "motion " + ac.getDeltaMovement();
        double dx = ac.getX() - ac.xo, dy = ac.getY() - ac.yo, dz = ac.getZ() - ac.zo;
        if (dx * dx + dy * dy + dz * dz >= STILL_SPEED_SQR) return "moved";
        if (Math.abs(ac.getYRot() - ac.yRotO) >= STILL_DEGREES || Math.abs(ac.getXRot() - ac.xRotO) >= STILL_DEGREES) return "rot " + (ac.getYRot() - ac.yRotO) + "/" + (ac.getXRot() - ac.xRotO);
        return this.state.isResting() ? "resting" : "settling";
    }

    @Override
    public void describe(StateSink state) {
        state.put("resting", this.state.isResting());
        state.put("status", status());
    }

    private boolean isStill() {
        AircraftEntity ac = this.aircraft;
        double dx = ac.getX() - ac.xo, dy = ac.getY() - ac.yo, dz = ac.getZ() - ac.zo;
        return ac.getDeltaMovement().lengthSqr() < STILL_SPEED_SQR && dx * dx + dy * dy + dz * dz < STILL_SPEED_SQR
                && Math.abs(ac.getYRot() - ac.yRotO) < STILL_DEGREES && Math.abs(ac.getXRot() - ac.xRotO) < STILL_DEGREES;
    }

    private boolean canRest() {
        AircraftEntity ac = this.aircraft;
        if (ac.spec() == null || ac.isDestroyed() || ac.isOnFire() || this.engine.throttle() > 0
                || this.tow.towChain() != null || hasAnyoneAboard()) {
            return false;
        }
        if (ac.level().isClientSide) {
            return true;
        }
        return ac.onGround() && hasGround(ac) && this.uavLink.station() == null && this.damage.damageTaken() == 0
                && !this.fuel.hasPendingWork();
    }

    private boolean hasAnyoneAboard() {
        if (!this.aircraft.getPassengers().isEmpty()) {
            return true;
        }
        for (SeatEntity seat : this.seats.all()) {
            if (seat != null && seat.getRiddenByEntity() != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasGround(Entity entity) {
        BlockPos below = BlockPos.containing(entity.getX(), entity.getY() - 0.2, entity.getZ());
        return entity.level().hasChunkAt(below) && !entity.level().getBlockState(below).getCollisionShape(entity.level(), below).isEmpty();
    }
}
