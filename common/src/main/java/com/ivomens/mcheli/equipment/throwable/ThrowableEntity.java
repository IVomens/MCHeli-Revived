package com.ivomens.mcheli.equipment.throwable;

import com.ivomens.mcheli.combat.explosion.ExplosionSpec;
import com.ivomens.mcheli.combat.explosion.Explosions;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition;
import com.ivomens.mcheli.equipment.EquipmentEntityTypes;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import com.ivomens.mcheli.util.Color4f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Hand grenades and other throwables (smoke etc.) defined in {@code assets/mcheli/throwable}.
 * Bounces off blocks using the definition's {@code bound} factor; explosive ones go off when the
 * time fuse runs out, the others puff smoke from then on until they expire.
 */
public class ThrowableEntity extends ThrowableProjectile {

    private static final EntityDataAccessor<String> INFO_NAME = SynchedEntityData.defineId(ThrowableEntity.class, EntityDataSerializers.STRING);
    private static final float THROW_SPEED_SCALE = 0.4F;
    /** Ticks the client waits for the synced definition name before the server gives up on it. */
    private static final int MAX_TICKS_WITHOUT_INFO = 10;
    private static final float GROUND_FRICTION = 0.9F;

    /** Position at the start of the tick; a bounce restarts the movement from here. */
    private double boundPosX;
    private double boundPosY;
    private double boundPosZ;
    private HitResult lastImpact;
    private int noInfoCount;
    private int countOnUpdate;
    private ThrowableDefinition throwableInfo;

    public ThrowableEntity(EntityType<? extends ThrowableEntity> type, Level level) {
        super(type, level);
    }

    public ThrowableEntity(Level level) {
        this(EquipmentEntityTypes.THROWABLE.get(), level);
    }

    public ThrowableEntity(Level level, LivingEntity thrower, float acceleration) {
        super(EquipmentEntityTypes.THROWABLE.get(), thrower, level);
        this.setDeltaMovement(this.getDeltaMovement().scale(acceleration));
    }

