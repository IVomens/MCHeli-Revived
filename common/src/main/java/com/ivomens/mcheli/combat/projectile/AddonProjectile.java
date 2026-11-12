package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.api.weapon.GuidanceLaw;
import com.ivomens.mcheli.api.weapon.ProjectileEffect;
import com.ivomens.mcheli.combat.projectile.behaviour.ProjectileVisualHooks;
import com.ivomens.mcheli.combat.projectile.behaviour.WeaponBehaviourSet;
import com.ivomens.mcheli.combat.projectile.guidance.AddonGuidance;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * The addon behaviours of one projectile whose weapon names some ({@code Guidance}, {@code ProjectileEffects}):
 * runs them at the points of its life on the server, and the client visuals registered for them on a client.
 * Projectiles of other weapons have none, and their ticks never get here.
 * <p>
 * A behaviour that throws is logged and dropped for the rest of the projectile's life: a failed guidance law hands
 * the steering back to the guidance of the projectile's kind.
 */
final class AddonProjectile extends AddonProjectileView {

    /** The bit of {@link #failed} that marks the guidance law. */
    private static final int GUIDANCE_FAILED = 1 << 31;

    private final WeaponBehaviourSet behaviours;
    @Nullable
    private final ProjectileVisualHooks.Visuals visuals;
    private boolean spawned;
    /** The warhead went off or the end was reported: the projectile is not reported again when it is removed. */
    private boolean ended;
    /** One bit per effect that threw, plus {@link #GUIDANCE_FAILED}. */
    private int failed;

    private AddonProjectile(ProjectileEntity projectile, WeaponSpec weapon, WeaponBehaviourSet behaviours) {
        super(projectile, weapon);
        this.behaviours = behaviours;
        this.visuals = projectile.level().isClientSide ? ProjectileVisualHooks.attach(projectile, weapon) : null;
    }

    /** The behaviours of a projectile of the weapon, or null when the weapon names none. */
    @Nullable
    static AddonProjectile of(ProjectileEntity projectile, WeaponSpec weapon) {
        WeaponBehaviourSet behaviours = weapon.behaviours();
        return behaviours == null ? null : new AddonProjectile(projectile, weapon, behaviours);
    }

    /** Before each tick: the spawn hook, once. */
    void beforeTick() {
        if (!this.spawned && this.isServer()) {
            this.spawned = true;
            this.forEachEffect(ProjectileEffect::onSpawn, null);
        }
    }

    /** After each tick the projectile survived. */
    void afterTick() {
        if (this.projectile.isRemoved()) {
            return;
        }
        if (this.isServer()) {
            this.forEachEffect(ProjectileEffect::onTick, null);
        } else if (this.visuals != null) {
            this.visuals.tick();
        }
    }

    /**
     * Steers with the weapon's guidance law instead of the kind's guidance.
     *
     * @return false when the weapon has no law (or it failed), so the kind's guidance steers
     */
    boolean steer() {
        GuidanceLaw law = this.behaviours.guidance();
        if (law == null || (this.failed & GUIDANCE_FAILED) != 0) {
            return false;
        }
        if (!this.isServer()) {
            return true;
        }
        try {
            if (AddonGuidance.steer(this.projectile, law, this) && this.projectile.kind().isFlownByClients()) {
                ProjectileSync.disturbed(this.projectile);
            }
        } catch (RuntimeException e) {
            this.failed |= GUIDANCE_FAILED;
            ModLogger.error("Guidance " + this.behaviours.guidanceId() + " of weapon " + this.weapon.name()
                    + " failed; the projectile falls back to the guidance of its type", e);
        }
        return true;
    }

    /** The projectile hit something: before the hit's damage and explosion. */
    void hit(HitResult hit) {
        if (!this.isServer()) {
            if (this.visuals != null) {
                this.visuals.impact(hit);
            }
        } else if (hit instanceof EntityHitResult entityHit) {
            this.forEachEffect((effect, context) -> effect.onEntityHit(context, entityHit), null);
        } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            this.forEachEffect((effect, context) -> effect.onBlockHit(context, blockHit), null);
        }
    }

    /**
     * Server: the warhead goes off at the point.
     *
     * @return whether an effect cancelled the explosion
     */
    boolean cancelsExplosion(Vec3 at) {
        if (!this.isServer()) {
            return false;
        }
        this.ended = true;
        this.startDetonation(at);
        this.forEachEffect(null, this);
        return this.isExplosionCancelled();
    }

    /** The projectile is being removed: on the server, the end of a projectile that did not go off. */
    void removed() {
        if (this.ended) {
            return;
        }
        this.ended = true;
        if (this.isServer()) {
            this.forEachEffect(ProjectileEffect::onExpire, null);
        } else if (this.visuals != null) {
            this.visuals.removed();
        }
    }

    /**
     * Calls each effect that has not failed, in order: {@code hook} with this projectile, or with {@code detonation}
     * set, {@link ProjectileEffect#onDetonate}.
     */
    private void forEachEffect(@Nullable BiConsumer<ProjectileEffect, AddonProjectileView> hook,
                               @Nullable AddonProjectileView detonation) {
        for (int i = 0; i < this.behaviours.effectCount(); i++) {
            if ((this.failed & 1 << i) != 0) {
                continue;
            }
            ProjectileEffect effect = this.behaviours.effect(i);
            try {
                if (detonation != null) {
                    effect.onDetonate(detonation);
                } else if (hook != null) {
                    hook.accept(effect, this);
                }
            } catch (RuntimeException e) {
                this.failed |= 1 << i;
                ModLogger.error("Projectile effect " + this.behaviours.effectIds().get(i) + " of weapon "
                        + this.weapon.name() + " failed; it is skipped for the rest of this projectile", e);
            }
        }
    }
}
