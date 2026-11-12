package com.ivomens.mcheli.combat.weapon.client;

import com.ivomens.mcheli.combat.weapon.net.LaserBeamMessage;
import com.ivomens.mcheli.combat.weapon.net.LaserBeamStyle;
import com.ivomens.mcheli.networking.ClientMessageHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Draws a laser beam the server fired, with smoke and flames where it hit. */
final class LaserBeamHandler implements ClientMessageHandler<LaserBeamMessage> {

    private static final int IMPACT_SMOKE_PARTICLES = 6;
    private static final int IMPACT_FLAME_PARTICLES = 4;

    @Override
    public void handle(LaserBeamMessage message) {
        LaserBeamStyle style = message.style();
        LaserRenderer.addBeam(message.source(), message.target(), style.argb(), style.width(), style.life(),
                style.pulsate(), style.renderStartDist());
        ClientLevel level = Minecraft.getInstance().level;
        if (message.hitSomething() && level != null) {
            spawnImpact(level, message.target());
        }
    }

    private static void spawnImpact(ClientLevel level, Vec3 hit) {
        RandomSource rand = level.random;
        for (int i = 0; i < IMPACT_SMOKE_PARTICLES; i++) {
            level.addParticle(ParticleTypes.CLOUD,
                    hit.x + (rand.nextFloat() - 0.5D), hit.y + 0.1D, hit.z + (rand.nextFloat() - 0.5D),
                    rand.nextGaussian() * 0.02D, Math.abs(rand.nextGaussian()) * 0.04D, rand.nextGaussian() * 0.02D);
        }
        for (int i = 0; i < IMPACT_FLAME_PARTICLES; i++) {
            level.addParticle(ParticleTypes.FLAME,
                    hit.x + (rand.nextFloat() - 0.5D) * 0.5D, hit.y + 0.1D, hit.z + (rand.nextFloat() - 0.5D) * 0.5D,
                    0.0D, 0.02D, 0.0D);
        }
    }
}
