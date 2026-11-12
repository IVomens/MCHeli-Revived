package com.ivomens.mcheli.world.uavstation;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.FriendlyFire;
import com.ivomens.mcheli.combat.damage.DamageScaling;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.entity.InventoryEntity;
import com.ivomens.mcheli.entity.SinglePassenger;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.ivomens.mcheli.vehicle.uav.UavControl;
import com.ivomens.mcheli.core.math.Degrees;
import com.ivomens.mcheli.world.WorldEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Placed (or handheld, see {@link UavTabletItem}) UAV control station. The operator sits on it, launches UAVs
 * from the item slot and flies the paired ones remotely.
 */
public class UavStationEntity extends InventoryEntity
        implements SinglePassenger, IUavStation, IUavPairingHolder {

    private static final EntityDataAccessor<Byte> STATUS = SynchedEntityData.defineId(UavStationEntity.class,
            EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> LAST_AC_ID = SynchedEntityData.defineId(UavStationEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> UAV_POS = SynchedEntityData.defineId(UavStationEntity.class,
            EntityDataSerializers.BLOCK_POS);
    /** {@link #STATUS} layout: the top bit is the cover (open) state, the rest the station kind. */
    private static final int STATUS_OPEN = 128;
    private static final int STATUS_KIND_MASK = 127;
    private static final int KIND_DEFAULT = 1;
    private static final int KIND_SMALL = 2;
    /** Launch point offset from the station, set from the station screen. */
    private BlockPos uavOffset = BlockPos.ZERO;
    private float coverRotation;
    private float prevCoverRotation;
    protected Entity lastRiddenByEntity;
    private final StationClientMotion clientMotion = new StationClientMotion(this);
    private AircraftEntity controlAircraft;
    private final LastControlledUav lastControlled = new LastControlledUav(this, LAST_AC_ID);
    /** UUIDs of the UAVs paired to this station (stable insertion order for the GUI list). */
    private final UavPairings pairings = new UavPairings();

    /** The tablet a handheld station was opened from; null for a placed station. */
    @Nullable
    private TabletLink tablet;

    public float coverRotation() {
        return this.coverRotation;
    }

    /** Where UAVs launch, relative to the station. */
    public BlockPos uavOffset() {
        return this.uavOffset;
    }

    public float prevCoverRotation() {
        return this.prevCoverRotation;
    }

    public UavStationEntity(EntityType<? extends UavStationEntity> type, Level level) {
        super(type, level);
        this.setInventorySize((short) 1);
        this.setDropContentsWhenDead(false);
        this.blocksBuilding = true;
        this.setDeltaMovement(Vec3.ZERO);
    }

    public UavStationEntity(Level level) {
        this(WorldEntityTypes.UAV_STATION.get(), level);
    }

    public static UavStationEntity createHandheld(Level world, Player player, InteractionHand hand,
                                                      Collection<UUID> paired, boolean smallOnly) {
        UavStationEntity station = new UavStationEntity(world);
        station.tablet = new TabletLink(player.getUUID(), hand);
        station.setPos(player.getX(), player.getY(), player.getZ());
        station.setYRot(player.getYRot());
        if (!world.isClientSide) {
            station.setType(smallOnly ? StationType.SMALL : StationType.DEFAULT);
        }
        station.pairings.addAll(paired);
        return station;
    }

    public boolean isHandheld() {
        return this.tablet != null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        // stations start open
        builder.define(STATUS, (byte) STATUS_OPEN);
        builder.define(LAST_AC_ID, 0);
        builder.define(UAV_POS, BlockPos.ZERO);
    }

    public int getStatus() {
        return this.entityData.get(STATUS);
    }

    public void setStatus(int status) {
        if (!this.level().isClientSide) {
            this.entityData.set(STATUS, (byte) status);
        }
    }

    @Override
    public @NotNull StationType getStationType() {
        int kind = STATUS_KIND_MASK & this.getStatus();
        return kind == KIND_SMALL ? StationType.SMALL : StationType.DEFAULT;
    }

    @Override
    public void setType(@NotNull StationType type) {
        int kind = type == StationType.SMALL ? KIND_SMALL : KIND_DEFAULT;
        this.setStatus(this.getStatus() & STATUS_OPEN | kind);
    }

    @Override
    @Nullable
    public Entity getOperator() {
        return this.getRiddenByEntity();
    }

    public boolean isOpen() {
        return (this.getStatus() & STATUS_OPEN) != 0;
    }

    public void setOpen(boolean open) {
        this.setStatus((open ? STATUS_OPEN : 0) | this.getStatus() & STATUS_KIND_MASK);
    }

    @Override
    public Vec3 getPos() {
        return this.position();
    }

    @Override
    public Vec3 launchPoint() {
        return new Vec3(this.getX() + this.uavOffset.getX(), this.getY() + this.uavOffset.getY(),
                this.getZ() + this.uavOffset.getZ());
    }

    @Override
    @Nullable
    public AircraftEntity getControlled() {
        return this.controlAircraft;
    }

    @Override
    public void setControlled(@Nullable AircraftEntity aircraft) {
        this.controlAircraft = aircraft;
        if (aircraft != null && !aircraft.isRemoved()) {
            this.setLastControlAircraft(aircraft);
        }
    }

    public void setUavPosition(int x, int y, int z) {
        if (!this.level().isClientSide) {
            this.uavOffset = new BlockPos(x, y, z);
            this.entityData.set(UAV_POS, this.uavOffset);
        }
    }

    @Override
    public void setUavPosition(double x, double y, double z) {
        this.setUavPosition((int) x, (int) y, (int) z);
    }

    public void updateUavPosition() {
        this.uavOffset = this.entityData.get(UAV_POS);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("UavStatus", this.getStatus());
        nbt.putInt("PosUavX", this.uavOffset.getX());
        nbt.putInt("PosUavY", this.uavOffset.getY());
        nbt.putInt("PosUavZ", this.uavOffset.getZ());
        this.lastControlled.save(nbt);
        this.pairings.save(nbt);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setUavPosition(nbt.getInt("PosUavX"), nbt.getInt("PosUavY"), nbt.getInt("PosUavZ"));

        if (nbt.contains("UavStatus")) {
            this.setStatus(nbt.getInt("UavStatus"));
        } else {
            this.setType(StationType.DEFAULT);
        }
        this.lastControlled.load(nbt);
        this.pairings.load(nbt);
    }

    @Override
    public List<UUID> getPairedUavs() {
        return this.pairings.list();
    }

    @Override
    public boolean isPaired(UUID uav) {
        return this.pairings.contains(uav);
    }

    @Override
    public boolean pairUav(UUID uav) {
        if (uav == null) {
            return false;
        }
        if (this.pairings.contains(uav)) {
            return true;
        }
        if (!this.canPairMore()) {
            return false;
        }
        this.pairings.add(uav);
        return true;
    }

    @Override
    public boolean unpairUav(UUID uav) {
        boolean removed = this.pairings.remove(uav);
        // if the dropped UAV is the one currently controlled, sever the live link too
        if (removed && this.getControlled() instanceof AircraftEntity ac && ac.getUUID().equals(uav)) {
            ac.uavLink().link(null);
            this.setControlled(null);
        }
        return removed;
    }

    @Override
    public int getMaxPairedUavs() {
        return Math.max(0, McheliConfig.uav().maxPairedPerStation());
    }

    /** Default launch point: 12 blocks out to the front right of the station, 2 up. */
    public void initUavPosition() {
        int quadrant = (int) (Degrees.wrap360(this.getYRot() + 45.0F) / 90.0);
        int x = quadrant != 0 && quadrant != 3 ? -12 : 12;
        int z = quadrant != 0 && quadrant != 1 ? -12 : 12;
        this.uavOffset = new BlockPos(x, 2, z);
        this.setUavPosition(x, 2, z);
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float damage) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (this.isRemoved() || this.level().isClientSide) {
            return true;
        }

        float finalDamage = DamageScaling.byExternal(this, source, damage);
        if (!FriendlyFire.allows(source, this)) {
            return false;
        }

        String sound = source.getEntity() instanceof Player ? "hit" : "helidmg";
        float pitch = sound.equals("hit") ? 1.0F : 0.9F + this.random.nextFloat() * 0.1F;
        this.playSound(ModSounds.getSound(sound), 1.0F, pitch);

        this.markHurt();

        if (finalDamage > 0.0F) {
            StationBreakage.breakBy(this, source);
        }

        return true;
    }

    @Override
    protected @NotNull MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    /** The small station's operator sits on the block in front, or lower when that block is not solid. */
    public double getMountedYOffset() {
        return OperatorSeat.mountedYOffset(this);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public void push(@NotNull Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        this.clientMotion.setVelocity(x, y, z);
    }

    @Override
    public void tick() {
        super.tick();

        this.prevCoverRotation = this.coverRotation;
        float delta = this.isOpen() ? 0.1F : -0.1F;
        this.coverRotation = Math.clamp(this.coverRotation + delta, 0.0F, 1.0F);

        Entity currentRider = this.getRiddenByEntity();
        if (currentRider == null) {
            if (this.lastRiddenByEntity != null) {
                this.unmountOperator();
            }
            this.setControlled(null);
        }

        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        // Only the server severs the link when the controlled aircraft dies. On the client a UAV is
        // often flagged removed merely because it left the tracking range; keeping the reference
        // stops the camera from snapping back to the operator while it is re-streamed. The operator
        // is released along with the link: cutting the link alone would leave them seated with a
        // frozen camera feed and no way out (control packets resolve no aircraft any more).
        if (!this.level().isClientSide && this.getControlled() instanceof AircraftEntity ac && ac.isRemoved()) {
            UavControl.disconnect(this);
        }

        this.lastControlled.dropRemoved();

        if (this.level().isClientSide) {
            this.clientMotion.tick();
            this.updateUavPosition();
        } else {
            this.tickServer();
        }

        this.lastRiddenByEntity = currentRider;
    }

    @Nullable
    public AircraftEntity getLastControlAircraft() {
        return this.lastControlled.get();
    }

    public void setLastControlAircraft(AircraftEntity ac) {
        this.lastControlled.set(ac);
    }

    public AircraftEntity getAndSearchLastControlAircraft() {
        return this.lastControlled.getOrFind();
    }

    public Integer getLastControlAircraftEntityId() {
        return this.lastControlled.entityId();
    }

    public void setLastControlAircraftEntityId(int id) {
        this.lastControlled.setEntityId(id);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.clientMotion.moveTo(x, y, z, yRot, xRot, steps);
    }

    private void tickServer() {
        if (this.tablet != null) {
            this.setDeltaMovement(Vec3.ZERO);
            if (this.getRiddenByEntity() == null && this.tickCount > 2) {
                // abandoned by its operator: the pairings go back to the tablet
                this.tablet.writeBack(this.level(), this.pairings.ids());
                this.setControlled(null);
                this.discard();
                return;
            }
        } else {
            Vec3 motion = new Vec3(0.0, this.getDeltaMovement().y - 0.03, 0.0);
            this.setDeltaMovement(motion);
            this.move(MoverType.SELF, motion);
            this.setDeltaMovement(0.0, this.getDeltaMovement().y * 0.96, 0.0);
        }
        this.setRot(this.getYRot(), this.getXRot());
        Entity riddenByEntity = this.getRiddenByEntity();
        if (riddenByEntity != null) {
            if (riddenByEntity.isRemoved()) {
                this.unmountOperator();
            } else {
                ItemStack item = this.getStackInSlot(0);
                if (!item.isEmpty()) {
                    this.handleItem(riddenByEntity, item);
                    if (item.getCount() == 0) {
                        this.setInventorySlotContents(0, ItemStack.EMPTY);
                    }
                }
            }
        }

        this.lastControlled.tickSearch();
    }

    @Override
    public boolean shouldBeSaved() {
        // transient tablet stations must never be saved to disk; they exist only while ridden
        return this.tablet == null && super.shouldBeSaved();
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (this.hasPassenger(passenger)) {
            Vec3 seat = OperatorSeat.riderPosition(this, passenger);
            callback.accept(passenger, seat.x, seat.y, seat.z);
        }
    }

    public void controlLastAircraft(Entity user) {
        AircraftEntity last = this.lastControlled.reconnectable();
        if (last != null) {
            last.uavLink().link(this);
            this.setControlled(last);
            if (user instanceof Player player) {
                player.closeContainer();
            }
        }
    }

    /** Server: launches the UAV the operator put into the station. */
    public void handleItem(@Nullable Entity user, ItemStack itemStack) {
        if (user == null || user.isRemoved() || itemStack.isEmpty() || itemStack.getCount() != 1
                || this.level().isClientSide) {
            return;
        }
        UavLaunch.launch(this, user, itemStack);
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || this.getVehicle() != null) {
            return InteractionResult.PASS;
        }

        if (this.getStationType() == StationType.SMALL) {
            if (player.isShiftKeyDown()) {
                this.setOpen(!this.isOpen());
                return InteractionResult.PASS;
            }

            if (!this.isOpen()) {
                return InteractionResult.PASS;
            }
        }

        this.lastRiddenByEntity = null;
        if (player instanceof ServerPlayer serverPlayer) {
            // ride the station to act as its operator (required for the UAV camera/control link),
            // then open the pairing/control screen
            player.startRiding(this);
            UavStationGuiFactory.INSTANCE.openGui(serverPlayer, this);
        }

        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /** Drops the operator and the link to the controlled UAV. */
    private void unmountOperator() {
        Entity operator = null;
        Entity riddenByEntity = this.getRiddenByEntity();
        if (riddenByEntity != null) {
            if (!this.level().isClientSide) {
                operator = riddenByEntity;
                riddenByEntity.stopRiding();
            }
        } else if (this.lastRiddenByEntity != null) {
            operator = this.lastRiddenByEntity;
        }

        if (this.getControlled() != null) {
            this.getControlled().uavLink().link(null);
        }

        this.setControlled(null);
        if (this.level().isClientSide) {
            if (operator instanceof Player player) {
                player.closeContainer();
            }
        }

        this.lastRiddenByEntity = null;
    }

    @Nullable
    @Override
    public Entity getRiddenByEntity() {
        List<Entity> passengers = this.getPassengers();
        return passengers.isEmpty() ? null : passengers.getFirst();
    }
}
