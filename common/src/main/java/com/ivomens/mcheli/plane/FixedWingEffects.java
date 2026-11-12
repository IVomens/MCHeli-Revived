package com.ivomens.mcheli.plane;

import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Client: dust from the tyres while rolling, exhaust from the nozzles, and smoke trails from the
 * rotors and the cockpit once the plane is below half its HP.
 */
final class FixedWingEffects {

    private final AbstractPlaneEntity plane;
    /** Where each smoke trail ended last tick: one per rotor, then the cockpit. */
    private Vec3[] lastTrailEnds = new Vec3[0];
    private boolean firstTrail = true;

    FixedWingEffects(AbstractPlaneEntity plane) {
        this.plane = plane;
    }

    void spawnTyreDust() {
        AbstractPlaneEntity p = this.plane;
        RandomSource random = p.getRandom();
        Vec3 motion = p.getDeltaMovement();
        if (motion.x * motion.x + motion.z * motion.z <= 0.01) {
            return;
        }
        Effects.get().blockCrack(p.level(),
                Mth.floor(p.getX() + 0.5), Mth.floor(p.getY() - 0.5), Mth.floor(p.getZ() + 0.5),
                p.getX() + (random.nextFloat() - 0.5) * p.getBbWidth(),
                p.getBoundingBox().minY + 0.1,
                p.getZ() + (random.nextFloat() - 0.5) * p.getBbWidth(),
                -motion.x * 4.0, 1.5, -motion.z * 4.0);
    }

    void spawnNozzleExhaust() {
        AbstractPlaneEntity p = this.plane;
        VehicleSpec info = p.planeSpec();
        double throttle = p.engine().throttle();
        if (info == null || info.kindParts().nozzles().isEmpty() || throttle <= 0.1F) {
            return;
        }
        RandomSource random = p.getRandom();
        float yaw = p.getYRot();
        float pitch = p.getXRot();
        float roll = p.roll();
        Vec3 exhaustDir = Rotations.rotate(0.0, 0.0, 1.0, -yaw - 180.0F, pitch - p.nozzles().angle(), roll);
        for (VehicleParts.DrawnPart nozzle : info.kindParts().nozzles()) {
            if (random.nextFloat() > throttle * 1.5) {
                continue;
            }
            Vec3 nozzlePos = Rotations.rotate(nozzle.pos, -yaw, -pitch, -roll);
            double x = p.getX() + nozzlePos.x + exhaustDir.x;
            double y = p.getY() + nozzlePos.y + exhaustDir.y;
            double z = p.getZ() + nozzlePos.z + exhaustDir.z;
            // exhaust hitting the ground spreads out
            float spread = 0.7F;
            if (p.level().getBlockState(new BlockPos(Mth.floor(x + exhaustDir.x * 3.0), Mth.floor(y + exhaustDir.y * 3.0),
                    Mth.floor(z + exhaustDir.z * 3.0))).getBlock() != Blocks.AIR) {
                spread = 2.0F;
            }
            Effects.get().spawn(new ParticleParams(p.level(), "smoke", x, y, z,
                    exhaustDir.x + (random.nextFloat() - 0.5F) * spread,
                    exhaustDir.y,
                    exhaustDir.z + (random.nextFloat() - 0.5F) * spread,
                    5.0F * p.spec().file().render().particleScale()));
        }
    }

    void spawnDamageSmoke() {
        AbstractPlaneEntity p = this.plane;
        VehicleSpec info = p.planeSpec();
        if (p.damage().hp() >= p.damage().maxHp() * 0.5 || info == null) {
            return;
        }
        List<VehicleParts.Propeller> rotors = info.kindParts().propellers();
        int rotorCount = rotors.size();
        if (this.firstTrail || this.lastTrailEnds.length != rotorCount + 1) {
            this.lastTrailEnds = new Vec3[rotorCount + 1];
        }

        float yaw = p.getYRot();
        float pitch = p.getXRot();
        float roll = p.roll();
        for (int i = 0; i < rotorCount; i++) {
            Vec3 pos = Rotations.rotate(rotors.get(i).pos, -yaw, -pitch, -roll);
            spawnSmokeTrail(i, p.getX() + pos.x, p.getY() + pos.y, p.getZ() + pos.z, 1.0F);
        }

        // one more trail from just behind the cockpit
        double x = p.getX();
        double y = p.getY();
        double z = p.getZ();
        SeatInfo pilotSeat = p.seats().info(0);
        if (pilotSeat != null && pilotSeat.pos != null) {
            Vec3 pos = Rotations.rotate(0.0, pilotSeat.pos.y, -2.0, -yaw, -pitch, -roll);
            x += pos.x;
            y += pos.y;
            z += pos.z;
        }
        spawnSmokeTrail(rotorCount, x, y, z, rotorCount == 0 ? 2.0F : 1.0F);
        this.firstTrail = false;
    }

    /** Spawns smoke along the path from the trail's last position to this one, one puff every 0.3 blocks. */
    private void spawnSmokeTrail(int trail, double x, double y, double z, float size) {
        if (this.firstTrail || this.lastTrailEnds[trail] == null) {
            this.lastTrailEnds[trail] = new Vec3(x, y, z);
        }
        RandomSource random = this.plane.getRandom();
        Vec3 prev = this.lastTrailEnds[trail];
        double dx = x - prev.x;
        double dy = y - prev.y;
        double dz = z - prev.z;
        int count = (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.3) + 1;
        for (int i = 0; i < count; i++) {
            float grey = 0.2F + random.nextFloat() * 0.3F;
            ParticleParams particle = new ParticleParams(this.plane.level(), "smoke",
                    prev.x + dx * i / count, prev.y + dy * i / count, prev.z + dz * i / count);
            particle.setMotion(size * (random.nextDouble() - 0.5) * 0.3, size * random.nextDouble() * 0.1,
                    size * (random.nextDouble() - 0.5) * 0.3);
            particle.setSize(size * (random.nextInt(5) + 5.0F));
            particle.setColor(0.7F + random.nextFloat() * 0.1F, grey, grey, grey);
            Effects.get().spawn(particle);
        }
        this.lastTrailEnds[trail] = new Vec3(x, y, z);
    }
}
