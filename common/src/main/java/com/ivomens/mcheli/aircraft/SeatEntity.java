package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.api.vehicle.VehicleSeat;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.entity.SinglePassenger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A crew seat of an aircraft. Players and mobs ride the seat; the seat itself is not a passenger of
 * the aircraft but is positioned by it every tick ({@link AircraftEntity#updateSeatsPosition}),
 * so server position packets for seats are ignored on the client.
 * <p>
 * Riders are never made invisible here: whether a crew member is drawn is decided by the renderer
 * from the aircraft info.
 */
public class SeatEntity extends McheliEntity implements SinglePassenger, VehicleSeat {

    /** A seat whose aircraft has been missing for this long (a minute) removes itself. */
    private static final int MAX_PARENT_SEARCH_TICKS = 1200;

    private String parentUniqueID = "";
    private int seatID = -1;
    private int parentSearchTicks;
    private Entity lastRiddenByEntity;
    private AircraftEntity parent;

    public String parentUniqueID() {
        return this.parentUniqueID;
    }

    public int seatID() {
        return this.seatID;
    }

    public void setParentUniqueID(String parentUniqueID) {
        this.parentUniqueID = parentUniqueID;
    }

    public void setSeatID(int seatID) {
        this.seatID = seatID;
    }

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.setDeltaMovement(Vec3.ZERO);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public SeatEntity(Level level, double x, double y, double z) {
        this(AircraftEntityTypes.SEAT.get(), level);
        this.setPos(x, y + 1.0, z);
        this.xo = x;
        this.yo = y + 1.0;
        this.zo = z;
    }

    @Override
    protected @NotNull MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return this.getParent() != null && this.getParent().hurt(source, amount);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    /** The aircraft positions seats; server updates must not drag them around on the client. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
    }

    @Override
    public void tick() {
        AircraftEntity parent = this.getParent();
        if (parent != null && parent.rest().isResting() && this.getRiddenByEntity() == null && this.tickCount % 20 != 0) {
            // a parked, empty vehicle's seats have nothing to do
            return;
        }
        super.tick();
        this.fallDistance = 0.0F;
        Entity rider = this.getRiddenByEntity();
        if (rider != null) {
            rider.fallDistance = 0.0F;
        }
        AircraftEntity aircraft = this.getParent();
        if (aircraft != null) {
            if (this.lastRiddenByEntity == null && rider != null) {
                aircraft.seatChanges().onSeatTaken(this, rider);
            } else if (this.lastRiddenByEntity != null && rider == null) {
                aircraft.seatChanges().onSeatLeft(this, this.lastRiddenByEntity);
            }
        }
        this.checkDetachmentAndDelete();
        this.lastRiddenByEntity = this.getRiddenByEntity();
    }

    @Override
    protected void addPassenger(@NotNull Entity passenger) {
        AircraftEntity parent = this.getParent();
        if (parent != null) {
            parent.rest().wake();
        }
        super.addPassenger(passenger);
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (this.hasPassenger(passenger)) {
            callback.accept(passenger, this.getX(), this.getY(), this.getZ());
        }
    }

    public void updatePosition(@Nullable Entity rider) {
        if (rider != null) {
            rider.setPos(this.getX(), this.getY(), this.getZ());
            rider.setDeltaMovement(Vec3.ZERO);
        }
    }

    public void updateRotation(@Nullable Entity rider, float yaw, float pitch) {
        if (rider != null) {
            rider.setYRot(yaw);
            rider.setXRot(pitch);
        }
    }

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        AircraftEntity aircraft = this.getParent();
        if (aircraft != null) {
            Vec3 position = aircraft.crewExit().getUnmountPosition(passenger, aircraft.seats().info(this.seatID + 1));
            if (position != null) {
                return position;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    /**
     * A seat without its aircraft waits a minute for it (it may still be loading), then removes
     * itself. If the aircraft was destroyed rather than unloaded, the seat goes right away.
     */
    protected void checkDetachmentAndDelete() {
        AircraftEntity aircraft = this.getParent();
        if (this.isRemoved() || this.seatID >= 0 && aircraft != null && !aircraft.isRemoved()) {
            this.parentSearchTicks = 0;
            return;
        }
        boolean parentDestroyed = aircraft != null && aircraft.getRemovalReason() != null
                && aircraft.getRemovalReason().shouldDestroy();
        if (parentDestroyed || this.parentSearchTicks >= MAX_PARENT_SEARCH_TICKS) {
            if (!this.level().isClientSide) {
                this.ejectPassengers();
            }
            this.discard();
            this.setParent(null);
        } else {
            this.parentSearchTicks++;
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putInt("SeatID", this.seatID);
        tag.putString("ParentUniqueID", this.parentUniqueID != null ? this.parentUniqueID : "");
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        this.seatID = tag.getInt("SeatID");
        this.parentUniqueID = tag.getString("ParentUniqueID");
    }

    /** Crew seats can be boarded directly; racks only take cargo. */
    public boolean canRideMob(Entity entity) {
        return this.getParent() != null && this.seatID >= 0
                && !(this.getParent().seats().info(this.seatID + 1) instanceof SeatRackInfo);
    }

    public boolean isGunnerMode() {
        Entity riddenByEntity = this.getRiddenByEntity();
        return riddenByEntity != null && this.getParent() != null && this.getParent().modes().usesGunnerView(riddenByEntity);
    }

    public boolean processInitialInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        AircraftEntity aircraft = this.getParent();
        if (aircraft == null || aircraft.isDestroyed() || aircraft.seats().hasEnemyOf(player)) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof VehicleTool) {
            return aircraft.boarding().interact(player, hand);
        }
        if (this.getRiddenByEntity() != null || player.getVehicle() != null || !this.canRideMob(player)) {
            return false;
        }
        if (!aircraft.boarding().admits(player, this.seatID + 1)) {
            return true;
        }
        if (!this.level().isClientSide) {
            player.startRiding(this);
        }
        return true;
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        return this.processInitialInteract(player, hand) ? InteractionResult.sidedSuccess(this.level().isClientSide) : InteractionResult.PASS;
    }

    @Nullable
    @Override
    public AircraftEntity getParent() {
        return this.parent;
    }

    public void setParent(AircraftEntity parent) {
        this.parent = parent;
        Entity rider = this.getRiddenByEntity();
        if (rider != null && parent != null) {
            parent.seatChanges().onSeatTaken(this, rider);
        }
    }

    @Nullable
    @Override
    public Entity getRiddenByEntity() {
        List<Entity> passengers = this.getPassengers();
        return passengers.isEmpty() ? null : passengers.getFirst();
    }
}
