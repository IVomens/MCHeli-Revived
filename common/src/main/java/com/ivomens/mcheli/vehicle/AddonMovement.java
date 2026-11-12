package com.ivomens.mcheli.vehicle;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftPhysics;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.extension.MovementContext;
import com.ivomens.mcheli.api.vehicle.extension.MovementModel;
import com.ivomens.mcheli.api.vehicle.extension.VehicleControls;
import com.ivomens.mcheli.core.sim.body.MoveResult;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.model.GroundVehicleHandling;
import com.ivomens.mcheli.extension.ConfiguredMovement;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.component.KeyControls;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Moves a vehicle with the movement model of another mod, when its file picks one ({@code Movement}): the server
 * steps the model and moves the vehicle by the velocity it sets with the vehicle's own collider; a client
 * dead-reckons between server updates and lets the model adjust the velocity it predicts with. The model is made
 * per vehicle and made again when a content reload replaced the file. A model that throws is logged and the
 * vehicle falls back to its kind's movement until the file is loaded again.
 */
final class AddonMovement implements MovementContext {

    /** The furthest {@link #heightAboveGround} looks down. */
    private static final int MAX_GROUND_SEARCH = 64;

    private final AircraftEntity vehicle;
    private final VehicleControls controls;
    /** What {@link #model} was made from; compared by identity, a reload makes a new one. */
    @Nullable
    private ConfiguredMovement<?> source;
    @Nullable
    private MovementModel model;
    private boolean failed;
    private Vec3 velocity = Vec3.ZERO;
    private float yaw;
    private boolean moved;
    private boolean horizontalCollision;

    AddonMovement(AircraftEntity vehicle) {
        this.vehicle = vehicle;
        this.controls = new KeyControls(vehicle.keys(), vehicle);
    }

    /** Whether the vehicle's file picks a model now (and it has not failed), so this moves the vehicle. */
    boolean isActive() {
        VehicleSpec spec = this.vehicle.spec();
        ConfiguredMovement<?> wanted = spec != null ? spec.file().addons().movement() : null;
        if (wanted != this.source) {
            this.source = wanted;
            this.failed = false;
            this.model = wanted != null ? make(wanted) : null;
        }
        return this.model != null && !this.failed;
    }

    @Nullable
    private MovementModel make(ConfiguredMovement<?> configured) {
        try {
            return configured.create();
        } catch (RuntimeException e) {
            ModLogger.error("Movement model " + configured.id() + " could not be made for " + this.vehicle, e);
            return null;
        }
    }

    /** Server: one step of the model, the move through the collider, then the model's look at the result. */
    void step() {
        MovementModel stepping = this.model;
        begin();
        if (!run(stepping, "step", () -> stepping.step(this))) {
            return;
        }
        this.vehicle.setYRot(this.yaw);
        AircraftPhysics physics = this.vehicle.physics();
        MoveResult result = physics.context().collider().move(physics.body()
                .withVelocity(new Vec3d(this.velocity.x, this.velocity.y, this.velocity.z)));
        this.velocity = new Vec3(result.velocity().x(), result.velocity().y(), result.velocity().z());
        this.horizontalCollision = result.horizontalCollision();
        this.moved = true;
        if (run(stepping, "afterMove", () -> stepping.afterMove(this))) {
            finish();
        }
        VehicleMotion.trampleSoftBlocks(this.vehicle);
    }

    /** Client, between server updates: moves by the last velocity, then lets the model change it. */
    void predict() {
        MovementModel predicting = this.model;
        Vec3 motion = this.vehicle.getDeltaMovement();
        this.vehicle.setPos(this.vehicle.getX() + motion.x, this.vehicle.getY() + motion.y,
                this.vehicle.getZ() + motion.z);
        begin();
        if (run(predicting, "predict", () -> predicting.predict(this))) {
            finish();
        }
    }

    private void begin() {
        this.velocity = this.vehicle.getDeltaMovement();
        this.yaw = this.vehicle.getYRot();
        this.moved = false;
        this.horizontalCollision = false;
    }

    private void finish() {
        this.vehicle.setDeltaMovement(this.velocity);
        if (this.yaw != this.vehicle.getYRot()) {
            this.vehicle.setYRot(this.yaw);
        }
    }

    private boolean run(MovementModel running, String hook, Runnable call) {
        try {
            call.run();
            return true;
        } catch (RuntimeException e) {
            this.failed = true;
            ModLogger.error("Movement model " + (this.source != null ? this.source.id() : running) + " threw in "
                    + hook + " on " + this.vehicle + "; the vehicle moves as its kind does from now on", e);
            return false;
        }
    }

    @Override
    public Vehicle vehicle() {
        return this.vehicle;
    }

    @Override
    public Level level() {
        return this.vehicle.level();
    }

    @Override
    public boolean isClientSide() {
        return this.vehicle.level().isClientSide();
    }

    @Override
    public VehicleControls controls() {
        return this.controls;
    }

    @Override
    public Vec3 position() {
        return this.vehicle.position();
    }

    @Override
    public Vec3 velocity() {
        return this.velocity;
    }

    @Override
    public float yaw() {
        return this.yaw;
    }

    @Override
    public float pitch() {
        return this.vehicle.getXRot();
    }

    @Override
    public float roll() {
        return this.vehicle.roll();
    }

    @Override
    public boolean onGround() {
        return this.vehicle.onGround();
    }

    @Override
    public boolean inWater() {
        return this.vehicle.isInWater();
    }

    @Override
    public boolean horizontalCollision() {
        return this.moved && this.horizontalCollision;
    }

    @Override
    public boolean isDestroyed() {
        return this.vehicle.isDestroyed();
    }

    @Override
    public double waterDepth() {
        return this.vehicle.getWaterDepth();
    }

    @Override
    public double gravity() {
        GroundVehicleHandling handling = this.vehicle.physics().handling(GroundVehicleHandling.class);
        return handling.gravity().in(this.vehicle.isInWater());
    }

    @Override
    public double heightAboveGround(int maxDepth) {
        Level level = this.vehicle.level();
        double bottom = this.vehicle.getY();
        int x = Mth.floor(this.vehicle.getX());
        int z = Mth.floor(this.vehicle.getZ());
        int top = Mth.floor(bottom);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y >= top - Math.min(maxDepth, MAX_GROUND_SEARCH); y--) {
            pos.set(x, y, z);
            if (!level.hasChunkAt(pos)) {
                return Double.POSITIVE_INFINITY;
            }
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return bottom - (y + shape.max(Direction.Axis.Y));
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public void setVelocity(Vec3 velocity) {
        this.velocity = Objects.requireNonNull(velocity, "velocity");
    }

    @Override
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }
}
