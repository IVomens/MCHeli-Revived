package com.ivomens.mcheli.aircraft.parachute;

import com.ivomens.mcheli.aircraft.AircraftEntityTypes;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.entity.SinglePassenger;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A parachute carrying one rider. The handheld parachute is open right away; the ejection seat
 * parachute opens once it falls and turns into a boat when it lands on open water. The rider steers
 * by walking.
 */
public class ParachuteEntity extends McheliEntity implements SinglePassenger {

    private static final EntityDataAccessor<Byte> TYPE = SynchedEntityData.defineId(ParachuteEntity.class, EntityDataSerializers.BYTE);
    /** Round parachute of the parachute item. */
    public static final int TYPE_HANDHELD = 1;
    /** Ejection seat whose canopy opens once it starts falling. */
    public static final int TYPE_EJECTION_SEAT = 2;
    /** The ejection seat canopy alone, used when crew bail out of a vehicle. */
    public static final int TYPE_CANOPY = 3;
    /** For this many ticks after launch an ejection seat feels less gravity and vertical drag. */
    private static final int EJECTION_DEPLOY_TICKS = 20;
    private static final double MIN_STEER_SPEED = 0.07;
    private static final double MAX_SPEED = 0.35;
    /** Ticks on the ground before the parachute is packed up. */
    private static final int LANDED_TICKS = 5;
    /** Water blocks (of 75 checked) needed below the landing spot to inflate the boat. */
    private static final int BOAT_WATER_BLOCKS = 37;

    /** Entity to put into the parachute on its first server tick. */
    private Entity user;
    private int onGroundCount;
    private double speedMultiplier = MIN_STEER_SPEED;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYaw;
    private double lerpPitch;
    private double velocityX;
    private double velocityY;
    private double velocityZ;

    public void setUser(Entity user) {
        this.user = user;
    }

