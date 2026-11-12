package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftBoundingBox;
import com.ivomens.mcheli.aircraft.PartBoundingBox;
import com.ivomens.mcheli.combat.damage.AreaDamageSource;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Works out which part of the hull a hit lands on, when the hit happens, from what the damage
 * source says about it. It asks the hull box with the same geometry the hit detection used:
 * <ul>
 *   <li>an explosion of ours carries the area it searched for victims;</li>
 *   <li>a projectile flew along its motion this tick, through the hull box grown by the pick
 *       margin every projectile hit test uses;</li>
 *   <li>a melee attacker is judged by the area its own movement checks for vehicles in its way,
 *       its box grown by the collision search reach (see {@code MixinEntity});</li>
 *   <li>any other explosion is judged by the area around where it came from.</li>
 * </ul>
 * Damage without a place (fire, walls, falling) lands on no part.
 */
final class HitPartLocator {

    /** {@code MixinEntity} looks this far around a moving entity's box for vehicles in its way. */
    private static final double MELEE_REACH = 5.25;
    /** The margin vanilla and our projectiles add to a target's box before the ray test. */
    private static final double PROJECTILE_PICK_MARGIN = 0.3F;
    /** Reach of a TNT-sized explosion's victim search, for explosions that do not say theirs. */
    private static final double FOREIGN_EXPLOSION_REACH = 9.0;

    private HitPartLocator() {
    }

    @Nullable
    static PartBoundingBox locate(AABB hullBox, DamageSource source) {
        if (!(hullBox instanceof AircraftBoundingBox hull)) {
            return null;
        }
        if (source instanceof AreaDamageSource area) {
            return hull.partTouching(area.area());
        }
        Entity direct = source.getDirectEntity();
        if (direct == null) {
            return null;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            Vec3 at = direct.position();
            return hull.partTouching(new AABB(at, at).inflate(FOREIGN_EXPLOSION_REACH));
        }
        if (direct instanceof LivingEntity) {
            return hull.partTouching(direct.getBoundingBox().inflate(MELEE_REACH));
        }
        Vec3 from = direct.position();
        // our bullets test a stretched step for hits, see ProjectileEntity#onUpdateCollided
        Vec3 step = direct instanceof ProjectileEntity bullet
                ? direct.getDeltaMovement().scale(bullet.stepFactor()) : direct.getDeltaMovement();
        Vec3 to = from.add(step);
        AABB picked = hull.inflate(PROJECTILE_PICK_MARGIN);
        return picked instanceof AircraftBoundingBox pickedHull ? pickedHull.partOnRay(from, to) : null;
    }
}
