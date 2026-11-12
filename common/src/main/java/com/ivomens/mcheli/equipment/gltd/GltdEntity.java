package com.ivomens.mcheli.equipment.gltd;

import com.ivomens.mcheli.aircraft.AircraftCamera;
import com.ivomens.mcheli.aircraft.FriendlyFire;
import com.ivomens.mcheli.combat.damage.DamageScaling;
import com.ivomens.mcheli.combat.weapon.fire.CloseAirSupport;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.entity.SinglePassenger;
import com.ivomens.mcheli.equipment.EquipmentEntityTypes;
import com.ivomens.mcheli.registry.McheliItems;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Ground laser target designator: a tripod the player sits behind to mark targets for an A-10 gun run.
 */
public class GltdEntity extends McheliEntity implements SinglePassenger {

    public static final float Y_OFFSET = 0.25F;
    private static final EntityDataAccessor<Integer> TIME_SINCE_HIT = SynchedEntityData.defineId(GltdEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DAMAGE_TAKEN = SynchedEntityData.defineId(GltdEntity.class,
            EntityDataSerializers.INT);
    /** Half size of the culling box while someone is aiming: the laser and the strike marker reach far out. */
    private static final double AIMING_CULL_SIZE = 150.0;
    private static final String CAS_WEAPON = "a10gau8";
    /** Name of the strike on the designator's screen. */
    public static final String STRIKE_NAME = "A-10 GAU-8 Avenger";
    /** Ticks between strikes, close to an hour; the client counts ten fewer and pads them itself. */
    private static final int STRIKE_COOLDOWN = 65386;
    private static final int CLIENT_STRIKE_COOLDOWN = 65376;
    private static final int STRIKE_POWER = 32;
    private static final int STRIKE_EXPLOSION = 2;
    private static final float STRIKE_SPEED = 4.0F;
    /** Accumulated damage (x100) that breaks the tripod. */
    private static final float BREAK_DAMAGE = 40.0F;
    private static final double MAX_SPEED = 0.35;
    private static final float MIN_ZOOM = 1.0F;
    private static final float MAX_ZOOM = 10.0F;
    /** Ticks a missing rider is searched for before the designator gives up and dismounts them. */
    private static final int RIDER_RETRY_TICKS = 3;

    public final AircraftCamera camera;
    /** The designator's air strike: an A-10 with the GAU-8, whatever the a10gau8 definition says. */
    private final CloseAirSupport strike = new CloseAirSupport(CAS_WEAPON, STRIKE_POWER, STRIKE_EXPLOSION, STRIKE_SPEED);
    /**
     * Ticks until the next strike. Positive on the server; the client counts a negative copy back to zero so
     * the screen can show the wait.
     */
    private int countWait;
    /** Set by the renderer once the player looked through the scope. */
    private boolean isUsedPlayer;
    private float renderRotationYaw;
    private float renderRotationPitch;
    private Entity lastRiddenByEntity;
    private int retryRiddenByEntityCheck;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYaw;
    private double lerpPitch;
    private double velocityX;
    private double velocityY;
    private double velocityZ;

    public boolean isUsedPlayer() {
        return this.isUsedPlayer;
    }

    public float renderRotationPitch() {
        return this.renderRotationPitch;
    }

    public float renderRotationYaw() {
        return this.renderRotationYaw;
    }

    /** Ticks until the next strike may be called; negative while a strike is running. */
    public int countWait() {
        return this.countWait;
    }

    public void setIsUsedPlayer(boolean isUsedPlayer) {
        this.isUsedPlayer = isUsedPlayer;
    }

    public void setRenderRotationPitch(float renderRotationPitch) {
        this.renderRotationPitch = renderRotationPitch;
    }

    public void setRenderRotationYaw(float renderRotationYaw) {
        this.renderRotationYaw = renderRotationYaw;
    }

    public GltdEntity(EntityType<? extends GltdEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.camera = new AircraftCamera(level, this);
        this.setRenderDistanceWeight(2.0);
    }

    public GltdEntity(Level level) {
        this(EquipmentEntityTypes.GLTD.get(), level);
    }

    public GltdEntity(Level level, double x, double y, double z) {
        this(level);
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.camera.setPosition(x, y, z);
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(TIME_SINCE_HIT, 0);
        builder.define(DAMAGE_TAKEN, 0);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    public double getMountedYOffset() {
        return -0.3;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float damage) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (level().isClientSide || isRemoved()) {
            return true;
        }

        damage = DamageScaling.byExternal(this, source, damage);
        if (!FriendlyFire.allows(source, this)) {
            return false;
        }

        setTimeSinceHit(10);
        setDamageTaken((int) (getDamageTaken() + damage * 100.0F));
        markHurt();
        boolean creative = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
        if (creative || getDamageTaken() > BREAK_DAMAGE) {
            Entity rider = getRiddenByEntity();
            camera.initCamera(0, rider);
            if (rider != null) {
                rider.stopRiding();
            }
            if (!creative) {
                spawnAtLocation(new ItemStack(McheliItems.GLTD.get()), 0.0F);
            }
            ModSounds.playSound(level(), getX(), getY(), getZ(), Ids.MOD_ID + ":hit", 1.0F, 1.0F);
            discard();
        }
        return true;
    }

    @Override
    public void animateHurt(float yaw) {
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpSteps = steps + 5;
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yRot;
        lerpPitch = xRot;
        setDeltaMovement(velocityX, velocityY, velocityZ);
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        velocityX = x;
        velocityY = y;
        velocityZ = z;
        setDeltaMovement(x, y, z);
    }

    @Override
    public void tick() {
        super.tick();
        if (getTimeSinceHit() > 0) {
            setTimeSinceHit(getTimeSinceHit() - 1);
        }
        if (getDamageTaken() > 0.0F) {
            setDamageTaken(getDamageTaken() - 1);
        }

        xo = getX();
        yo = getY();
        zo = getZ();
        Entity rider = getRiddenByEntity();
        if (rider != null) {
            camera.updateViewer(0, rider);
        }

        if (!level().isClientSide) {
            tickServerMovement();
        } else if (lerpSteps > 0) {
            interpolateToServerPosition();
        } else {
            Vec3 motion = getDeltaMovement();
            setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
            double factor = onGround() ? 0.5 : 1.0;
            setDeltaMovement(motion.x * factor * 0.99, motion.y * factor * 0.95, motion.z * factor * 0.99);
        }

        updateCameraPosition(false);
        if (countWait > 0) {
            countWait--;
        }
        if (countWait < 0) {
            countWait++;
        }
        strike.advance(level().isClientSide, countWait);
        trackRider();
    }

    private void tickServerMovement() {
        Vec3 startMotion = getDeltaMovement();
        double motionX = startMotion.x;
        double motionY = startMotion.y - 0.04;
        double motionZ = startMotion.z;
        double speed = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (speed > MAX_SPEED) {
            motionX *= MAX_SPEED / speed;
            motionZ *= MAX_SPEED / speed;
        }
        if (onGround()) {
            motionX *= 0.5;
            motionY *= 0.5;
            motionZ *= 0.5;
        }

        Vec3 motion = new Vec3(motionX, motionY, motionZ);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        Vec3 moved = getDeltaMovement();
        setDeltaMovement(moved.x * 0.99, moved.y * 0.95, moved.z * 0.99);
        turnTowardsMovement();

        if (McheliConfig.combat().collisionDestroyBlock()) {
            clearSnowUnderLegs();
        }
        Entity rider = getRiddenByEntity();
        if (rider != null && rider.isRemoved()) {
            rider.stopRiding();
        }
    }

    /** A sliding tripod slowly swings around to face where it is going. */
    private void turnTowardsMovement() {
        float yaw = getYRot();
        double targetYaw = yaw;
        double dx = xo - getX();
        double dz = zo - getZ();
        if (dx * dx + dz * dz > 0.001) {
            targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI);
        }
        double turn = Mth.clamp(Mth.wrapDegrees(targetYaw - yaw), -20.0, 20.0);
        setRot((float) (yaw + turn), 0.0F);
    }

