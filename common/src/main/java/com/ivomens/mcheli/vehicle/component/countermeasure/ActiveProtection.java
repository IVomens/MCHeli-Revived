package com.ivomens.mcheli.vehicle.component.countermeasure;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.countermeasure.CountermeasureFlag;
import com.ivomens.mcheli.combat.explosion.ExplosionSpec;
import com.ivomens.mcheli.combat.explosion.Explosions;
import com.ivomens.mcheli.combat.guidance.ForeignVehicles;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Active protection system (APS): while active it shoots down the projectiles that come within range,
 * our own and, by class name, the bullets and grenades of other gun mods. Missiles and grenades go off
 * where they are hit.
 */
public final class ActiveProtection implements Countermeasure {

    private final Level level;
    private final AircraftEntity vehicle;
    private int cooldown;
    private int activeTicks;
    private int range;

    public ActiveProtection(AircraftEntity vehicle) {
        this.level = vehicle.level();
        this.vehicle = vehicle;
    }

    /** Switches it on for {@code activeTicks}; false while it is still recharging. */
    public boolean activate(int activeTicks, int cooldown) {
        if (this.cooldown != 0) {
            return false;
        }
        this.cooldown = cooldown;
        this.activeTicks = activeTicks;
        if (!this.level.isClientSide) {
            CountermeasureFlag.APS_USING.set(this.vehicle, true);
        }
        this.playSound("aps_activate", 3.0F);
        return true;
    }

    /** Keeps the range in step with the vehicle's definition, which can change while it runs. */
    public void setRange(int range) {
        this.range = range;
    }

    @Override
    public void tick() {
        if (this.vehicle.isRemoved()) {
            return;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (this.activeTicks > 0) {
            this.activeTicks--;
            if (this.activeTicks == 0) {
                this.playSound("aps_deactivate", 3.0F);
            } else {
                this.intercept();
            }
        }
        if (!this.isActive()) {
            CountermeasureFlag.APS_USING.clear(this.vehicle);
        }
    }

    @Override
    public boolean isActive() {
        return this.activeTicks > 0;
    }

    @Override
    public boolean isRecharging() {
        return this.cooldown != 0;
    }

    private void playSound(String name, float volume) {
        ModSounds.playSound(this.level, this.vehicle.getX(), this.vehicle.getY(), this.vehicle.getZ(), name, volume, 1.0F);
    }

    private static boolean isInterceptable(Entity entity) {
        return !entity.isRemoved() && (entity instanceof ProjectileEntity || ForeignVehicles.isProjectile(entity));
    }

    private void intercept() {
        List<Entity> threats = this.level.getEntities(this.vehicle, this.vehicle.getBoundingBox().inflate(this.range),
                ActiveProtection::isInterceptable);
        for (Entity entity : threats) {
            if (this.isFriendly(entity)) {
                continue;
            }
            if (this.level.isClientSide) {
                this.drawFlameLine(this.vehicle.position(), entity.position());
                continue;
            }
            this.playSound("aps_shoot", 5.0F);
            entity.discard();
            if (entity instanceof ProjectileEntity || ForeignVehicles.isGrenade(entity)) {
                Explosions.detonate(this.level, ExplosionSpec.at(entity.position(), 2.0F).blockSize(0.0F)
                        .destroyBlocks(true).build(), null, null);
            }
        }
    }

    /** Projectiles fired by an ally of the pilot are let through. */
    private boolean isFriendly(Entity entity) {
        return entity instanceof ProjectileEntity bullet && bullet.shooter() instanceof LivingEntity shooter
                && this.vehicle.getRiddenByEntity() instanceof LivingEntity pilot && shooter.isAlliedTo(pilot);
    }

    private void drawFlameLine(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int count = (int) (delta.length() * 5);
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            this.level.addParticle(ParticleTypes.FLAME, from.x + delta.x * t, from.y + delta.y * t,
                    from.z + delta.z * t, 0.0, 0.0, 0.0);
        }
    }
}
