package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponKind;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Client-side particle effects of an aircraft: smoke generators and the dust/spray kicked up below it. */
public final class AircraftParticles {

    private AircraftParticles() {
    }

    /** Smoke trail of a selected smoke-generator weapon, denser with more throttle. */
    static void spawnWeaponSmoke(AircraftEntity ac) {
        if (!ac.level().isClientSide || ac.engine().throttle() <= 0.1F) {
            return;
        }
        WeaponGroup weapons = ac.weapons().current(ac.getRiddenByEntity());
        if (weapons.kind() != WeaponKind.SMOKE) {
            return;
        }
        RandomSource random = ac.getRandom();
        float yaw = ac.getYRot();
        float pitch = ac.getXRot();
        float roll = ac.roll();
        for (int i = 0; i < weapons.mountCount(); i++) {
            WeaponMount weapon = weapons.mount(i);
            WeaponSpec info = weapon != null ? weapon.spec() : null;
            if (info == null) {
                continue;
            }
            Vec3 direction = Rotations.rotate(0.0, 0.0, 1.0, -yaw - 180.0F + weapon.fixedYaw(),
                    pitch - weapon.fixedPitch(), roll);
            if (random.nextFloat() > ac.engine().throttle() * 1.5) {
                continue;
            }
            Vec3 offset = Rotations.rotate(weapon.position(), -yaw, -pitch, -roll);
            double x = ac.getX() + offset.x + direction.x;
            double y = ac.getY() + offset.y + direction.y;
            double z = ac.getZ() + offset.z + direction.z;
            for (int puff = 0; puff < info.render().smoke().count(); puff++) {
                float tint = random.nextFloat() * 0.05F;
                int maxAge = (int) (random.nextDouble() * info.render().smoke().maxAge());
                ParticleParams smoke = new ParticleParams(ac.level(), "smoke", x, y, z);
                smoke.setMotion(direction.x * info.ballistics().acceleration() + (random.nextDouble() - 0.5) * 0.2,
                        direction.y * info.ballistics().acceleration() + (random.nextDouble() - 0.5) * 0.2,
                        direction.z * info.ballistics().acceleration() + (random.nextDouble() - 0.5) * 0.2);
                smoke.setSize((random.nextInt(5) + 5.0F) * info.render().smoke().size());
                smoke.setColor(info.bulletColor().a + random.nextFloat() * 0.05F, info.bulletColor().r + tint,
                        info.bulletColor().g + tint, info.bulletColor().b + tint);
                smoke.setAge(maxAge);
                smoke.setTurnsWhite(true);
                smoke.setDiffusible(true);
                Effects.get().spawn(smoke);
            }
        }
    }

    /**
     * Dust (or sea spray with {@code seaOnly}) blown up by rotors and engines when close to the
     * ground. Sea spray needs enough water blocks under the aircraft to count as a sea surface.
     */
    public static void spawnGroundCloud(AircraftEntity ac, boolean seaOnly) {
        VehicleSpec info = ac.spec();
        if (info == null || seaOnly && !info.file().render().seaSurfaceParticles()) {
            return;
        }
        float scale = info.file().render().particleScale() * 3.0F * (seaOnly ? 2.0F : 1.0F);
        double throttle = Math.min(ac.engine().throttle() * 2.0, 1.0);
        int waterNeeded = seaOnly ? (int) (scale * 7.0F) : 0;
        int rangeY = (int) (scale * 10.0F) + 1;
        int baseX = Mth.floor(ac.getX() + 0.5);
        int baseY = Mth.floor(ac.getY() + 0.5);
        int baseZ = Mth.floor(ac.getZ() + 0.5);

        int surface = surfaceDepth(ac, baseX, baseY, baseZ, rangeY, seaOnly, waterNeeded);
        if (surface < 0 || info.file().render().particleScale() <= 0.01F) {
            return;
        }
        double cloudY = ac.getY() + 1.0 + scale / 5.0F - surface;
        double density = (rangeY - surface) / (5.0 * scale) / 2.0;
        RandomSource random = ac.getRandom();
        for (int i = 0; i < (int) (throttle * 6.0 * density); i++) {
            float angle = (float) (random.nextDouble() * Math.PI * 2.0);
            double dx = Mth.cos(angle);
            double dz = Mth.sin(angle);
            ParticleParams cloud = new ParticleParams(ac.level(), "smoke", ac.getX() + dx * scale * 3.0,
                    cloudY + (random.nextDouble() - 0.5) * scale, ac.getZ() + dz * scale * 3.0,
                    scale * (dx * 0.3), scale * -0.4 * 0.05, scale * (dz * 0.3), scale * 5.0F);
            cloud.setAlpha(cloud.alpha() * 0.6F);
            cloud.setAge((int) (10.0F * scale));
            cloud.setRisesAfter(seaOnly ? 0.2F : 0.1F);
            Effects.get().spawn(cloud);
        }
    }

    /**
     * How many blocks below the aircraft the ground (or, with {@code seaOnly}, a sea of at least
     * {@code waterNeeded} water blocks) starts in the 3x3 column, searched down to {@code rangeY}; -1 if not found.
     */
    private static int surfaceDepth(AircraftEntity ac, int baseX, int baseY, int baseZ, int rangeY,
                                    boolean seaOnly, int waterNeeded) {
        for (int depth = 0; depth < rangeY; depth++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockState state = ac.level().getBlockState(new BlockPos(baseX + dx, baseY - depth, baseZ + dz));
                    if (state.is(Blocks.AIR)) {
                        continue;
                    }
                    if (seaOnly && state.is(Blocks.WATER)) {
                        waterNeeded--;
                    }
                    if (waterNeeded <= 0) {
                        return depth;
                    }
                }
            }
        }
        return -1;
    }
}