    private void clearSnowUnderLegs() {
        for (int leg = 0; leg < 4; leg++) {
            int x = Mth.floor(getX() + (leg % 2 - 0.5) * 0.8);
            int z = Mth.floor(getZ() + ((double) leg / 2D - 0.5) * 0.8);
            for (int dy = 0; dy < 2; dy++) {
                int y = Mth.floor(getY()) + dy;
                if (level().getBlockState(new BlockPos(x, y, z)).is(Blocks.SNOW)) {
                    level().removeBlock(new BlockPos(x, y, z), false);
                }
            }
        }
    }

    private void interpolateToServerPosition() {
        double x = getX() + (lerpX - getX()) / lerpSteps;
        double y = getY() + (lerpY - getY()) / lerpSteps;
        double z = getZ() + (lerpZ - getZ()) / lerpSteps;
        double yawChange = Mth.wrapDegrees(lerpYaw - getYRot());
        float yaw = (float) (getYRot() + yawChange / lerpSteps);
        float pitch = (float) (getXRot() + (lerpPitch - getXRot()) / lerpSteps);
        lerpSteps--;
        setPos(x, y, z);
        setRot(yaw, pitch);
    }

    /** A rider can briefly disappear while it is being synced; only dismount after a few ticks without one. */
    private void trackRider() {
        Entity rider = getRiddenByEntity();
        if (lastRiddenByEntity == null || rider != null) {
            retryRiddenByEntityCheck = 0;
        } else if (retryRiddenByEntityCheck < RIDER_RETRY_TICKS) {
            retryRiddenByEntityCheck++;
            placeDismountedRider(lastRiddenByEntity);
        } else {
            unmountEntity();
        }

        rider = getRiddenByEntity();
        if (rider != null) {
            lastRiddenByEntity = rider;
        }
    }

