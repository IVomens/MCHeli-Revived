package com.ivomens.mcheli.example.client;

import com.ivomens.mcheli.api.client.weapon.ProjectileVisual;
import com.ivomens.mcheli.api.client.weapon.ProjectileVisualContext;
import com.ivomens.mcheli.api.client.weapon.ProjectileVisualProvider;
import com.ivomens.mcheli.api.client.weapon.ProjectileVisualRegistrar;
import com.ivomens.mcheli.example.weapon.ExampleBehaviours;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * What clients draw for the EMP rocket in flight: a few sparks along its path, so players can tell it from an
 * ordinary rocket. MCHeli's client finds this class through
 * {@code META-INF/services/com.ivomens.mcheli.api.client.weapon.ProjectileVisualProvider}; a dedicated server never
 * loads it. The burst itself is drawn by the server side effect ({@code EmpEffect}), which every player sees.
 */
public final class ExampleVisuals implements ProjectileVisualProvider {

    @Override
    public void registerVisuals(ProjectileVisualRegistrar visuals) {
        visuals.visual(ExampleBehaviours.EMP, settings -> new EmpSparks());
    }

    /** Sparks trailing the rocket every other tick. */
    private static final class EmpSparks implements ProjectileVisual {

        @Override
        public void onTick(ProjectileVisualContext projectile) {
            if (projectile.ticksFlown() % 2 != 0) {
                return;
            }
            Vec3 at = projectile.position();
            Vec3 back = projectile.velocity().scale(-0.5);
            projectile.level().addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, back.x, back.y, back.z);
        }
    }
}
