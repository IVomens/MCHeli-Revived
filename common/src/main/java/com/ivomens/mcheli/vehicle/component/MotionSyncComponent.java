package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntSupplier;

/**
 * Keeping the clients' copy of a vehicle in step with the server's. The server says when the
 * vehicle has stopped; a client blends each position update from the server in over a few ticks and
 * predicts the motion itself between updates. This is the only place that does either; the vehicle
 * type picks how it blends and predicts.
 */
public final class MotionSyncComponent implements VehicleComponent {

    /** How the attitude is blended in. */
    public enum Smoothing {
        /** Yaw, pitch and roll, except on the pilot's own client (its attitude is its own) and on wrecks. */
        FLIGHT,
        /** Yaw and pitch always; the hull's roll comes from the terrain. */
        GROUND
    }

    private static final int ANNOUNCE_STOP_AFTER = 20;

    private final AircraftEntity vehicle;
    private final RestState rest;
    private final RollComponent roll;
    private final BodyComponent body;
    private Smoothing smoothing = Smoothing.FLIGHT;
    private Runnable prediction;
    private IntSupplier extraBlendTicks = () -> 3;
    private boolean driverSeesOwnVehicleSooner;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double targetYaw;
    private double targetPitch;
    private int blendTicksLeft;
    private int stillTicks;

    public MotionSyncComponent(AircraftEntity vehicle, RestState rest, RollComponent roll, BodyComponent body) {
        this.vehicle = vehicle;
        this.rest = rest;
        this.roll = roll;
        this.body = body;
        this.prediction = () -> VehicleMotion.predictFlightMovement(vehicle);
    }

    public void smoothAs(Smoothing smoothing) {
        this.smoothing = smoothing;
    }

    public void predictWith(Runnable prediction) {
        this.prediction = prediction;
    }

    /** Ticks added to the server's own step count when blending in an update; 3 unless the type says. */
    public void blendOverExtraTicks(IntSupplier ticks) {
        this.extraBlendTicks = ticks;
    }

    /** Ground vehicles are blended in one tick faster for their own driver. */
    public void driverSeesOwnVehicleSooner() {
        this.driverSeesOwnVehicleSooner = true;
    }

    /**
     * Server: after a second at rest, one explicit zero velocity goes out, so clients stop
     * extrapolating the last motion they received.
     */
    public void announceStop() {
        AircraftEntity v = this.vehicle;
        if (v.getDeltaMovement().lengthSqr() >= 1.0E-4) {
            this.stillTicks = 0;
        } else if (this.stillTicks < ANNOUNCE_STOP_AFTER && ++this.stillTicks >= ANNOUNCE_STOP_AFTER) {
            this.stillTicks = 0;
            if (v.level() instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().broadcast(v, new ClientboundSetEntityMotionPacket(v.getId(), Vec3.ZERO));
            }
        }
    }

    /** A velocity update from the server; it takes effect with the next position update. */
    public void receiveVelocity(double x, double y, double z) {
        this.velocityX = x;
        this.velocityY = y;
        this.velocityZ = z;
        this.vehicle.setDeltaMovement(x, y, z);
    }

    /** A position update from the server, blended in over the next ticks. */
    public void receivePosition(double x, double y, double z, float yaw, float pitch, int steps) {
        AircraftEntity v = this.vehicle;
        if (v.distanceToSqr(x, y, z) > 1.0E-6 || yaw != v.getYRot() || pitch != v.getXRot()) {
            this.rest.wake();
        }
        this.blendTicksLeft = blendTicks(steps);
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.targetYaw = yaw;
        this.targetPitch = pitch;
        v.setDeltaMovement(this.velocityX, this.velocityY, this.velocityZ);
    }

    private int blendTicks(int steps) {
        int ticks = steps + this.extraBlendTicks.getAsInt();
        if (this.driverSeesOwnVehicleSooner && LocalClient.get().isLocalPlayer(this.vehicle.getRiddenByEntity())) {
            return Math.max(1, ticks - 1);
        }
        return ticks;
    }

    @Override
    public void describe(StateSink state) {
        state.put("smoothing", this.smoothing.name().toLowerCase(java.util.Locale.ROOT));
        state.put("blend ticks left", this.blendTicksLeft);
        state.put("target", StateSink.number(targetX()) + ", " + StateSink.number(targetY()) + ", "
                + StateSink.number(targetZ()));
        state.put("still ticks", this.stillTicks);
    }

    public double targetX() {
        return this.blendTicksLeft > 0 ? this.targetX : this.vehicle.getX();
    }

    public double targetY() {
        return this.blendTicksLeft > 0 ? this.targetY : this.vehicle.getY();
    }

    public double targetZ() {
        return this.blendTicksLeft > 0 ? this.targetZ : this.vehicle.getZ();
    }

    /**
     * Client tick: blends in the last server update, or predicts while there is none. The local
     * pilot's own pitch is left alone, their client turns it every frame.
     */
    public void tick() {
        Entity rider = this.vehicle.getRiddenByEntity();
        if (rider != null && LocalClient.get().isLocalPlayer(rider)) {
            rider.setXRot(rider.xRotO);
        }
        if (this.blendTicksLeft > 0) {
            if (this.smoothing == Smoothing.FLIGHT) {
                blendFlight();
            } else {
                blendGround();
            }
            this.blendTicksLeft--;
        } else {
            this.prediction.run();
        }
    }

    private void blendFlight() {
        AircraftEntity v = this.vehicle;
        double ticks = this.blendTicksLeft;
        double yaw = Mth.wrapDegrees(this.targetYaw - v.getYRot());
        double roll = Mth.wrapDegrees(this.roll.synced() - this.roll.get());
        if (!v.isDestroyed() && (!LocalClient.get().isLocalPlayer(v.getRiddenByEntity()) || v.getVehicle() != null)) {
            v.setYRot((float) (v.getYRot() + yaw / ticks));
            v.setXRot((float) (v.getXRot() + (this.targetPitch - v.getXRot()) / ticks));
            this.roll.set((float) (this.roll.get() + roll / ticks));
        }
        moveTowardsTarget(ticks);
        this.body.turnTo(v.getYRot(), v.getXRot());
    }

    private void blendGround() {
        AircraftEntity v = this.vehicle;
        double ticks = this.blendTicksLeft;
        double yawChange = Mth.wrapDegrees(this.targetYaw - v.getYRot());
        float yaw = (float) (v.getYRot() + yawChange / ticks);
        float pitch = (float) (v.getXRot() + (this.targetPitch - v.getXRot()) / ticks);
        moveTowardsTarget(ticks);
        this.body.turnTo(yaw, pitch);
    }

    private void moveTowardsTarget(double ticks) {
        AircraftEntity v = this.vehicle;
        v.setPos(v.getX() + (this.targetX - v.getX()) / ticks, v.getY() + (this.targetY - v.getY()) / ticks,
                v.getZ() + (this.targetZ - v.getZ()) / ticks);
    }
}
