package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.entity.InventoryEntity;
import com.ivomens.mcheli.vehicle.component.BodyComponent;
import com.ivomens.mcheli.vehicle.component.CollisionExclusions;
import com.ivomens.mcheli.vehicle.component.CrewExitComponent;
import com.ivomens.mcheli.vehicle.component.DamageComponent;
import com.ivomens.mcheli.vehicle.component.HullDamageComponent;
import com.ivomens.mcheli.vehicle.component.MotionSyncComponent;
import com.ivomens.mcheli.vehicle.component.OwnershipComponent;
import com.ivomens.mcheli.vehicle.component.RestComponent;
import com.ivomens.mcheli.vehicle.component.SeatsComponent;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinition;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * A vehicle as the game sees it: an entity that is a rigid hull. It cannot be pushed, takes no fall damage
 * and makes no step sounds; it moves with the vanilla swept-box collision, which resolves the larger
 * horizontal axis first and so behaves the same whichever way the hull faces; clients move it towards what
 * the server sends; it carries its pilot as its one passenger and syncs its state in the entity data defined
 * here.
 * <p>
 * This class only answers the game's calls; what happens is up to the components {@link AircraftEntity}
 * holds and hands back through the accessors declared here.
 */
public abstract class VehicleEntity extends InventoryEntity {

