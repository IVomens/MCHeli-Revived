package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleSeat;
import com.ivomens.mcheli.combat.HurtTime;
import com.ivomens.mcheli.combat.countermeasure.ChaffEntity;
import com.ivomens.mcheli.combat.countermeasure.FlareEntity;
import com.ivomens.mcheli.combat.damage.DamageScaling;
import com.ivomens.mcheli.combat.damage.ModDamageSources;
import com.ivomens.mcheli.combat.explosion.BlastCover;
import com.ivomens.mcheli.combat.explosion.ExplosionSpec;
import com.ivomens.mcheli.combat.explosion.Explosions;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.net.CombatSounds;
import com.ivomens.mcheli.combat.weapon.net.HitMarkers;
import com.ivomens.mcheli.event.ApiEvents;
import com.ivomens.mcheli.registry.McheliCriteria;
import com.ivomens.mcheli.sound.WorldSounds;
import com.ivomens.mcheli.util.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * What a projectile does to what it hits: the damage of the round, then the explosion (fuel-air, under
 * water, or only its sound), and the hit marker for the shooter.
 * <p>
 * <b>Sound-only explosions.</b> A negative explosion power means "play the explosion sound, explode
 * nothing". The tracer machine gun fires its rounds like that when the high-explosive mode is off.
 */
public final class Warhead {

    private int power = 1;
    private int explosionPower;
    private int explosionPowerInWater;
    int piercing;

    /** Takes the damage and explosion powers of the weapon or parent projectile. */
    public void set(int power, int explosionPower, int explosionPowerInWater, int piercing) {
        this.power = power;
        this.explosionPower = explosionPower;
        this.explosionPowerInWater = explosionPowerInWater;
        this.piercing = piercing;
    }

    void copyFrom(Warhead other) {
        this.set(other.power, other.explosionPower, other.explosionPowerInWater, other.piercing);
    }

    public int power() {
        return this.power;
    }

    public int explosionPower() {
        return this.explosionPower;
    }

    public int explosionPowerInWater() {
        return this.explosionPowerInWater;
    }

    public int piercing() {
        return this.piercing;
    }

    public void setPower(int power) {
        this.power = power;
    }

    public void setExplosionPower(int explosionPower, int explosionPowerInWater) {
        this.explosionPower = explosionPower;
        this.explosionPowerInWater = explosionPowerInWater;
    }

    /** Fires without its own explosion, as plain rounds of a rocket pod in the first mode do. */
    public void setPiercing(int piercing) {
        this.piercing = piercing;
    }

    /** The extra punch of a top-attack dive: a fifth more damage and one more explosion power. */
    public void boostForDive(float damageFactor) {
        this.power = (int) (this.power * damageFactor);
        if (this.explosionPower > 0) this.explosionPower++;
    }

    void impact(ProjectileEntity p, HitResult hit) {
        if (p.addon != null) {
            p.addon.hit(hit);
        }
        Entity entityHit = hit instanceof EntityHitResult entityResult ? entityResult.getEntity() : null;
        if (p.level().isClientSide) {
            ProjectileImpacts.show(p, hit, entityHit);
            return;
        }
        ApiEvents.projectileImpact(p, p.weaponName(), p.shooter(), hit);
        WeaponSpec info = p.spec();
        if (entityHit != null) {
            if (entityHit instanceof ProjectileEntity && (info == null || !info.missile().canBeIntercepted())) return;
            if (entityHit instanceof FlareEntity || entityHit instanceof ChaffEntity) return;
            this.damage(p, entityHit, 1.0F);
            this.piercing--;
        }
        if (hit.getType() == HitResult.Type.BLOCK && isPassThrough(p.level().getBlockState(blockPos(hit)))) {
            return;
        }
        float blastPower = this.explosionPower;
        if (this.piercing > 0) {
            this.piercing--;
            if (blastPower > 0.0F) {
                this.explode(p, hit.getLocation(), 1.0F, 1.0F, false);
            }
            return;
        }
        this.explodeOnImpact(p, hit, entityHit != null, blastPower, this.explosionPowerInWater, info);
        if (info != null) {
            this.playHitSound(p, p.position());
        }
        p.discard();
    }

    static BlockPos blockPos(HitResult hit) {
        return hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos() : BlockPos.containing(hit.getLocation());
    }

