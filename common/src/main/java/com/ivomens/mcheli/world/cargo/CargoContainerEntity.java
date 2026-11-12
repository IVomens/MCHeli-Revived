package com.ivomens.mcheli.world.cargo;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.FriendlyFire;
import com.ivomens.mcheli.aircraft.IRideableAboard;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatRackInfo;
import com.ivomens.mcheli.combat.damage.DamageScaling;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.entity.InventoryEntity;
import com.ivomens.mcheli.registry.McheliItems;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.world.WorldEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;


/** Placeable cargo container (54 slots) that can be carried on aircraft racks. */
public class CargoContainerEntity extends InventoryEntity implements IRideableAboard {

    public static final float Y_OFFSET = 0.5F;
    private static final EntityDataAccessor<Integer> TIME_SINCE_HIT = SynchedEntityData.defineId(CargoContainerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FORWARD_DIR = SynchedEntityData.defineId(CargoContainerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DAMAGE_TAKEN = SynchedEntityData.defineId(CargoContainerEntity.class, EntityDataSerializers.INT);
    private static final int SLOT_COUNT = 54;
    /** Damage (in twentieths) a container takes before it breaks. */
    private static final float BREAK_DAMAGE = 40.0F;
    private static final double MAX_SPEED = 0.35;
    /** Number of horizontal slices checked for buoyancy. */
    private static final int BUOYANCY_SLICES = 5;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYaw;
    private double lerpPitch;
    private double velocityX;
    private double velocityY;
    private double velocityZ;

    public CargoContainerEntity(EntityType<? extends CargoContainerEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.setRenderDistanceWeight(2.0);
    }

    public CargoContainerEntity(Level level) {
        this(WorldEntityTypes.CONTAINER.get(), level);
    }

    public CargoContainerEntity(Level level, double x, double y, double z) {
        this(level);
        this.setPos(x, y + 0.5, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    @Override
    public float maxUpStep() {
        return 0.6F;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected @NotNull MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIME_SINCE_HIT, 0);
        builder.define(FORWARD_DIR, 1);
        builder.define(DAMAGE_TAKEN, 0);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public @NotNull Component getName() {
        return Component.literal("Container ").append(super.getName());
    }

    public double getMountedYOffset() {
        return -0.3;
    }

    /** Only players can break containers by hitting them; creative players break them at once. */
    @Override
    public boolean hurt(@NotNull DamageSource source, float damage) {
        if (this.isInvulnerableTo(source) || this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        damage = DamageScaling.byExternal(this, source, damage);
        if (!FriendlyFire.allows(source, this) || !(source.getEntity() instanceof Player attacker)
                || !source.is(DamageTypes.PLAYER_ATTACK)) {
            return false;
        }
        this.playSound(ModSounds.getSound("hit"), 1.0F, 1.3F);
        this.setDamageTaken(this.getDamageTaken() + (int) (damage * 20.0F));
        this.setForwardDirection(-this.getForwardDirection());
        this.setTimeSinceHit(10);
        this.markHurt();
        boolean creative = attacker.getAbilities().instabuild;
        if (creative || this.getDamageTaken() > BREAK_DAMAGE) {
            if (!creative) {
                this.spawnAtLocation(new ItemStack(McheliItems.CONTAINER.get()), 0.0F);
            }
            this.discard();
        }
        return true;
    }

    @Override
    public void animateHurt(float yaw) {
        this.setForwardDirection(-this.getForwardDirection());
        this.setTimeSinceHit(10);
        this.setDamageTaken(this.getDamageTaken() * 11);
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
        if (this.getTimeSinceHit() > 0) {
            this.setTimeSinceHit(this.getTimeSinceHit() - 1);
        }

        if (this.getDamageTaken() > 0.0F) {
            this.setDamageTaken(this.getDamageTaken() - 1);
        }

        if (this.level().isClientSide) {
            this.tickClient();
        } else {
            this.tickServer();
        }
    }

    private void tickClient() {
        if (this.lerpSteps > 0) {
            double x = this.getX() + (this.lerpX - this.getX()) / this.lerpSteps;
            double y = this.getY() + (this.lerpY - this.getY()) / this.lerpSteps;
            double z = this.getZ() + (this.lerpZ - this.getZ()) / this.lerpSteps;
            double yawDiff = Mth.wrapDegrees(this.lerpYaw - this.getYRot());
            this.setYRot((float) (this.getYRot() + yawDiff / this.lerpSteps));
            this.setXRot((float) (this.getXRot() + (this.lerpPitch - this.getXRot()) / this.lerpSteps));
            this.lerpSteps--;
            this.setPos(x, y, z);
            return;
        }
        Vec3 m = this.getDeltaMovement();
        double motionX = m.x;
        double motionZ = m.z;
        this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
        if (this.onGround()) {
            motionX *= 0.9F;
            motionZ *= 0.9F;
        }
        this.setDeltaMovement(motionX * 0.99, m.y * 0.95, motionZ * 0.99);
    }

    private void tickServer() {
        Vec3 m = this.getDeltaMovement();
        double motionX = m.x;
        double motionY = m.y;
        double motionZ = m.z;

        // floats in water and lava, sinking until about half submerged
        double submerged = this.submergedFraction();
        if (submerged < 1.0) {
            motionY += 0.04 * (submerged * 2.0 - 1.0);
        } else {
            if (motionY < 0.0) {
                motionY /= 2.0;
            }
            motionY += 0.007;
        }

        double speed = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (speed > MAX_SPEED) {
            motionX *= MAX_SPEED / speed;
            motionZ *= MAX_SPEED / speed;
        }
        if (this.onGround()) {
            motionX *= 0.9F;
            motionZ *= 0.9F;
        }

        this.setDeltaMovement(motionX, motionY, motionZ);
        this.move(MoverType.SELF, this.getDeltaMovement());
        m = this.getDeltaMovement();
        this.setDeltaMovement(m.x * 0.99, m.y * 0.95, m.z * 0.99);
        this.setXRot(0.0F);
        this.turnIntoDrift();
        this.pushOtherContainers();
        if (McheliConfig.combat().collisionDestroyBlock()) {
            this.breakSnowAndLilyPads();
        }
    }

    private double submergedFraction() {
        AABB box = this.getBoundingBox();
        double height = box.maxY - box.minY;
        double fraction = 0.0;
        for (int i = 0; i < BUOYANCY_SLICES; i++) {
            double bottom = box.minY + height * i / BUOYANCY_SLICES - 0.125;
            double top = box.minY + height * (i + 1) / BUOYANCY_SLICES - 0.125;
            AABB slice = new AABB(box.minX, bottom, box.minZ, box.maxX, top, box.maxZ);
            if (this.level().getBlockStates(slice).anyMatch(state -> state.getFluidState().is(FluidTags.WATER) || state.getFluidState().is(FluidTags.LAVA))) {
                fraction += 1.0 / BUOYANCY_SLICES;
            }
        }
        return fraction;
    }

    /** Turns at most 5 degrees per tick towards the direction it drifts in. */
    private void turnIntoDrift() {
        double yaw = this.getYRot();
        double dx = this.xo - this.getX();
        double dz = this.zo - this.getZ();
        if (dx * dx + dz * dz > 0.001) {
            yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI);
        }
        double yawDiff = Mth.clamp(Mth.wrapDegrees(yaw - this.getYRot()), -5.0, 5.0);
        this.setYRot((float) (this.getYRot() + yawDiff));
    }

    private void pushOtherContainers() {
        for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(0.2, 0.0, 0.2),
                e -> e instanceof CargoContainerEntity && e.isPushable())) {
            entity.push(this);
        }
    }

    /** Clears snow layers and lily pads under the four corners. */
    private void breakSnowAndLilyPads() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int corner = 0; corner < 4; corner++) {
            int x = Mth.floor(this.getX() + (corner % 2 - 0.5) * 0.8);
            int z = Mth.floor(this.getZ() + ((double) corner / 2.0 - 0.5) * 0.8);
            for (int dy = 0; dy < 2; dy++) {
                pos.set(x, Mth.floor(this.getY()) + dy, z);
                BlockState state = this.level().getBlockState(pos);
                if (state.is(Blocks.SNOW)) {
                    this.level().removeBlock(pos, false);
                } else if (state.is(Blocks.LILY_PAD)) {
                    this.level().destroyBlock(pos.immutable(), true);
                }
            }
        }
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        this.displayInventory(player);
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    public int getDamageTaken() {
        return this.entityData.get(DAMAGE_TAKEN);
    }

    public void setDamageTaken(int damage) {
        this.entityData.set(DAMAGE_TAKEN, damage);
    }

    public int getTimeSinceHit() {
        return this.entityData.get(TIME_SINCE_HIT);
    }

    public void setTimeSinceHit(int ticks) {
        this.entityData.set(TIME_SINCE_HIT, ticks);
    }

    public int getForwardDirection() {
        return this.entityData.get(FORWARD_DIR);
    }

    public void setForwardDirection(int direction) {
        this.entityData.set(FORWARD_DIR, direction);
    }

    @Override
    public boolean canRideAircraft(AircraftEntity ac, int seatID, SeatRackInfo info) {
        for (String s : info.names) {
            if (s.equalsIgnoreCase("container")) {
                return ac.getVehicle() == null && this.getVehicle() == null;
            }
        }

        return false;
    }

    @Override
    public boolean isSkipNormalRender() {
        return this.getVehicle() instanceof SeatEntity;
    }
}
