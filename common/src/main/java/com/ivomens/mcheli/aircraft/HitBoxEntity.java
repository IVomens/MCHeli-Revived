package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.api.vehicle.VehicleHitBox;
import com.ivomens.mcheli.entity.McheliEntity;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/** Auxiliary hit box that forwards damage and interaction to its aircraft. */
public class HitBoxEntity extends McheliEntity implements VehicleHitBox {

    @Nullable
    private AircraftEntity parent;

    public HitBoxEntity(EntityType<? extends HitBoxEntity> type, Level level) {
        super(type, level);
        this.setDeltaMovement(Vec3.ZERO);
        this.noPhysics = true;
        this.setNoGravity(true);
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
        return this.parent != null && this.parent.hurt(source, amount);
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
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    /** Hit boxes are recreated by their aircraft. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        return this.parent != null && this.parent.boarding().interact(player, hand) ?
                InteractionResult.sidedSuccess(this.level().isClientSide) : InteractionResult.PASS;
    }

    @Override
    @Nullable
    public AircraftEntity owner() {
        return this.parent;
    }
}
