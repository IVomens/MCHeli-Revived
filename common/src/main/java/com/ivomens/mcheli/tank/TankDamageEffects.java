package com.ivomens.mcheli.tank;

import com.ivomens.mcheli.aircraft.PartBoundingBox;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.vehicle.component.countermeasure.IronCurtain;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import com.ivomens.mcheli.util.math.Rotations;
import com.ivomens.mcheli.vehicle.component.DamageComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Client: the red glow of a running iron curtain, and smoke from a damaged hull. */
final class TankDamageEffects {

    private final TankEntity tank;

    TankDamageEffects(TankEntity tank) {
        this.tank = tank;
    }

    void tick() {
        if (this.tank.tankSpec() == null) {
            return;
        }
        if (this.tank.countermeasures().ironCurtain().isRunning()) {
            spawnIronCurtainGlow();
        }
        if (this.tank.damage().hp() < this.tank.damage().maxHp() * 0.5) {
            spawnDamageSmoke();
        }
    }

    private void spawnIronCurtainGlow() {
        TankEntity t = this.tank;
        RandomSource random = t.getRandom();
        IronCurtain curtain = t.countermeasures().ironCurtain();
        float pulse = 0.5f + 0.5f * (float) Math.sin(curtain.runningTicks() * 0.15f);
        float red = 0.5f * pulse;
        float green = 0.1f * pulse;
        float blue = 0.1f * pulse;
        float spread = 0.015f * (curtain.runningTicks() % 40 + 1);
        for (PartBoundingBox box : t.tankSpec().hitBoxes().boxes()) {
            Vec3 pos = t.body().toWorld(box.offsetX(), box.offsetY(), box.offsetZ());
            int count = 2 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                double x = pos.x + random.nextGaussian() * 0.3;
                double y = pos.y + random.nextGaussian() * 0.2;
                double z = pos.z + random.nextGaussian() * 0.3;
                Effects.get().tintedCloud(x, y, z, red, green, blue, spread, random);
            }
        }
    }

    /**
     * Below 20% HP every hull part smokes; between 20% and 50% only the engine does, and only now and
     * then (rarer the healthier the tank is).
     */
    private void spawnDamageSmoke() {
        TankEntity t = this.tank;
        DamageComponent damage = t.damage();
        int partCount = t.tankSpec().hitBoxes().boxes().size();
        boolean lightlyDamaged = damage.hp() >= damage.maxHp() * 0.2 && damage.maxHp() > 0;
        if (!lightlyDamaged) {
            for (int i = 0; i < partCount; i++) {
                PartBoundingBox box = t.tankSpec().hitBoxes().boxes().get(i);
                Vec3 pos = t.body().toWorld(box.offsetX(), box.offsetY(), box.offsetZ());
                spawnSmokePuff(pos.x, pos.y, pos.z, 1.0F);
            }
        }

        boolean engineSmokes = true;
        if (lightlyDamaged) {
            int odds = (int) (((double) damage.hp() / damage.maxHp() - 0.2) / 0.3 * 15.0);
            engineSmokes = odds <= 0 || t.getRandom().nextInt(odds) == 0;
        }
        if (engineSmokes) {
            Vec3 engine = t.position();
            SeatInfo driverSeat = t.seats().info(0);
            if (driverSeat != null && driverSeat.pos != null) {
                engine = engine.add(Rotations.rotate(0.0, driverSeat.pos.y, -2.0, -t.getYRot(), -t.getXRot(),
                        -t.roll()));
            }
            spawnSmokePuff(engine.x, engine.y, engine.z, partCount == 0 ? 2.0F : 1.0F);
        }
    }

    private void spawnSmokePuff(double x, double y, double z, float size) {
        RandomSource random = this.tank.getRandom();
        float grey = 0.2F + random.nextFloat() * 0.3F;
        ParticleParams particle = new ParticleParams(this.tank.level(), "smoke", x, y, z);
        particle.setMotion(size * (random.nextDouble() - 0.5) * 0.3, size * random.nextDouble() * 0.1,
                size * (random.nextDouble() - 0.5) * 0.3);
        particle.setSize(size * (random.nextInt(5) + 5.0F));
        particle.setColor(0.7F + random.nextFloat() * 0.1F, grey, grey, grey);
        Effects.get().spawn(particle);
    }
}