    public ParachuteEntity(EntityType<? extends ParachuteEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public ParachuteEntity(Level level) {
        this(AircraftEntityTypes.PARACHUTE.get(), level);
    }

    public ParachuteEntity(Level level, double x, double y, double z) {
        this(level);
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    @Override
    protected @NotNull MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(TYPE, (byte) 0);
    }

    /** Parachute model type ({@code Entity#getType} is taken by the entity type). */
    public int getParachuteType() {
        return this.entityData.get(TYPE);
    }

    public void setType(int type) {
        this.entityData.set(TYPE, (byte) type);
    }

    private boolean isEjectionSeatDeploying() {
        return this.getParachuteType() == TYPE_EJECTION_SEAT && this.tickCount < EJECTION_DEPLOY_TICKS;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    public double getMountedYOffset() {
        return -0.3F;
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
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        this.lerpSteps = steps + 10;
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yaw;
        this.lerpPitch = pitch;
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
        if (this.isOpenParachute() && this.getDeltaMovement().y > -0.3 && this.tickCount > 20) {
            this.fallDistance = (float) (this.fallDistance * 0.85);
        }

        if (!this.level().isClientSide && this.user != null && this.user.getVehicle() == null) {
            this.user.startRiding(this);
            this.setYRot(this.user.getYRot());
            this.yRotO = this.getYRot();
            this.user = null;
        }

        if (this.isTouchingWater()) {
            this.inflateBoat();
            if (!this.level().isClientSide) this.discard();
            return;
        }

        if (this.level().isClientSide) {
            this.tickClient();
        } else {
            this.tickServer();
        }
    }

    /** Water in the bottom fifth of the box (extended an eighth of a block down). */
    private boolean isTouchingWater() {
        AABB box = this.getBoundingBox();
        double bottom = box.minY - 0.125;
        double top = box.minY + (box.maxY - box.minY) / 5.0 - 0.125;
        AABB waterBox = new AABB(box.minX, bottom, box.minZ, box.maxX, top, box.maxZ);
        return this.level().getBlockStates(waterBox).anyMatch(state -> state.getFluidState().is(FluidTags.WATER));
    }

    private void tickClient() {
        if (this.lerpSteps > 0) {
            double x = this.getX() + (this.lerpX - this.getX()) / this.lerpSteps;
            double y = this.getY() + (this.lerpY - this.getY()) / this.lerpSteps;
            double z = this.getZ() + (this.lerpZ - this.getZ()) / this.lerpSteps;
            double yaw = Mth.wrapDegrees(this.lerpYaw - this.getYRot());
            this.setYRot((float) (this.getYRot() + yaw / this.lerpSteps));
            this.setXRot((float) (this.getXRot() + (this.lerpPitch - this.getXRot()) / this.lerpSteps));
            this.lerpSteps--;
            this.setPos(x, y, z);
            if (this.getRiddenByEntity() != null) {
                this.setYRot(this.getRiddenByEntity().yRotO);
            }
        } else {
            Vec3 m = this.getDeltaMovement();
            this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
            this.setDeltaMovement(m.x * 0.99, m.y * 0.95, m.z * 0.99);
        }

        Vec3 m = this.getDeltaMovement();
        if (!this.isOpenParachute() && m.y > 0.01) {
            this.spawnEjectionSmoke(m);
        }
    }

    /** Rocket smoke of an ejection seat still climbing. */
    private void spawnEjectionSmoke(Vec3 m) {
        float color = 0.6F + this.random.nextFloat() * 0.2F;
        double dx = this.xo - this.getX();
        double dy = this.yo - this.getY();
        double dz = this.zo - this.getZ();
        int count = 1 + (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 2.0);
        for (int i = 0; i < count; i++) {
            double t = (double) i / count * 0.8;
            ParticleParams prm = new ParticleParams(this.level(), "smoke",
                    this.xo + (this.getX() - this.xo) * t,
                    this.yo + (this.getY() - this.yo) * t,
                    this.zo + (this.getZ() - this.zo) * t);
            prm.setMotion(m.x * 0.5 + (this.random.nextDouble() - 0.5) * 0.5, m.y * -0.5 + (this.random.nextDouble() - 0.5) * 0.5,
                    m.z * 0.5 + (this.random.nextDouble() - 0.5) * 0.5);
            prm.setSize(5.0F);
            prm.setColor(0.8F + this.random.nextFloat(), color, color, color);
            Effects.get().spawn(prm);
        }
    }

    private void tickServer() {
        Vec3 m = this.getDeltaMovement();
        double motionX = m.x;
        double motionZ = m.z;
        double gravity = this.onGround() || this.isEjectionSeatDeploying() ? 0.01 : 0.03;
        double motionY = m.y - gravity;
        if (this.isOpenParachute()) {
            double prevSpeed = Math.sqrt(motionX * motionX + motionZ * motionZ);
            Entity rider = this.getRiddenByEntity();
            if (rider instanceof LivingEntity living && living.zza > 0.0) {
                motionX += -Math.sin(rider.getYRot() * (float) Math.PI / 180.0F) * this.speedMultiplier * 0.05;
                motionZ += Math.cos(rider.getYRot() * (float) Math.PI / 180.0F) * this.speedMultiplier * 0.05;
            }

            double speed = Math.sqrt(motionX * motionX + motionZ * motionZ);
            if (speed > MAX_SPEED) {
                motionX *= MAX_SPEED / speed;
                motionZ *= MAX_SPEED / speed;
                speed = MAX_SPEED;
            }
            this.updateSteerSpeed(speed > prevSpeed);
        }

        if (this.onGround() && ++this.onGroundCount > LANDED_TICKS) {
            this.land();
            return;
        }

        this.setDeltaMovement(motionX, motionY, motionZ);
        this.move(MoverType.SELF, this.getDeltaMovement());
        m = this.getDeltaMovement();
        double verticalDrag = this.isEjectionSeatDeploying() ? 0.95 : 0.9;
        double horizontalDrag = this.isOpenParachute() ? 0.95 : 0.99;
        this.setDeltaMovement(m.x * horizontalDrag, m.y * verticalDrag, m.z * horizontalDrag);

        this.setXRot(0.0F);
        this.updateYaw();
        this.pushOtherParachutes();

        if (this.getRiddenByEntity() != null && this.getRiddenByEntity().isRemoved()) {
            this.discard();
        }
    }

    /** Steering builds up while the rider keeps speeding up and fades back otherwise. */
    private void updateSteerSpeed(boolean accelerating) {
        if (accelerating && this.speedMultiplier < MAX_SPEED) {
            this.speedMultiplier = Math.min(MAX_SPEED, this.speedMultiplier + (MAX_SPEED - this.speedMultiplier) / 35.0);
        } else {
            this.speedMultiplier = Math.max(MIN_STEER_SPEED, this.speedMultiplier - (this.speedMultiplier - MIN_STEER_SPEED) / 35.0);
        }
    }

    /** Faces the rider's direction, or turns (20 degrees per tick at most) into the drift. */
    private void updateYaw() {
        Entity rider = this.getRiddenByEntity();
        if (rider != null) {
            this.setYRot(rider.getYRot());
            return;
        }
        double yaw = this.getYRot();
        double dx = this.xo - this.getX();
        double dz = this.zo - this.getZ();
        if (dx * dx + dz * dz > 0.001) {
            yaw = (float) (Math.atan2(dx, dz) * 180.0 / Math.PI);
        }
        double yawDiff = Mth.clamp(Mth.wrapDegrees(yaw - this.getYRot()), -20.0, 20.0);
        this.setYRot((float) (this.getYRot() + yawDiff));
    }

    private void pushOtherParachutes() {
        Entity rider = this.getRiddenByEntity();
        for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(0.2, 0.0, 0.2),
                e -> e instanceof ParachuteEntity && e.isPushable())) {
            if (entity != rider) {
                entity.push(this);
            }
        }
    }

