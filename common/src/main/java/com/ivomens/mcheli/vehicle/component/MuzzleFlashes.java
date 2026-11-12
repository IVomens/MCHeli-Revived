package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;



/** Client effect: the flash and smoke at the muzzle of a weapon that just fired. */
public final class MuzzleFlashes {


    private MuzzleFlashes() {
    }

    static void spawn(AircraftEntity ac, WeaponComponent weapons, WeaponGroup weapon, float aircraftYaw) {
        Vec3 backwards = Rotations.rotate(0.0, 0.0, -1.0, -weapon.yaw() - aircraftYaw, -weapon.pitch());
        Vec3 shotPos = weapons.shooter(null).muzzleOffset(weapon.currentMount());
        spawn(ac.level(), ac.getRandom(), weapon.spec(), ac.getX() + shotPos.x, ac.getY() + shotPos.y,
                ac.getZ() + shotPos.z, backwards);
    }

    private static void spawn(Level level, RandomSource aircraftRandom, WeaponSpec info, double px, double py,
                                         double pz, Vec3 backwards) {
        RandomSource random = level.random;
        if (info.muzzleSmoke() != null) {
            for (WeaponSpec.MuzzleFlash flash : info.muzzleSmoke()) {
                double x = px - backwards.x * flash.dist();
                double y = py - backwards.y * flash.dist();
                double z = pz - backwards.z * flash.dist();
                ParticleParams smoke = new ParticleParams(level, "smoke", px, py, pz);
                smoke.setSize(flash.size());
                for (int i = 0; i < flash.num(); i++) {
                    float alpha = flash.a() * 0.9F + random.nextFloat() * 0.1F;
                    float tint = random.nextFloat() * 0.1F;
                    smoke.setColor(alpha, tint + flash.r() * 0.9F, tint + flash.g() * 0.9F, tint + flash.b() * 0.9F);
                    smoke.setAge((int) (flash.age() + 0.1 * flash.age() * random.nextFloat()));
                    double smokeX = x + (random.nextDouble() - 0.5) * flash.range();
                    double smokeY = y + (random.nextDouble() - 0.5) * flash.range();
                    double smokeZ = z + (random.nextDouble() - 0.5) * flash.range();
                    smoke.setPosition(smokeX, smokeY, smokeZ);
                    smoke.setMotion(random.nextDouble() * (smokeX < x ? -0.2 : 0.2),
                            random.nextDouble() * (smokeY < y ? -0.03 : 0.03),
                            random.nextDouble() * (smokeZ < z ? -0.2 : 0.2));
                    Effects.get().spawn(smoke);
                }
            }
        }

        if (info.muzzleFlashes() != null) {
            for (WeaponSpec.MuzzleFlash flash : info.muzzleFlashes()) {
                float brightness = aircraftRandom.nextFloat() * 0.1F + 0.9F;
                Effects.get().muzzleFlash(level, px - backwards.x * flash.dist(), py - backwards.y * flash.dist(),
                        pz - backwards.z * flash.dist(), flash.size(), brightness * flash.r(), brightness * flash.g(),
                        brightness * flash.b(), flash.a(), flash.age() + random.nextInt(3));
            }
        }
    }
}
