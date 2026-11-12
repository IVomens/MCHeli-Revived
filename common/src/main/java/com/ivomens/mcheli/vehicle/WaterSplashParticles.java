package com.ivomens.mcheli.vehicle;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Bow-wave spray for vehicles moving through water, driven by the definition's {@code ParticleSplash} entries.
 * Client side only.
 */
public final class WaterSplashParticles {

    /** Squared horizontal speed at which the spray reaches full size. */
    private static final double FULL_SPRAY_SPEED_SQ = 1.0;

    private WaterSplashParticles() {
    }

    public static void spawn(AircraftEntity vehicle) {
        VehicleSpec info = vehicle.spec();
        if (info == null || !vehicle.level().isClientSide) {
            return;
        }

        RandomSource random = vehicle.getRandom();
        double moveX = vehicle.getX() - vehicle.xo;
        double moveZ = vehicle.getZ() - vehicle.zo;
        double speedSq = Math.min(moveX * moveX + moveZ * moveZ, FULL_SPRAY_SPEED_SQ);
        for (VehicleParts.ParticleSplash splash : info.parts().splashes()) {
            for (int i = 0; i < splash.num(); i++) {
                if (speedSq > 0.03 + random.nextFloat() * 0.1) {
                    spawnDroplet(vehicle, random, splash, -moveX * splash.acceleration(),
                            -moveZ * splash.acceleration(), splash.size() * (0.5 + speedSq * 0.5));
                }
            }
        }
    }

    private static void spawnDroplet(AircraftEntity vehicle, RandomSource random, VehicleParts.ParticleSplash splash,
                                     double motionX, double motionZ, double size) {
        Vec3 pos = vehicle.body().toWorld(splash.pos())
                .add(random.nextDouble() - 0.5, (random.nextDouble() - 0.5) * 0.5, random.nextDouble() - 0.5);
        if (!vehicle.level().getBlockState(new BlockPos(Mth.floor(pos.x + 0.5), Mth.floor(pos.y),
                Mth.floor(pos.z + 0.5))).is(Blocks.WATER)) {
            return;
        }

        float brightness = random.nextFloat() * 0.3F + 0.7F;
        ParticleParams particle = new ParticleParams(vehicle.level(), "smoke", pos.x, pos.y, pos.z);
        particle.setMotion(motionX + (random.nextFloat() - 0.5) * 0.7, splash.motionY(),
                motionZ + (random.nextFloat() - 0.5) * 0.7);
        particle.setSize((float) size * (random.nextFloat() * 0.2F + 0.8F));
        particle.setColor(0.9F, brightness, brightness, brightness);
        particle.setAge(splash.age() + (int) (random.nextFloat() * 0.5 * splash.age()));
        particle.setGravity(splash.gravity());
        Effects.get().spawn(particle);
    }
}