    /** Players sit lower than their mount point. */
    private static double riderFeetOffset(Entity entity) {
        return entity instanceof Player ? -0.35 : 0.0;
    }

    /** Puts a dismounted rider behind the tripod. */
    private void placeDismountedRider(Entity entity) {
        if (entity == null) {
            return;
        }
        float yaw = getYRot();
        double dx = Math.sin(yaw * Math.PI / 180.0) * 1.2;
        double dz = -Math.cos(yaw * Math.PI / 180.0) * 1.2;
        entity.setPos(getX() + dx, getY() + getMountedYOffset() + riderFeetOffset(entity) + 1.0, getZ() + dz);
        entity.xOld = entity.xo = entity.getX();
        entity.yOld = entity.yo = entity.getY();
        entity.zOld = entity.zo = entity.getZ();
    }

    public void unmountEntity() {
        camera.setMode(0, 0);
        camera.setCameraZoom(1.0F);
        if (!level().isClientSide) {
            Entity rider = getRiddenByEntity();
            if (rider != null) {
                if (!rider.isRemoved()) {
                    rider.stopRiding();
                }
            } else if (lastRiddenByEntity != null && !lastRiddenByEntity.isRemoved()) {
                camera.updateViewer(0, lastRiddenByEntity);
                placeDismountedRider(lastRiddenByEntity);
            }
        }
        lastRiddenByEntity = null;
    }

    /** The scope sits 0.6 blocks in front of the tripod's center. */
    private void updateCameraPosition(boolean force) {
        if (force || getRiddenByEntity() != null) {
            double x = -Math.sin(getYRot() * Math.PI / 180.0) * 0.6;
            double z = Math.cos(getYRot() * Math.PI / 180.0) * 0.6;
            camera.setPosition(getX() + x, getY() + 0.7, getZ() + z);
        }
    }

    public void zoomCamera(float change) {
        camera.setCameraZoom(Mth.clamp(camera.getCameraZoom() + change, MIN_ZOOM, MAX_ZOOM));
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (!hasPassenger(passenger)) {
            return;
        }
        double x = Math.sin(getYRot() * Math.PI / 180.0) * 0.5;
        double z = -Math.cos(getYRot() * Math.PI / 180.0) * 0.5;
        double y = getY() + getMountedYOffset() - passenger.getVehicleAttachmentPoint(this).y +
                McheliEntity.GLOBAL_SEAT_OFFSET;
        callback.accept(passenger, getX() + x, y, getZ() + z);
    }

    /** The designator has a single weapon; the control packet still carries the switch request. */
    public void switchWeapon(int id) {
    }

    /** Calls in a strike at the point the scope is aimed at. */
    public boolean useCurrentWeapon(int option1, int option2) {
        Entity rider = getRiddenByEntity();
        if (countWait != 0 || rider == null ||
                !strike.designate(rider, null, new Vec3(camera.posX(), camera.posY(), camera.posZ()))) {
            return false;
        }

        countWait = level().isClientSide ? CLIENT_STRIKE_COOLDOWN : STRIKE_COOLDOWN;
        if (level().isClientSide) {
            // a little slack so the client never shows the gun ready before the server is
            countWait += countWait > 0 ? 10 : -10;
        } else {
            ModSounds.playSound(level(), getX(), getY(), getZ(), Ids.MOD_ID + ":gltd", 0.5F, 1.0F);
        }
        return true;
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        Entity rider = getRiddenByEntity();
        if (!(rider instanceof Player) || rider == player) {
            player.setYRot(Mth.wrapDegrees(getYRot()));
            player.setXRot(Mth.wrapDegrees(getXRot()));
            if (!level().isClientSide) {
                player.startRiding(this);
            } else {
                camera.setCameraZoom(1.0F);
                // show at least a three second warm-up after boarding
                if (countWait > 0) {
                    countWait = -countWait;
                }
                if (countWait > -60) {
                    countWait = -60;
                }
            }
            updateCameraPosition(true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public int getDamageTaken() {
        return entityData.get(DAMAGE_TAKEN);
    }

    public void setDamageTaken(int damage) {
        entityData.set(DAMAGE_TAKEN, damage);
    }

    public int getTimeSinceHit() {
        return entityData.get(TIME_SINCE_HIT);
    }

    public void setTimeSinceHit(int ticks) {
        entityData.set(TIME_SINCE_HIT, ticks);
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        if (getRiddenByEntity() != null) {
            return getBoundingBox().inflate(AIMING_CULL_SIZE);
        }
        return super.getBoundingBoxForCulling();
    }

    @Nullable
    @Override
    public Entity getRiddenByEntity() {
        List<Entity> passengers = getPassengers();
        return passengers.isEmpty() ? null : passengers.getFirst();
    }
}
