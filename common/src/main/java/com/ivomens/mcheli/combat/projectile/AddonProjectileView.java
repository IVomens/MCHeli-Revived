package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.combat.weapon.ArmedVehicle;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.content.WeaponInfo;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.weapon.DetonationContext;
import com.ivomens.mcheli.api.weapon.GuidanceContext;
import com.ivomens.mcheli.combat.projectile.guidance.AddonGuidance;
import com.ivomens.mcheli.combat.weapon.WeaponKind;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * One projectile as the API's guidance laws and projectile effects see it ({@link GuidanceContext},
 * {@link DetonationContext}). Made once per projectile with addon behaviours and handed to every call, so a tick
 * allocates nothing for it.
 */
abstract class AddonProjectileView implements GuidanceContext, DetonationContext {

    /** A sub-munition spawned without a velocity falls from rest, as a spread child of a cluster round does. */
    private static final Vec3 AT_REST = new Vec3(1.0E-6, -1.0E-6, 1.0E-6);

    final ProjectileEntity projectile;
    final WeaponSpec weapon;
    @Nullable
    private WeaponInfo weaponInfo;
    @Nullable
    private CompoundTag data;
    private Vec3 detonationPoint = Vec3.ZERO;
    private boolean explosionCancelled;

    AddonProjectileView(ProjectileEntity projectile, WeaponSpec weapon) {
        this.projectile = projectile;
        this.weapon = weapon;
    }

    final boolean isServer() {
        return !this.projectile.level().isClientSide;
    }

    final void startDetonation(Vec3 at) {
        this.detonationPoint = at;
        this.explosionCancelled = false;
    }

    @Override
    public Entity entity() {
        return this.projectile;
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) this.projectile.level();
    }

    @Override
    public WeaponInfo weapon() {
        if (this.weaponInfo == null) {
            this.weaponInfo = McheliApi.get().content().weapon(this.weapon.name())
                    .orElseThrow(() -> new IllegalStateException("weapon " + this.weapon.name() + " is not loaded"));
        }
        return this.weaponInfo;
    }

    @Override
    public Vec3 position() {
        return this.projectile.position();
    }

    @Override
    public Vec3 velocity() {
        return this.projectile.getDeltaMovement();
    }

    @Override
    public double speed() {
        return this.projectile.speed();
    }

    @Override
    public int ticksFlown() {
        return this.projectile.age();
    }

    @Override
    public boolean isSubmunition() {
        return this.projectile.isBomblet();
    }

    @Override
    @Nullable
    public Entity shooter() {
        return this.projectile.shooter();
    }

    @Override
    @Nullable
    public Entity launcher() {
        return this.projectile.platform();
    }

    @Override
    @Nullable
    public Entity target() {
        return this.projectile.target();
    }

    @Override
    @Nullable
    public Vec3 targetPoint() {
        return this.projectile.targetPoint();
    }

    @Override
    public CompoundTag data() {
        if (this.data == null) {
            this.data = new CompoundTag();
        }
        return this.data;
    }

    @Override
    public double turnRate() {
        return this.weapon.missile().turningFactor();
    }

    @Override
    public float seekerConeDegrees() {
        return this.weapon.missile().maxDegreeOfMissile();
    }

    @Override
    public boolean isInSeekerCone(Vec3 point) {
        return AddonGuidance.inSeekerCone(this.projectile, point);
    }

    @Override
    public boolean isTargetDecoyed() {
        return AddonGuidance.isTargetDecoyed(this.projectile);
    }

    @Override
    public void setTarget(@Nullable Entity target) {
        this.projectile.setTarget(target);
    }

    @Override
    public Entity spawnProjectile(String weaponId, Vec3 position, Vec3 velocity) {
        WeaponSpec child = Weapons.get(weaponId)
                .orElseThrow(() -> new IllegalArgumentException("no weapon '" + weaponId + "' is loaded"));
        WeaponKind kind = WeaponKind.ofLegacyName(child.definition().type());
        ProjectileKind projectileKind = kind != null ? kind.projectile() : null;
        if (projectileKind == null) {
            throw new IllegalArgumentException("weapon '" + weaponId + "' fires no projectile");
        }
        Vec3 motion = velocity.lengthSqr() < 1.0E-12 ? AT_REST : velocity;
        ProjectileEntity spawned = ProjectileEntity.create(projectileKind, this.projectile.level(), position, motion,
                this.projectile.getYRot(), this.projectile.getXRot(), motion.length());
        FlightModel.faceMotion(spawned);
        spawned.setWeapon(weaponId);
        spawned.warhead().set(child.definition().baseDamage(), child.explosion().power(), child.explosionInWater(),
                child.ballistics().piercing());
        spawned.setShooter(this.projectile.shooter(), this.projectile.platform());
        spawned.markBomblet();
        this.projectile.level().addFreshEntity(spawned);
        return spawned;
    }

    @Override
    public void explode(Vec3 at, float power) {
        float blockPower = this.weapon.explosion().destructionPower() < 0 ? power : this.weapon.explosionBlock();
        this.projectile.warhead().explode(this.projectile, at, power, blockPower, false);
    }

    @Override
    public void stallEngine(Vehicle vehicle, int ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("an engine is stalled for at least one tick, not " + ticks);
        }
        if (vehicle instanceof ArmedVehicle armed && !armed.isDestroyed()) {
            armed.stallEngine(ticks);
        }
    }

    @Override
    public Vec3 at() {
        return this.detonationPoint;
    }

    @Override
    public void cancelExplosion() {
        this.explosionCancelled = true;
    }

    @Override
    public boolean isExplosionCancelled() {
        return this.explosionCancelled;
    }
}
