package com.ivomens.mcheli.combat.strike;

import com.ivomens.mcheli.combat.CombatEntityTypes;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.sound.WorldSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * The A-10 of a close air support strike: dives in over the target, strafes it with GAU-8 rounds
 * and climbs away.
 */
public class A10StrikeEntity extends McheliEntity {

    private static final EntityDataAccessor<String> WEAPON_NAME = SynchedEntityData.defineId(A10StrikeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDimensions SIZE = EntityDimensions.fixed(5.0F, 3.0F);

    public static final int DESPAWN_COUNT_MAX = 70;
    /** The aircraft is invisible and holds its position for this many ticks before the run. */
    private static final int RENDER_START_TICK = 20;
    private static final int ROUNDS_PER_RUN = 40;
    private static final int ROUNDS_PER_TICK = 2;
    /** Every fourth round is explosive. */
    private static final int EXPLOSIVE_ROUND_INTERVAL = 4;
    private static final double ROUND_SPACING = 0.6;
    private static final double ROUND_SPEED = 4.0;
    /** Removes saved strikes right away; they are not resumed after loading. */
    private static final int LOADED_DESPAWN_COUNT = 200;

    private int despawnCount;
    private int shotCount;

    @Nullable
    private Entity platform;
    @Nullable
    private Entity shooter;
    /** Flight direction: 0 south, 1 west, 2 north, 3 east. */
    private int direction;
    private int power = 32;
    private float speed = 4.0F;
    private int explosionPower = 1;
    @Nullable
    private WeaponSpec weaponSpec;

    public A10StrikeEntity(EntityType<? extends A10StrikeEntity> type, Level level) {
        super(type, level);
        this.refreshDimensions();
        this.setRenderDistanceWeight(this.renderDistanceWeight() * 10.0);
    }

    public A10StrikeEntity(Level level) {
        this(CombatEntityTypes.A10.get(), level);
    }

    public A10StrikeEntity(Level level, double x, double y, double z) {
        this(level);
        this.setPos(x, y, z);
        this.xo = this.xOld = x;
        this.yo = this.yOld = y;
        this.zo = this.zOld = z;
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        return SIZE;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(WEAPON_NAME, "");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compound) {
        compound.putString("WeaponName", this.weaponName());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compound) {
        this.despawnCount = LOADED_DESPAWN_COUNT;
        this.setWeaponName(compound.getString("WeaponName"));
    }

    /** Sets the run up: the gun's weapon, the flight direction, who called the strike and the gun's stats. */
    public void arm(String weaponName, int direction, Entity shooter, @Nullable Entity platform, int power,
                    int explosionPower, float speed) {
        this.setWeaponName(weaponName);
        this.setYRot(90.0F * direction);
        this.yRotO = this.getYRot();
        this.direction = direction;
        this.shooter = shooter;
        this.platform = platform;
        this.explosionPower = explosionPower;
        this.power = power;
        this.speed = speed;
    }

    private void setWeaponName(String name) {
        if (name == null || name.isEmpty()) return;

        this.weaponSpec = Weapons.get(name).orElse(null);
        if (this.weaponSpec != null && !this.level().isClientSide) {
            this.entityData.set(WEAPON_NAME, name);
        }
    }

    public String weaponName() {
        return this.entityData.get(WEAPON_NAME);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.isRemoved()) this.despawnCount++;

        if (this.weaponSpec == null) {
            this.setWeaponName(this.weaponName());
            if (this.weaponSpec == null) {
                if (!this.level().isClientSide) this.discard();
                return;
            }
        }

        if (this.level().isClientSide) {
            this.shotCount += 4;
        } else {
            this.updateServer();
        }

        this.updateMotion();
    }

    private void updateServer() {
        if (this.isRemoved()) return;

        if (this.despawnCount > DESPAWN_COUNT_MAX) {
            this.discard();
        } else if (this.despawnCount > 0 && this.shotCount < ROUNDS_PER_RUN) {
            for (int i = 0; i < ROUNDS_PER_TICK; i++) {
                this.fireRound(this.shotCount++);
            }
            if (this.shotCount == ROUNDS_PER_RUN - 2) {
                WorldSounds.playHeavySound(this.level(), this.getX(), this.getY(), this.getZ(), "mcheli:gau-8_snd", 150.0F, 1.0F);
            }
        }
    }

    private void updateMotion() {
        if (this.isRemoved()) return;

        Vec3 m = this.getDeltaMovement();
        if (this.despawnCount <= RENDER_START_TICK) {
            this.setDeltaMovement(m.x, -0.3, m.z);
        } else {
            this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
            this.setDeltaMovement(m.x, m.y + 0.02, m.z);
        }
    }

    /** Fires one round of the burst, spaced along the flight path and aimed steeply down ahead. */
    private void fireRound(int roundIndex) {
        float yaw = 90.0F * this.direction;
        float pitch = 30.0F;
        double offset = roundIndex * ROUND_SPACING;
        double spawnX = this.getX();
        double spawnZ = this.getZ();
        double dirX = this.random.nextDouble() - 0.5;
        double dirY = -2.6;
        double dirZ = this.random.nextDouble() - 0.5;
        switch (this.direction) {
            case 0 -> {
                dirZ += 10.0;
                spawnZ += offset;
            }
            case 1 -> {
                dirX -= 10.0;
                spawnX -= offset;
            }
            case 2 -> {
                dirZ -= 10.0;
                spawnZ -= offset;
            }
            case 3 -> {
                dirX += 10.0;
                spawnX += offset;
            }
        }

        double length = Mth.sqrt((float) (dirX * dirX + dirY * dirY + dirZ * dirZ));
        Vec3 direction = new Vec3(dirX * ROUND_SPEED / length, dirY * ROUND_SPEED / length, dirZ * ROUND_SPEED / length);
        ProjectileEntity bullet = ProjectileEntity.create(ProjectileKind.BULLET, this.level(),
                new Vec3(spawnX, this.getY(), spawnZ), direction, yaw, pitch, this.speed);
        bullet.setWeapon(this.weaponName());
        int roundExplosion = roundIndex % EXPLOSIVE_ROUND_INTERVAL == 0 ? this.explosionPower : 0;
        bullet.warhead().set(this.power, roundExplosion, bullet.warhead().explosionPowerInWater(),
                bullet.warhead().piercing());
        bullet.setShooter(this.shooter, this.platform);

        this.level().addFreshEntity(bullet);
    }

    public boolean isRender() {
        return this.despawnCount > RENDER_START_TICK;
    }

    @Override
    protected @NotNull MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        // the model is much larger than the collision box
        return this.getBoundingBox().inflate(10.0, 4.0, 10.0);
    }
}