    public ThrowableEntity(Level level, double x, double y, double z) {
        super(EquipmentEntityTypes.THROWABLE.get(), x, y, z, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(INFO_NAME, "");
    }

    /** Throws along the thrower's view with a fixed inaccuracy of 1. */
    @Override
    public void shootFromRotation(@NotNull Entity thrower, float pitch, float yaw, float pitchOffset, float velocity, float inaccuracy) {
        float yawRad = yaw / 180.0F * (float) Math.PI;
        float pitchRad = pitch / 180.0F * (float) Math.PI;
        double mx = -Mth.sin(yawRad) * Mth.cos(pitchRad) * THROW_SPEED_SCALE;
        double mz = Mth.cos(yawRad) * Mth.cos(pitchRad) * THROW_SPEED_SCALE;
        double my = -Mth.sin((pitch + pitchOffset) / 180.0F * (float) Math.PI) * THROW_SPEED_SCALE;
        this.shoot(mx, my, mz, velocity, 1.0F);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        this.boundPosX = this.getX();
        this.boundPosY = this.getY();
        this.boundPosZ = this.getZ();

        // never load terrain for a grenade flying out of the loaded area
        if (!this.level().isClientSide) {
            Vec3 m = this.getDeltaMovement();
            if (!this.level().hasChunkAt(BlockPos.containing(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z))) {
                this.discard();
                return;
            }
        }

        this.applyDefinitionGravity();
        super.tick();
        if (this.isRemoved()) return;

        if (this.lastImpact != null) {
            this.bounce(this.lastImpact);
            Vec3 m = this.getDeltaMovement();
            this.setPos(this.boundPosX + m.x, this.boundPosY + m.y, this.boundPosZ + m.z);
            this.lastImpact = null;
        }

        if (++this.countOnUpdate >= Integer.MAX_VALUE - 15) {
            this.discard();
            return;
        }
        if (!this.resolveInfo()) {
            return;
        }

        ThrowableDefinition info = this.info();
        if (!this.level().isClientSide) {
            if (this.countOnUpdate == info.timeFuse() && info.explosion() > 0) {
                this.explode(info);
                this.discard();
                return;
            }
            if (this.countOnUpdate >= info.aliveTime()) {
                this.discard();
            }
        } else if (this.countOnUpdate >= info.timeFuse() && info.explosion() <= 0) {
            this.spawnSmoke(info);
        }
    }

    private void applyDefinitionGravity() {
        ThrowableDefinition info = this.info();
        if (info == null) {
            return;
        }
        BlockPos pos = BlockPos.containing((int) (this.getX() + 0.5), (int) this.getY(), (int) (this.getZ() + 0.5));
        float gravity = this.level().getFluidState(pos).is(FluidTags.WATER) ? info.gravityInWater() : info.gravity();
        Vec3 m = this.getDeltaMovement();
        this.setDeltaMovement(m.x, m.y + gravity, m.z);
    }

    /** Looks up the definition from the synced name if needed. Returns false while it is unknown. */
    private boolean resolveInfo() {
        if (this.info() != null) {
            return true;
        }
        String name = this.entityData.get(INFO_NAME);
        if (!name.isEmpty()) {
            this.setInfo(Throwables.get(name).orElse(null));
        }
        if (this.info() != null) {
            return true;
        }
        if (++this.noInfoCount > MAX_TICKS_WITHOUT_INFO && !this.level().isClientSide) {
            this.discard();
        }
        return false;
    }

    /** A grenade's explosion power sizes the blast, the block damage and the damage radius alike. */
    private void explode(ThrowableDefinition info) {
        float size = info.explosion();
        Explosions.detonate(this.level(), ExplosionSpec.at(this.position(), size).blockSize(size)
                .damagePower(info.explosion()).blockPower(size).damageRadius(size).destroyBlocks(true)
                .build(), null, null);
    }

    /** Client: smoke puffs in the definition's colour, keeping grey shades grey. */
    private void spawnSmoke(ThrowableDefinition info) {
        Color4f color = Throwables.smokeColor(info);
        for (int i = 0; i < info.smoke().count(); i++) {
            float r = color.r * 0.9F + this.random.nextFloat() * 0.1F;
            float g = color.g * 0.9F + this.random.nextFloat() * 0.1F;
            float b = color.b * 0.9F + this.random.nextFloat() * 0.1F;
            if (color.r == color.g) g = r;
            if (color.r == color.b) b = r;
            if (color.g == color.b) b = g;

            this.spawnSmokeTrail(4, info.smoke().size() + this.random.nextFloat() * info.smoke().size() / 3.0F, r, g, b,
                    info.smoke().velocity().horizontal() * (this.random.nextFloat() - 0.5F),
                    info.smoke().velocity().vertical() * this.random.nextFloat(),
                    info.smoke().velocity().horizontal() * (this.random.nextFloat() - 0.5F));
        }
    }

    private void spawnSmokeTrail(int count, float size, float r, float g, float b, float mx, float my, float mz) {
        double stepX = (this.getX() - this.xo) / count;
        double stepY = (this.getY() - this.yo) / count;
        double stepZ = (this.getZ() - this.zo) / count;
        for (int i = 0; i < count; i++) {
            ParticleParams prm = new ParticleParams(this.level(), "smoke", this.xo + stepX * i, 1.0 + this.yo + stepY * i, this.zo + stepZ * i);
            prm.setMotion(mx, my, mz);
            prm.setSize(size);
            prm.setColor(1.0F, r, g, b);
            prm.setWindBlown(true);
            prm.setTurnsWhite(true);
            Effects.get().spawn(prm);
        }
    }

    /** Bounces off the hit block face; on floors and ceilings it also loses some horizontal speed. */
    private void bounce(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return;
        }
        Direction side = blockHit.getDirection();
        float bound = this.info().bound();
        Vec3 v = this.getDeltaMovement();
        double mx = v.x;
        double my = v.y;
        double mz = v.z;
        switch (side) {
            case DOWN, UP -> {
                mx *= GROUND_FRICTION;
                mz *= GROUND_FRICTION;
                this.boundPosY = hit.getLocation().y;
                boolean movingIntoFace = (side == Direction.DOWN && my > 0.0) || (side == Direction.UP && my < 0.0);
                my = movingIntoFace ? -my * bound : 0.0;
            }
            case NORTH -> {
                if (mz > 0.0) mz = -mz * bound;
            }
            case SOUTH -> {
                if (mz < 0.0) mz = -mz * bound;
            }
            case WEST -> {
                if (mx > 0.0) mx = -mx * bound;
            }
            case EAST -> {
                if (mx < 0.0) mx = -mx * bound;
            }
        }
        this.setDeltaMovement(mx, my, mz);
    }

    @Override
    protected void onHit(@NotNull HitResult hit) {
        if (this.info() != null) {
            this.lastImpact = hit;
        }
    }

    @Nullable
    public ThrowableDefinition info() {
        return this.throwableInfo;
    }

    public void setInfo(ThrowableDefinition info) {
        this.throwableInfo = info;
        if (info != null && !this.level().isClientSide) {
            this.entityData.set(INFO_NAME, info.name());
        }
    }
}
