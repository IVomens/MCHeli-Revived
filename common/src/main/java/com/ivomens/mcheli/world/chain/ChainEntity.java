package com.ivomens.mcheli.world.chain;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.entityview.TowChain;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.world.WorldEntityTypes;
import com.ivomens.mcheli.world.cargo.CargoContainerEntity;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Tow chain between two vehicles. It rides along above the towing entity and pulls the towed one
 * when the chain is taut. After loading it looks both vehicles up by UUID for a while before giving up.
 */
public class ChainEntity extends McheliEntity implements TowChain {

    private static final EntityDataAccessor<Integer> TOWED_ID = SynchedEntityData.defineId(ChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TOW_ID = SynchedEntityData.defineId(ChainEntity.class, EntityDataSerializers.INT);
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 15;
    /** Ticks a loaded chain keeps looking for its vehicles. */
    private static final int RELINK_TICKS = 50;
    /** Beyond the chain length plus this slack the towed vehicle is dragged along directly. */
    private static final float MAX_SLACK = 2.0F;
    private static final float PULL_ACCELERATION = 0.001F;

    private Entity towEntity;
    private Entity towedEntity;
    private String towEntityUUID = "";
    private String towedEntityUUID = "";
    private int relinkTicksLeft = RELINK_TICKS;
    private int chainLength;
    private boolean isTowing;

    public ChainEntity(EntityType<? extends ChainEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.setChainLength(4);
    }

    public ChainEntity(Level level) {
        this(WorldEntityTypes.CHAIN.get(), level);
    }

    public ChainEntity(Level level, double x, double y, double z) {
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
        builder.define(TOWED_ID, 0);
        builder.define(TOW_ID, 0);
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }

    public int getChainLength() {
        return this.chainLength;
    }

    public void setChainLength(int length) {
        this.chainLength = Mth.clamp(length, MIN_LENGTH, MAX_LENGTH);
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        super.remove(reason);
        if (reason.shouldDestroy()) {
            this.playDisconnectTowingEntity();
        }
        this.isTowing = false;
        this.towEntity = null;
        this.towedEntity = null;
    }

    public boolean isTowingEntity() {
        return this.isTowing && !this.isRemoved() && this.towedEntity != null && this.towEntity != null;
    }

    public void setTowEntity(Entity towedEntity, Entity towEntity) {
        if (towedEntity != null && towEntity != null && !towedEntity.isRemoved() && !towEntity.isRemoved() &&
                !EntityIds.same(towedEntity, towEntity)) {
            this.isTowing = true;
            this.towedEntity = towedEntity;
            this.towEntity = towEntity;
            if (!this.level().isClientSide) {
                this.entityData.set(TOWED_ID, EntityIds.of(towedEntity));
                this.entityData.set(TOW_ID, EntityIds.of(towEntity));
                this.relinkTicksLeft = 0;
            }

            if (towEntity instanceof AircraftEntity ac) {
                ac.tow().setTowChain(this);
            }

            if (towedEntity instanceof AircraftEntity ac) {
                ac.tow().setTowedByChain(this);
            }
        } else {
            this.isTowing = false;
            this.towedEntity = null;
            this.towEntity = null;
        }
    }

    /** Re-links the chain after loading; looks the saved entities up by UUID (no area scan). */
    private void searchTowingEntity() {
        if ((this.towedEntity == null || this.towEntity == null) && !this.towedEntityUUID.isEmpty() &&
                !this.towEntityUUID.isEmpty() && this.level() instanceof ServerLevel serverLevel) {
            try {
                if (this.towedEntity == null) this.towedEntity = serverLevel.getEntity(UUID.fromString(this.towedEntityUUID));
                if (this.towEntity == null) this.towEntity = serverLevel.getEntity(UUID.fromString(this.towEntityUUID));
            } catch (IllegalArgumentException ignored) {
                this.towedEntityUUID = "";
                this.towEntityUUID = "";
            }
            if (this.towEntity != null && this.towedEntity != null) {
                this.setTowEntity(this.towedEntity, this.towEntity);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.towedEntity == null || this.towedEntity.isRemoved() || this.towEntity == null || this.towEntity.isRemoved()) {
            this.towedEntity = null;
            this.towEntity = null;
            this.isTowing = false;
        }

        if (this.towedEntity != null) {
            this.towedEntity.fallDistance = 0.0F;
        }

        if (this.level().isClientSide) {
            this.tickClient();
        } else {
            this.tickServer();
        }
    }

    private void tickClient() {
        if (!this.isTowingEntity()) {
            this.setTowEntity(this.level().getEntity(this.entityData.get(TOWED_ID)), this.level().getEntity(this.entityData.get(TOW_ID)));
        }

        Vec3 m = this.getDeltaMovement();
        this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
        if (this.onGround()) {
            m = m.scale(0.5);
        }

        this.setDeltaMovement(m.x * 0.99, m.y * 0.95, m.z * 0.99);
    }

    private void tickServer() {
        if (this.relinkTicksLeft > 0) {
            this.searchTowingEntity();
            if (this.towEntity != null && this.towedEntity != null) {
                this.relinkTicksLeft = 0;
            } else {
                this.relinkTicksLeft--;
            }
        } else if (this.towEntity == null || this.towedEntity == null) {
            this.discard();
            return;
        }

        Vec3 m = this.getDeltaMovement().add(0.0, -0.01, 0.0);
        if (!this.isTowing) {
            m = m.scale(0.8);
        }
        this.setDeltaMovement(m);

        this.move(MoverType.SELF, m);
        if (this.isTowingEntity()) {
            this.setPos(this.towEntity.getX(), this.towEntity.getY() + 2.0, this.towEntity.getZ());
            this.pullTowedEntity();
        }

        m = this.getDeltaMovement();
        this.setDeltaMovement(m.x * 0.99, m.y * 0.95, m.z * 0.99);
    }

    /** When the chain is taut, turns the towed vehicle towards the tug and pulls it along. */
    private void pullTowedEntity() {
        double dx = this.towedEntity.getX() - this.towEntity.getX();
        double dy = this.towedEntity.getY() - this.towEntity.getY();
        double dz = this.towedEntity.getZ() - this.towEntity.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float length = this.getChainLength();
        if (dist <= length) {
            return;
        }
        this.towedEntity.setYRot((float) (Math.atan2(dz, dx) * 180.0 / Math.PI) + 90.0F);
        this.towedEntity.yRotO = this.towedEntity.getYRot();
        double stretch = dist - length;
        this.towedEntity.setDeltaMovement(this.towedEntity.getDeltaMovement().subtract(
                dx * PULL_ACCELERATION / stretch, dy * PULL_ACCELERATION / stretch, dz * PULL_ACCELERATION / stretch));
        float maxDist = length + MAX_SLACK;
        if (dist > maxDist) {
            this.towedEntity.setPos(this.towEntity.getX() + dx * maxDist / dist,
                    this.towEntity.getY() + dy * maxDist / dist, this.towEntity.getZ() + dz * maxDist / dist);
        }
    }

    private void playDisconnectTowingEntity() {
        ModSounds.playSound(this.level(), this.getX(), this.getY(), this.getZ(), "mcheli:chain_ct", 1.0F, 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        if (this.isTowing && this.towEntity != null && !this.towEntity.isRemoved() && this.towedEntity != null &&
                !this.towedEntity.isRemoved()) {
            nbt.putString("TowEntityUUID", this.towEntity.getUUID().toString());
            nbt.putString("TowedEntityUUID", this.towedEntity.getUUID().toString());
            nbt.putInt("ChainLength", this.getChainLength());
        }
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        this.towEntityUUID = nbt.getString("TowEntityUUID");
        this.towedEntityUUID = nbt.getString("TowedEntityUUID");
        this.setChainLength(nbt.getInt("ChainLength"));
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        // the chain is drawn between the two vehicles
        if (this.towEntity != null && this.towedEntity != null) {
            return this.towEntity.getBoundingBox().minmax(this.towedEntity.getBoundingBox());
        }
        return super.getBoundingBoxForCulling();
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public Entity entity() {
        return this;
    }

    @Override
    @Nullable
    public Entity puller() {
        return this.towEntity;
    }

    @Override
    @Nullable
    public Entity towed() {
        return this.towedEntity;
    }

    @Override
    public boolean towsCargoContainer() {
        return this.towedEntity instanceof CargoContainerEntity;
    }
}
