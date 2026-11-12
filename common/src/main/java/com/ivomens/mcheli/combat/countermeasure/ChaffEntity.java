package com.ivomens.mcheli.combat.countermeasure;

import com.ivomens.mcheli.combat.CombatEntityTypes;
import com.ivomens.mcheli.entity.McheliEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/** A slowly falling chaff cloud that radar guided missiles may lock instead of the aircraft. */
public class ChaffEntity extends McheliEntity {

    public static final int MAX_TICK_EXISTED = 120;
    private double gravity = -0.001;
    private double airResistance = 0.99;

    public ChaffEntity(EntityType<? extends ChaffEntity> type, Level level) {
        super(type, level);
        this.setRenderDistanceWeight(500.0);
    }

    public ChaffEntity(Level level) {
        this(CombatEntityTypes.CHAFF.get(), level);
    }

    public ChaffEntity(Level level, double x, double y, double z, double mx, double my, double mz) {
        this(level);
        this.moveTo(x, y, z, 0.0F, 0.0F);
        this.setDeltaMovement(mx, my, mz);
    }

    @Override
    public void tick() {
        if (this.tickCount > MAX_TICK_EXISTED) {
            if (!this.level().isClientSide) this.discard();
            return;
        }

        if (!this.level().isClientSide && !this.level().hasChunkAt(this.blockPosition())) {
            this.discard();
            return;
        }

        super.tick();

        Vec3 m = this.getDeltaMovement();
        if (!this.level().isClientSide) {
            this.checkBlockCollision(m);
            if (this.isRemoved()) return;
        }

        double px = this.getX() + m.x;
        double py = this.getY() + m.y;
        double pz = this.getZ() + m.z;

        this.setDeltaMovement(m.x * this.airResistance, m.y + this.gravity, m.z * this.airResistance);

        if (!this.level().isClientSide && (this.isInWater() || this.onGround())) {
            this.discard();
            return;
        }

        this.setPos(px, py, pz);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double size = this.getBoundingBox().getSize() * 4.0;
        if (Double.isNaN(size)) size = 4.0;
        double range = size * 64.0 * getViewScale();
        return distance < range * range;
    }


    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compound) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compound) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /** Disappears on the first block in its path, or at unloaded chunks. */
    private void checkBlockCollision(Vec3 motion) {
        Vec3 start = this.position();
        Vec3 end = start.add(motion);
        if (!this.level().hasChunkAt(BlockPos.containing(end))) {
            this.discard();
            return;
        }

        HitResult result = this.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (result.getType() != HitResult.Type.MISS) {
            this.discard();
        }
    }
}