    // The keys keep their order, so the synced data ids stay those vehicles always had.
    protected static final EntityDataAccessor<Integer> PART_STAT = define(EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DAMAGE = define(EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> UAV_STATION = define(EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> STATUS = define(EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> USE_WEAPON = define(EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> FUEL = define(EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> ROT_ROLL = define(EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> THROTTLE = define(EntityDataSerializers.INT);
    protected static final EntityDataAccessor<String> ID_TYPE = define(EntityDataSerializers.STRING);
    protected static final EntityDataAccessor<String> TEXTURE_NAME = define(EntityDataSerializers.STRING);
    protected static final EntityDataAccessor<String> LIVERY = define(EntityDataSerializers.STRING);
    protected static final EntityDataAccessor<String> FUEL_FF = define(EntityDataSerializers.STRING);
    protected static final EntityDataAccessor<String> COMMAND = define(EntityDataSerializers.STRING);
    /** Intact/popped state of the reactive armor tiles, see {@link ReactiveArmor}. */
    protected static final EntityDataAccessor<String> ERA_STATE = define(EntityDataSerializers.STRING);
    /** Who placed the vehicle, their name, and the lock rules in force; see {@link OwnershipComponent}. */
    protected static final EntityDataAccessor<Optional<UUID>> OWNER = define(EntityDataSerializers.OPTIONAL_UUID);
    protected static final EntityDataAccessor<String> OWNER_NAME = define(EntityDataSerializers.STRING);
    protected static final EntityDataAccessor<Integer> LOCK_STATE = define(EntityDataSerializers.INT);

    protected VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
    }

    private static <T> EntityDataAccessor<T> define(EntityDataSerializer<T> serializer) {
        return SynchedEntityData.defineId(VehicleEntity.class, serializer);
    }

    // The components the game's calls are answered with

    /** The vehicle's type as it was loaded, or null before the type is known. */
    @Nullable
    public abstract VehicleSpec spec();

    /** The frozen definition of the vehicle's type, or null before the type is known. */
    @Nullable
    public abstract VehicleDefinition definition();

    public abstract boolean isUAV();

    public abstract BodyComponent body();

    public abstract MotionSyncComponent motionSync();

    public abstract RestComponent rest();

    public abstract DamageComponent damage();

    public abstract HullDamageComponent hullDamage();

    public abstract CollisionExclusions collisionExclusions();

    public abstract SeatsComponent seats();

    public abstract CrewExitComponent crewExit();

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ID_TYPE, "");
        builder.define(DAMAGE, 0);
        builder.define(STATUS, 0);
        builder.define(USE_WEAPON, 0);
        builder.define(FUEL, 0);
        builder.define(FUEL_FF, "");
        builder.define(TEXTURE_NAME, "");
        builder.define(LIVERY, "");
        builder.define(UAV_STATION, 0);
        builder.define(ROT_ROLL, 0);
        builder.define(COMMAND, "");
        builder.define(THROTTLE, 0);
        builder.define(PART_STAT, 0);
        builder.define(ERA_STATE, "");
        builder.define(OWNER, Optional.empty());
        builder.define(OWNER_NAME, "");
        builder.define(LOCK_STATE, 0);
    }

    // Size and drawing

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // tracking range (capped by the view distance) already limits how far vehicles are sent
        return true;
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        return this.body().cullingBox();
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        EntityDimensions body = this.body().dimensions();
        return body != null ? body : super.getDimensions(pose);
    }

    // Movement and collision

    /** Vehicles without a type do not move at all; a wall hit at speed damages the hull. */
    @Override
    public void move(@NotNull MoverType type, @NotNull Vec3 motion) {
        if (this.spec() == null) {
            return;
        }
        double requestedSpeed = motion.horizontalDistance();
        double startX = this.getX();
        double startZ = this.getZ();
        super.move(type, motion);
        if (type == MoverType.SELF && this.horizontalCollision && !this.level().isClientSide) {
            double dx = this.getX() - startX;
            double dz = this.getZ() - startZ;
            this.hullDamage().onWallCollision(requestedSpeed, Math.sqrt(dx * dx + dz * dz));
        }
    }

    @Override
    public float maxUpStep() {
        VehicleDefinition definition = this.definition();
        return definition != null ? definition.body().stepHeight() : 0.0F;
    }

    @Override
    public void setDeltaMovement(@NotNull Vec3 motion) {
        // only outside pushes matter here; the vehicle's own physics never runs while resting
        if (this.rest().isResting() && motion.lengthSqr() > 1.0E-4) {
            this.rest().wake();
        }
        super.setDeltaMovement(motion);
    }

    @Override
    public void push(@NotNull Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        if (entity instanceof LivingEntity || entity instanceof SeatEntity || entity instanceof HitBoxEntity) {
            return false;
        }
        return !this.collisionExclusions().ignores(entity) && super.canCollideWith(entity);
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    /** Landing impact is handled by the hull damage; never forward fall damage to the crew. */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, @NotNull DamageSource source) {
        return false;
    }

    @Override
    protected @NotNull MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        this.rest().wake();
        return this.damage().hurt(source, amount);
    }

    // Client motion

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.motionSync().receivePosition(x, y, z, yRot, xRot, steps);
    }

    @Override
    public double lerpTargetX() {
        return this.motionSync().targetX();
    }

    @Override
    public double lerpTargetY() {
        return this.motionSync().targetY();
    }

    @Override
    public double lerpTargetZ() {
        return this.motionSync().targetZ();
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        this.motionSync().receiveVelocity(x, y, z);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide) {
            this.rest().wake();
        }
    }

    // The pilot: the one passenger. Everyone else rides a seat entity.

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    protected void addPassenger(@NotNull Entity passenger) {
        this.rest().wake();
        super.addPassenger(passenger);
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (this.hasPassenger(passenger)) {
            Vec3 v = this.seats().pilotPosition(this.getX(), this.getY(), this.getZ());
            callback.accept(passenger, v.x, v.y, v.z);
        }
    }

    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        if (!this.isUAV()) {
            Vec3 v = this.crewExit().getUnmountPosition(passenger, this.seats().info(0));
            if (v != null) {
                return v;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    /** Crew can use the inventory from anywhere within 64 blocks, everyone else only up close. */
    @Override
    public boolean stillValid(@NotNull Player player) {
        if (this.isUAV()) {
            return super.stillValid(player);
        }
        if (this.isRemoved()) {
            return false;
        }
        double maxDistanceSqr = this.seats().seatIdOf(player) >= 0 ? 4096.0 : 64.0;
        return player.distanceToSqr(this) <= maxDistanceSqr;
    }
}
