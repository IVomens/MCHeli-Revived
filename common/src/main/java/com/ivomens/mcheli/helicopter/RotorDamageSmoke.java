package com.ivomens.mcheli.helicopter;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Client: smoke trails from the rotor hubs once the helicopter is below half its HP, denser the more
 * it is damaged; below 30% random hull parts smoke as well.
 */
final class RotorDamageSmoke {

    private final HelicopterEntity heli;
    /** Where each hub's trail ended last tick; empty until the first damaged tick. */
    private Vec3[] lastHubs = new Vec3[0];
    private boolean firstTrail = true;

    RotorDamageSmoke(HelicopterEntity heli) {
        this.heli = heli;
    }

    void tick() {
        HelicopterEntity h = this.heli;
        VehicleSpec info = h.helicopterSpec();
        if (h.damage().hp() > h.damage().maxHp() * 0.5 || info == null) {
            return;
        }
        int rotorCount = info.kindParts().heliRotors().size();
        if (rotorCount == 0) {
            return;
        }
        if (this.firstTrail || this.lastHubs.length != rotorCount) {
            this.lastHubs = new Vec3[rotorCount];
            this.firstTrail = true;
        }
        for (int i = 0; i < rotorCount; i++) {
            Vec3 hub = h.position().add(VehicleMotion.rotate(h, info.kindParts().heliRotors().get(i).pos));
            if (this.firstTrail) {
                this.lastHubs[i] = hub;
            }
            spawnTrail(this.lastHubs[i], hub);
            this.lastHubs[i] = hub;
        }
        this.firstTrail = false;
    }

    private void spawnTrail(Vec3 from, Vec3 to) {
        HelicopterEntity h = this.heli;
        RandomSource random = h.getRandom();
        Vec3 delta = to.subtract(from);
        int count = (int) (delta.length() * 2.0F) + 1;
        double health = (double) h.damage().hp() / h.damage().maxHp();
        for (int i = 0; i < count; i++) {
            if (health >= random.nextFloat() / 2.0F) {
                continue;
            }

            double t = (double) i / count;
            float grey = 0.2F + random.nextFloat() * 0.3F;
            ParticleParams particle = new ParticleParams(h.level(), "smoke",
                    from.x + delta.x * t, from.y + delta.y * t, from.z + delta.z * t);
            particle.setMotion((random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.1,
                    (random.nextDouble() - 0.5) * 0.3);
            particle.setSize(random.nextInt(5) + 5.0F);
            particle.setColor(0.7F + random.nextFloat() * 0.1F, grey, grey, grey);
            Effects.get().spawn(particle);

            // below 30% a random hull part smokes as well
            int part = random.nextInt(1 + h.partBoxes().boxes().length);
            if (health < 0.3 && part > 0) {
                AABB box = h.partBoxes().boxes()[part - 1].boundingBox();
                Vec3 center = box.getCenter();
                particle.setPosition(center.x, center.y, center.z);
                Effects.get().spawn(particle);
            }
        }
    }
}
