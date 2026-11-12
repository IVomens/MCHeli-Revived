package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.entityview.RappelRope;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.entity.SinglePassenger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Invisible carrier used while a crew member slides down a repelling rope. It follows the rope's
 * hook horizontally and drops off its rider on the ground or at the end of the rope.
 */
public class HideEntity extends McheliEntity implements SinglePassenger, RappelRope {

    private static final EntityDataAccessor<Integer> ROPE_INDEX = SynchedEntityData.defineId(HideEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AC_ID = SynchedEntityData.defineId(HideEntity.class,
            EntityDataSerializers.INT);
    private AircraftEntity aircraft;
    /** Entity to pick up on the first server tick. */
    private Entity user;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYaw;
    private double lerpPitch;
    private double velocityX;
    private double velocityY;
    private double velocityZ;

    public HideEntity(EntityType<? extends HideEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.setDeltaMovement(Vec3.ZERO);
    }

    public HideEntity(Level level) {
        this(AircraftEntityTypes.HIDE.get(), level);
    }

    public HideEntity(Level level, double x, double y, double z) {
        this(level);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROPE_INDEX, -1);
        builder.define(AC_ID, 0);
    }

    public void setParent(AircraftEntity aircraft, Entity user, int ropeIndex) {
        this.aircraft = aircraft;
        this.setRopeIndex(ropeIndex);
        this.user = user;
    }

    @Override
    protected @NotNull MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        return !(entity instanceof LivingEntity) && !(entity instanceof SeatEntity) && !(entity instanceof HitBoxEntity)
                && super.canCollideWith(entity);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag nbt) {
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag nbt) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public int getRopeIndex() {
        return this.entityData.get(ROPE_INDEX);
    }

    public void setRopeIndex(int value) {
        this.entityData.set(ROPE_INDEX, value);
    }

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (this.hasPassenger(passenger)) {
            // players sit a little lower than other riders
            double y = this.getY() - 0.3 + (passenger instanceof Player ? -0.35 : 0.0);
            callback.accept(passenger, this.getX(), y, this.getZ());
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpSteps = steps + 10;
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yRot;
        this.lerpPitch = xRot;
        this.setDeltaMovement(this.velocityX, this.velocityY, this.velocityZ);
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        this.velocityX = x;
        this.velocityY = y;
        this.velocityZ = z;
        this.setDeltaMovement(x, y, z);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.user != null && !this.level().isClientSide) {
            if (this.aircraft != null) {
                this.entityData.set(AC_ID, this.aircraft.getId());
            }
            this.user.startRiding(this, true);
            this.user = null;
        }
        if (this.aircraft == null && this.level().isClientSide) {
            int id = this.entityData.get(AC_ID);
            if (id > 0 && this.level().getEntity(id) instanceof AircraftEntity parent) {
                this.aircraft = parent;
            }
        }

        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.fallDistance = 0.0F;
        Entity rider = this.getRiddenByEntity();
        if (rider != null) {
            rider.fallDistance = 0.0F;
        }

        double x = this.getX();
        double z = this.getZ();
        if (this.aircraft != null) {
            if (!this.aircraft.ropes().isRepelling()) {
                this.discard();
            }
            int rope = this.getRopeIndex();
            if (rope >= 0) {
                Vec3 hook = this.aircraft.ropes().ropePosition(rope);
                x = hook.x;
                z = hook.z;
            }
        }
        this.setPos(x, this.getY(), z);
        if (this.level().isClientSide) {
            this.onUpdateClient();
        } else {
            this.onUpdateServer();
        }
    }

    public void onUpdateClient() {
        if (this.lerpSteps > 0) {
            double x = this.getX() + (this.lerpX - this.getX()) / this.lerpSteps;
            double y = this.getY() + (this.lerpY - this.getY()) / this.lerpSteps;
            double z = this.getZ() + (this.lerpZ - this.getZ()) / this.lerpSteps;
            double yawDelta = Mth.wrapDegrees(this.lerpYaw - this.getYRot());
            this.setYRot((float) (this.getYRot() + yawDelta / this.lerpSteps));
            this.setXRot((float) (this.getXRot() + (this.lerpPitch - this.getXRot()) / this.lerpSteps));
            this.lerpSteps--;
            this.setPos(x, y, z);
            Entity rider = this.getRiddenByEntity();
            if (rider != null) {
                this.setYRot(rider.yRotO);
            }
        } else {
            Vec3 motion = this.getDeltaMovement();
            this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
            this.setDeltaMovement(motion.x * 0.99, motion.y * 0.95, motion.z * 0.99);
        }
    }

    /** Slides down with a little gravity; lands, or lets go past the end of the rope. */
    public void onUpdateServer() {
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x, motion.y - (this.onGround() ? 0.01 : 0.03), motion.z);
        if (this.onGround()) {
            this.onGroundAndDead();
            return;
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x * 0.95, motion.y * 0.9, motion.z * 0.95);
        int rope = this.getRopeIndex();
        if (this.aircraft != null && rope >= 0) {
            Vec3 hook = this.aircraft.ropes().ropePosition(rope);
            if (Math.abs(this.getY() - hook.y) > Math.abs(this.aircraft.ropes().ropeLength()) + 5.0F) {
                this.onGroundAndDead();
            }
        }
        Entity rider = this.getRiddenByEntity();
        if (rider != null && !rider.isAlive()) {
            this.discard();
        }
    }

    public void onGroundAndDead() {
        this.setPos(this.getX(), this.getY() + 0.5, this.getZ());
        Entity rider = this.getRiddenByEntity();
        if (rider != null) {
            this.positionRider(rider);
        }
        this.ejectPassengers();
        this.discard();
    }

    @Nullable
    @Override
    public Entity getRiddenByEntity() {
        List<Entity> passengers = this.getPassengers();
        return passengers.isEmpty() ? null : passengers.getFirst();
    }
}
