package com.ivomens.mcheli.example.weapon;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.weapon.DetonationContext;
import com.ivomens.mcheli.api.weapon.ProjectileEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An electromagnetic pulse instead of an explosion: every vehicle within {@code Radius} blocks of the burst loses
 * its engine for {@code Seconds} seconds, and the warhead makes a crackle of sparks rather than a blast.
 *
 * <pre>
 * ProjectileEffects:
 * - Id: mcheli_example:emp
 *   Seconds: 4
 *   Radius: 6
 * </pre>
 *
 * @param seconds how long the engines stay dead
 * @param radius  how far from the burst vehicles are hit, in blocks
 */
public record EmpEffect(int seconds, double radius) implements ProjectileEffect {

    static final Codec<EmpEffect> SETTINGS = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 60).optionalFieldOf("Seconds", 4).forGetter(EmpEffect::seconds),
            Codec.doubleRange(0.5, 64.0).optionalFieldOf("Radius", 6.0).forGetter(EmpEffect::radius)
    ).apply(instance, EmpEffect::new));

    private static final int TICKS_PER_SECOND = 20;
    private static final int SPARKS = 40;

    static EmpEffect fromSettings(CompoundTag settings) {
        return SETTINGS.parse(NbtOps.INSTANCE, settings).getOrThrow(IllegalArgumentException::new);
    }

    @Override
    public void onDetonate(DetonationContext projectile) {
        projectile.cancelExplosion();
        Vec3 at = projectile.at();
        for (Vehicle vehicle : McheliApi.get().vehiclesIn(projectile.level(), new AABB(at, at).inflate(this.radius))) {
            projectile.stallEngine(vehicle, this.seconds * TICKS_PER_SECOND);
        }
        // server side particles and sound reach every player nearby; no client code needed for these
        projectile.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, SPARKS, this.radius / 3,
                this.radius / 3, this.radius / 3, 0.5);
        projectile.level().playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0F,
                1.5F);
    }
}
