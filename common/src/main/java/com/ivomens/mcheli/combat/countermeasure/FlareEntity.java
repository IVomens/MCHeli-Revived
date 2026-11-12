package com.ivomens.mcheli.combat.countermeasure;

import com.ivomens.mcheli.combat.CombatEntityTypes;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import com.ivomens.mcheli.networking.SpawnDataEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * A burning flare that decoys heat seeking missiles. Also spawned client side only as burning
 * debris of explosions. Flares with a fuse burst into a smoke cloud when it runs out.
 */
public class FlareEntity extends McheliEntity implements SpawnDataEntity {

    private static final int MAX_AGE = 300;

    private double gravity = -0.013;
    private double airResistance = 0.992;
    private float size = 6.0F;
    /** Age at which the flare bursts into smoke, or 0 for none. */
    private int fuseCount;

    public double gravity() {
        return this.gravity;
    }

    public void setAirResistance(double airResistance) {
        this.airResistance = airResistance;
    }

    public void setGravity(double gravity) {
        this.gravity = gravity;
    }

    public FlareEntity(EntityType<? extends FlareEntity> type, Level level) {
        super(type, level);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public FlareEntity(Level level) {
        this(CombatEntityTypes.FLARE.get(), level);
    }

    public FlareEntity(Level level, double x, double y, double z, double mx, double my, double mz, float size, int fuseCount) {
        this(level);
        this.moveTo(x, y, z, 0.0F, 0.0F);
        this.setDeltaMovement(mx, my, mz);
        this.size = size;
        this.fuseCount = fuseCount;
    }

    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSq) {
        double range = this.getBoundingBox().getSize() * 4.0 * 64.0 * getViewScale();
        return distanceSq < range * range;
    }

    /** Client side effect-only flares (from explosions) have negative ids and are not server-driven. */
    private boolean isClientOnly() {
        return this.level().isClientSide && this.getId() < 0;
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        super.remove(reason);
        this.spawnFuseSmoke();
    }

    @Override
    public void onClientRemoval() {
        super.onClientRemoval();
        this.spawnFuseSmoke();
    }

    private void spawnFuseSmoke() {
        if (this.fuseCount > 0 && this.level().isClientSide) {
            // only once, whichever removal path comes first
            this.fuseCount = 0;
            for (int i = 0; i < 20; i++) {
                double x = (this.random.nextDouble() - 0.5) * 10.0;
                double y = (this.random.nextDouble() - 0.5) * 10.0;
                double z = (this.random.nextDouble() - 0.5) * 10.0;
                ParticleParams prm = new ParticleParams(this.level(), "smoke", this.getX() + x, this.getY() + y, this.getZ() + z);
                prm.setAge(200 + this.random.nextInt(100));
                prm.setSize(20 + this.random.nextInt(25));
                prm.setMotion((this.random.nextDouble() - 0.5) * 0.45, (this.random.nextDouble() - 0.5) * 0.01,
                        (this.random.nextDouble() - 0.5) * 0.45);
                float alpha = this.random.nextFloat() * 0.1F + 0.85F;
                float blue = this.random.nextFloat() * 0.2F + 0.5F;
                prm.setColor(alpha, blue + 0.1F, blue + 0.05F, blue);
                Effects.get().spawn(prm);
            }
        }
    }

    @Override
    public void startSeenByPlayer(@NotNull ServerPlayer player) {
        super.startSeenByPlayer(player);
        SpawnDataEntity.sendSpawnData(this, player);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(this.fuseCount);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        this.fuseCount = buffer.readByte();
    }

    @Override
    public void tick() {
        boolean authoritative = !this.level().isClientSide || this.isClientOnly();
        if (this.fuseCount > 0 && this.tickCount >= this.fuseCount) {
            this.discard();
        } else if (!this.level().isClientSide && !this.level().hasChunkAt(this.blockPosition())) {
            this.discard();
        } else if (this.tickCount > MAX_AGE && authoritative) {
            this.discard();
        } else {
            this.tickFlight(authoritative);
        }
    }

    private void tickFlight(boolean authoritative) {
        super.tick();
        Vec3 m = this.getDeltaMovement();
        if (authoritative) {
            this.checkBlockCollision(m);
            if (this.isRemoved()) return;
        }

        double px = this.getX() + m.x;
        double py = this.getY() + m.y;
        double pz = this.getZ() + m.z;
        if (this.level().isClientSide) {
            this.spawnTrail(px, py, pz);
        }

        this.setDeltaMovement(m.x * this.airResistance, m.y + this.gravity, m.z * this.airResistance);
        if (authoritative && (this.isInWater() || this.onGround())) {
            this.discard();
            return;
        }
        this.setPos(px, py, pz);
    }

    private void spawnTrail(double nextX, double nextY, double nextZ) {
        double stepX = (nextX - this.xo) / 2.0;
        double stepY = (nextY - this.yo) / 2.0;
        double stepZ = (nextZ - this.zo) / 2.0;
        for (int i = 0; i < 2; i++) {
            ParticleParams prm = new ParticleParams(this.level(), "smoke", this.xo + stepX * i, this.yo + stepY * i, this.zo + stepZ * i);
            prm.setSize(6.0F + this.random.nextFloat());
            if (this.size < 5.0F) {
                prm.setAlpha((float) (prm.alpha() * 0.75));
            }
            if (this.fuseCount > 0) {
                float alpha = this.random.nextFloat() * 0.1F + 0.85F;
                float blue = this.random.nextFloat() * 0.1F + 0.5F;
                prm.setColor(alpha, blue + 0.1F, blue + 0.05F, blue);
            }
            Effects.get().spawn(prm);
        }
    }

    /** Burns out on the first block in its path. */
    private void checkBlockCollision(Vec3 motion) {
        Vec3 from = this.position();
        Vec3 to = from.add(motion);
        if (!this.level().isClientSide && !this.level().hasChunkAt(BlockPos.containing(to))) {
            this.discard();
            return;
        }
        HitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        Vec3 m = this.getDeltaMovement();
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(m.x));
        list.add(DoubleTag.valueOf(m.y));
        list.add(DoubleTag.valueOf(m.z));
        tag.put("direction", list);
    }

    /** Flares are short lived and not restored from saves. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return 1.0F;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }

}
