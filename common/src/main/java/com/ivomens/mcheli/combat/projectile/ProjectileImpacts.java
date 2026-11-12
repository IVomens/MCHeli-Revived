package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.fx.Effects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;

/** What a client shows when one of its projectiles hits: debris off a block, sparks off an iron curtain. */
final class ProjectileImpacts {

    private ProjectileImpacts() {
    }

    static void show(ProjectileEntity p, HitResult hit, @Nullable Entity entityHit) {
        if (p.spec() == null) {
            return;
        }
        WeaponSpec info = p.spec();
        if (entityHit == null) {
            Effects.get().bulletDebris(p.level(), hit.getLocation(), Warhead.blockPos(hit), p.getBbWidth(),
                    info.render().flakParticlesCrack(), info.ballistics().flakSpread(), info.render().particlesFlak(),
                            p.getRandom());
        } else if (entityHit instanceof Vehicle vehicle && vehicle.signature().isIronCurtainRunning()) {
            Effects.get().ironCurtainSparks(p.level(), hit.getLocation(), Warhead.blockPos(hit), p.getBbWidth(),
                    p.getBbHeight(), info.render().flakParticlesCrack(), info.ballistics().flakSpread(), p.getRandom());
        }
    }
}