    /** Lifts the rider clear of the ground and packs the parachute up. */
    private void land() {
        this.setPos(this.getX(), this.getY() + 1, this.getZ());
        Entity rider = this.getRiddenByEntity();
        if (rider != null) {
            this.positionRider(rider);
        }
        this.discard();
    }

    /** Server: an ejection seat landing on open water puts its rider into a boat. */
    private void inflateBoat() {
        Entity rider = this.getRiddenByEntity();
        if (this.level().isClientSide || this.getParachuteType() != TYPE_EJECTION_SEAT || rider == null) {
            return;
        }
        int px = Mth.floor(this.getX() + 0.5);
        int py = Mth.floor(this.getY() + 0.5);
        int pz = Mth.floor(this.getZ() + 0.5);
        int minY = this.level().getMinBuildHeight();
        int maxY = this.level().getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int waterY = Integer.MIN_VALUE;
        for (int dy = 0; dy < 5 && py + dy >= minY && py + dy <= maxY; dy++) {
            if (this.level().getBlockState(pos.set(px, py - dy, pz)).is(Blocks.WATER)) {
                waterY = py - dy;
                break;
            }
        }
        if (waterY == Integer.MIN_VALUE || !this.isOpenWater(pos, px, waterY, pz, minY, maxY)) {
            return;
        }
        Boat boat = new Boat(this.level(), px, waterY + 1.0F, pz);
        boat.setYRot(this.getYRot() - 90.0F);
        this.level().addFreshEntity(boat);
        rider.startRiding(boat);
    }

    /** Whether the 5x5x3 block below the surface point is mostly water. */
    private boolean isOpenWater(BlockPos.MutableBlockPos pos, int x, int surfaceY, int z, int minY, int maxY) {
        int waterCount = 0;
        for (int dy = 0; dy < 3 && surfaceY + dy >= minY && surfaceY + dy <= maxY; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (this.level().getBlockState(pos.set(x + dx, surfaceY - dy, z + dz)).is(Blocks.WATER) && ++waterCount > BOAT_WATER_BLOCKS) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The ejection seat parachute only opens once it is falling. */
    public boolean isOpenParachute() {
        return this.getParachuteType() != TYPE_EJECTION_SEAT || this.getDeltaMovement().y < -0.1;
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (this.hasPassenger(passenger)) {
            double x = -Math.sin(this.getYRot() * Math.PI / 180.0) * 0.1;
            double z = Math.cos(this.getYRot() * Math.PI / 180.0) * 0.1;
            // players ride a little lower so they hang below the canopy
            double riderOffset = passenger instanceof Player ? -0.35 : 0.0;
            callback.accept(passenger, this.getX() + x, this.getY() + this.getMountedYOffset() + riderOffset, this.getZ() + z);
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        nbt.putByte("ParachuteModelType", (byte) this.getParachuteType());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        this.setType(nbt.getByte("ParachuteModelType"));
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public Entity getRiddenByEntity() {
        List<Entity> passengers = this.getPassengers();
        return passengers.isEmpty() ? null : passengers.get(0);
    }
}
