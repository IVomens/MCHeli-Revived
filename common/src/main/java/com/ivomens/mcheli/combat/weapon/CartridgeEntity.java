package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.entity.McheliEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Spent cartridge case ejected by guns. Purely a client side effect entity: it is created and ticked only
 * on the client (see {@link SpentCases}) and never saved or synced. It flies with drag and gravity,
 * bounces off blocks and tumbles on impact.
 */
public class CartridgeEntity extends McheliEntity {

    private static final float ROTATION_STEP = 10.0F;
    private static final double AIR_DRAG = 0.98;

    private String textureName = "";
    private float bound;
    private float gravity;
    private float scale = 1.0F;
    private float targetYaw;
    private float targetPitch;
    private int countOnUpdate;

    public CartridgeEntity(EntityType<? extends CartridgeEntity> type, Level level) {
        super(type, level);
    }

    /** A case of the cartridge at the position, moving and facing as given. */
    public CartridgeEntity(EntityType<? extends CartridgeEntity> type, Level level, Cartridge cartridge, Vec3 pos,
                           Vec3 motion, float yaw, float pitch) {
        this(type, level);
        this.absMoveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        this.setDeltaMovement(motion);
        this.textureName = cartridge.name;
        this.bound = cartridge.bound;
        this.gravity = cartridge.gravity;
        this.scale = cartridge.scale;
        this.yRotO = yaw;
        this.setYRot(yaw);
        this.targetYaw = yaw;
        this.xRotO = pitch;
        this.setXRot(pitch);
        this.targetPitch = pitch;
    }

    /** The name of the case's model and texture under {@code bullets}. */
    public String textureName() {
        return this.textureName;
    }

    public float getScale() {
        return this.scale;
    }

    @Override
    public void tick() {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        if (this.countOnUpdate < McheliConfig.render().aliveTimeOfCartridge()) {
            this.countOnUpdate++;
        } else {
            this.discard();
            return;
        }

        Vec3 m = this.getDeltaMovement();
        this.setDeltaMovement(m.x * AIR_DRAG, m.y + this.gravity, m.z * AIR_DRAG);
        this.move();
    }

    /** Turns towards the target rotation by a fixed step per tick. */
    private void rotateTowardsTarget() {
        float yaw = this.getYRot();
        if (yaw < this.targetYaw - 3.0F) {
            yaw = Math.min(yaw + ROTATION_STEP, this.targetYaw);
        } else if (yaw > this.targetYaw + 3.0F) {
            yaw = Math.max(yaw - ROTATION_STEP, this.targetYaw);
        }
        float pitch = this.getXRot();
        if (pitch < this.targetPitch) {
            pitch = Math.min(pitch + ROTATION_STEP, this.targetPitch);
        } else if (pitch > this.targetPitch) {
            pitch = Math.max(pitch - ROTATION_STEP, this.targetPitch);
        }
        this.setYRot(yaw);
        this.setXRot(pitch);
    }

    /** Moves one tick, bouncing off blocks and tumbling on impact. */
    private void move() {
        Vec3 m = this.getDeltaMovement();
        Vec3 from = this.position();
        BlockHitResult hit = this.level().clip(new ClipContext(from, from.add(m.x, m.y, m.z), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        double speed = Math.max(Math.max(Math.abs(m.x), Math.abs(m.y)), Math.abs(m.z));
        if (hit.getType() == HitResult.Type.BLOCK) {
            bounce(hit, m, speed);
        } else {
            this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
            if (speed > 0.05F) {
                this.rotateTowardsTarget();
            }
        }
    }

    /** Bounces off the block face that was hit, scattering a little and tumbling with the impact speed. */
    private void bounce(BlockHitResult hit, Vec3 motion, double speed) {
        double px = hit.getLocation().x;
        double py = hit.getLocation().y;
        double pz = hit.getLocation().z;
        double mx = motion.x + speed * (this.random.nextFloat() - 0.5F) * 0.1F;
        double my = motion.y + speed * (this.random.nextFloat() - 0.5F) * 0.1F;
        double mz = motion.z + speed * (this.random.nextFloat() - 0.5F) * 0.1F;
        if (speed > 0.1F) {
            this.targetYaw += (float) (speed * (this.random.nextFloat() - 0.5F) * 720.0);
            this.targetPitch = (float) (speed * (this.random.nextFloat() - 0.5F) * 720.0);
        } else {
            this.targetPitch = 0.0F;
        }

        Direction face = hit.getDirection();
        switch (face) {
            case DOWN -> {
                if (my > 0.0) my = -my * this.bound;
            }
            case UP -> {
                if (my < 0.0) my = -my * this.bound;
                this.targetPitch *= 0.3F;
            }
            case NORTH -> {
                if (mz > 0.0) mz = -mz * this.bound;
                else pz += mz;
            }
            case SOUTH -> {
                if (mz < 0.0) mz = -mz * this.bound;
                else pz += mz;
            }
            case WEST -> {
                if (mx > 0.0) mx = -mx * this.bound;
                else px += mx;
            }
            case EAST -> {
                if (mx < 0.0) mx = -mx * this.bound;
                else px += mx;
            }
        }
        this.setPos(px, py, pz);
        this.setDeltaMovement(mx, my, mz);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {}
}