    /** Leaves, plants and iron bars do not stop projectiles. */
    private static boolean isPassThrough(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.getBlock() instanceof BushBlock || state.is(Blocks.IRON_BARS);
    }

    /** Picks the explosion of the impact: fuel-air, normal, or the underwater charge when in water. */
    private void explodeOnImpact(ProjectileEntity p, HitResult hit, boolean hitEntity, float power, float powerInWater,
                                 @Nullable WeaponSpec info) {
        Vec3 at = hit.getLocation();
        if (p.addon != null && p.addon.cancelsExplosion(at)) {
            return;
        }
        float blockPower = info != null ? (float) info.explosionBlock() : power;
        if (powerInWater == 0.0F) {
            if (info != null && info.isFAE()) {
                this.explodeFuelAir(p, p.position(), power, blockPower);
            } else {
                this.explodeOrPlaySound(p, at, power, blockPower);
            }
        } else if (hitEntity) {
            if (p.isInWater()) {
                this.explode(p, at, powerInWater, powerInWater, true);
            } else {
                this.explode(p, at, power, blockPower, false);
            }
        } else if (!p.isInWater() && !Terrain.isNearWater(p.level(), blockPos(hit))) {
            this.explodeOrPlaySound(p, at, power, blockPower);
        } else {
            BlockPos pos = blockPos(hit);
            this.explode(p, new Vec3(pos.getX(), pos.getY(), pos.getZ()), powerInWater, powerInWater, true);
        }
    }

    /** A positive power explodes; a negative one only plays the explosion sound. */
    private void explodeOrPlaySound(ProjectileEntity p, Vec3 at, float power, float blockPower) {
        if (power > 0.0F) {
            this.explode(p, at, power, blockPower, false);
        } else if (power < 0.0F) {
            playExplosionSound(p);
        }
    }

    /** When the delay or time fuze runs out. */
    void detonateOnTimeout(ProjectileEntity p) {
        if (p.addon != null && p.addon.cancelsExplosion(p.position())) {
            return;
        }
        if (p.isInWater()) {
            if (this.explosionPowerInWater > 0) {
                this.explode(p, p.position(), this.explosionPowerInWater, this.explosionPowerInWater, true);
            }
        } else if (this.explosionPower > 0) {
            float blockPower = p.spec() != null ? p.spec().explosionBlock() : this.explosionPower;
            this.explode(p, p.position(), this.explosionPower, blockPower, false);
        } else if (this.explosionPower < 0) {
            playExplosionSound(p);
        }
    }

    /** Airburst or proximity: the airburst explosion, or just its sound, plus the hit sound. */
    void detonateInAir(ProjectileEntity p, double x, double y, double z, WeaponSpec info) {
        if (p.addon != null && p.addon.cancelsExplosion(new Vec3(x, y, z))) {
            this.playHitSound(p, new Vec3(x, y, z));
            return;
        }
        if (info.explosion().power() > 0) {
            this.explode(p, new Vec3(x, y, z), info.explosionAirburst(), (float) info.explosionBlock(), false);
        } else if (this.explosionPower < 0) {
            playExplosionSound(p);
        }
        this.playHitSound(p, new Vec3(x, y, z));
    }

    /** The proximity fuze went off next to the entity: the burst, plus the fuze's own damage to it. */
    void detonateNear(ProjectileEntity p, Entity entity, boolean lockedTarget, Vec3 at, WeaponSpec info) {
        this.detonateInAir(p, at.x, at.y, at.z, info);
        if (!entity.isRemoved()) {
            HurtTime.scale(entity);
            // the explosion reports no result, so the fuze damage itself uses the bullet damage source
            DamageSource source = ModDamageSources.bullet(p.level(), p, p.shooter());
            float damage = DamageScaling.vsEntity(entity, source, info.missile().lockOn().proximityFuseDamage())
                    * info.getDamageFactor(entity);
            entity.hurt(source, damage);
            if (lockedTarget) entity.discard();
            if (damage > 0.0F) notifyHit(p);
        }
        p.discard();
    }

