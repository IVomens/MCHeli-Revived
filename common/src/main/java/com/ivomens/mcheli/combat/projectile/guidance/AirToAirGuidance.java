package com.ivomens.mcheli.combat.projectile.guidance;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import net.minecraft.world.entity.Entity;

/**
 * Air-to-air missile: it needs a target and self-destructs when it loses it, strays too far, or is
 * decoyed by flares (a heat seeker) or chaff (a radar missile), also those an addon's target says it
 * drops; after its rigid launch phase it homes on
 * the target until the proximity fuze sets it off.
 * <p>
 * <b>Squared proximity.</b> This fuze compares {@code ProximityFuseDist} with the <i>squared</i>
 * distance to the target, unlike the anti-tank missile's ({@link AntiTankGuidance}), so a fuze of 9
 * goes off at 3 blocks. Content packs tune their air-to-air missiles to it, so it stays.
 */
public final class AirToAirGuidance implements Guidance {

    @Override
    public void steer(ProjectileEntity missile) {
        if (missile.level().isClientSide) {
            return;
        }
        WeaponSpec info = missile.spec();
        Entity target = missile.target();
        if (missile.shooter() == null || target == null || target.isRemoved()) {
            missile.discard();
            return;
        }
        double distSq = missile.distanceToSqr(target);
        if (distSq > Homing.MAX_TARGET_DIST_SQ) {
            missile.discard();
            return;
        }
        if (missile.age() <= info.missile().lockOn().delay()) {
            return;
        }
        if (Homing.targetDropsDecoys(missile, target) || Homing.decoyedByExternalTarget(missile, target)) {
            missile.discard();
            return;
        }
        if (info.explosion().proximityFuseDist() >= 0.1F && distSq < info.explosion().proximityFuseDist()) {
            Homing.burstNextTo(missile, target);
        } else {
            Homing.steerTowards(missile, target.getX(), target.getY(), target.getZ(), true);
        }
    }
}