    /** The round's own damage to the entity it hit. */
    void damage(ProjectileEntity p, Entity entity, float damageFactor) {
        if (!entity.isRemoved()) {
            HurtTime.scale(entity);
            DamageSource source = ModDamageSources.bullet(p.level(), p, p.shooter());
            float damage = DamageScaling.vsEntity(entity, source, this.power * damageFactor);
            damage *= p.spec() != null ? p.spec().getDamageFactor(entity) : 1.0F;
            entity.hurt(source, damage);
            if (p.kind() == ProjectileKind.BULLET && entity instanceof Villager
                    && p.shooter() instanceof ServerPlayer player && player.getVehicle() instanceof VehicleSeat) {
                McheliCriteria.VILLAGER_HURT_BULLET.trigger(player);
            }
        }
        notifyHit(p);
    }

    /** An explosion at the given power, with the weapon's explosion rules applied. */
    public void explode(ProjectileEntity p, Vec3 at, float power, float blockPower, boolean inWater) {
        this.explode(p, at, power, blockPower, power, blockPower, inWater);
    }

    /**
     * Explodes with the given radii and powers. Definitions using the new explosion rules override
     * the radius ({@code ExplosionRadius}) and block power ({@code ExplosionBlock}).
     */
    public void explode(ProjectileEntity p, Vec3 at, float size, float sizeBlock, float damagePower, float blockPower,
                        boolean inWater) {
        WeaponSpec info = p.spec();
        float damageRadius = size;
        if (info != null && info.explosion().newBreak()) {
            size = info.explosionRadius() > 0.0F ? info.explosionRadius() : size;
            sizeBlock = size;
            blockPower = info.explosionBlock() >= 0 ? info.explosionBlock() : blockPower;
            damageRadius = info.explosion().damageRadius() > 0.0F ? info.explosion().damageRadius() : size;
        }
        if (info != null && info.useHBM()) {
            NtmPayloads.detonate(p, info, at, blockPower);
        }
        if (info != null && info.effectOnly()) {
            return;
        }
        // a third of projectile explosions are heard; the blast always breaks blocks
        ExplosionSpec spec = ExplosionSpec.at(at, size).blockSize(sizeBlock).damagePower(damagePower)
                .blockPower(blockPower).damageRadius(damageRadius).fire(info != null && info.explosion().flaming())
                .sound(p.getRandom().nextInt(3) == 0).destroyBlocks(true).underWater(inWater)
                .damageFactor(info != null ? info.damageFactor() : null).cover(blastCover(info)).build();
        if (Explosions.detonate(p.level(), spec, p, p.shooter()).hitEntity()) {
            notifyHit(p);
        }
    }

    /** Fuel-air explosion: always sets fire and has a large flame radius. */
    private void explodeFuelAir(ProjectileEntity p, Vec3 at, float power, float blockPower) {
        WeaponSpec info = p.spec();
        boolean newBreak = info != null && info.explosion().newBreak();
        float size = newBreak && info.explosionRadius() > 0.0F ? info.explosionRadius() : power;
        float damageRadius = newBreak && info.explosion().damageRadius() > 0.0F ? info.explosion().damageRadius()
                : size;
        ExplosionSpec spec = ExplosionSpec.at(at, size).damagePower(power).blockPower(blockPower)
                .damageRadius(damageRadius).fire(info != null && info.explosion().flaming()).igniteEntities(15)
                .cover(blastCover(info)).build();
        if (Explosions.detonate(p.level(), spec, p, p.shooter()).hitEntity()) {
            notifyHit(p);
        }
    }

    private static BlastCover blastCover(@Nullable WeaponSpec info) {
        return info != null ? new BlastCover(info.explosion().throughWall(), info.explosion().throughWallFactor())
                : BlastCover.EXPOSURE;
    }

    static void playExplosionSound(ProjectileEntity p) {
        WorldSounds.playExplosionSound(p.level(), p.getX(), p.getY(), p.getZ());
    }

    /** Server: the weapon's hit sound for the players nearby. */
    void playHitSound(ProjectileEntity p, Vec3 at) {
        WeaponSpec info = p.spec();
        if (info != null) {
            CombatSounds.playAround(p.level(), at, info.sound().hitSoundRange(), info.sound().locations().hit() != null
                    ? info.sound().locations().hit().toString() : null,
                    true);
        }
    }

    /** Shows the shooting player the hit marker, on the HUD of its vehicle and its own. */
    static void notifyHit(ProjectileEntity p) {
        if (p.shooter() instanceof ServerPlayer player) {
            if (p.platform() instanceof Vehicle) {
                HitMarkers.send(p.platform(), player);
            }
            HitMarkers.send(null, player);
        }
    }
}
