package com.ivomens.mcheli.combat.projectile.behaviour;

import com.ivomens.mcheli.api.weapon.GuidanceLaw;
import com.ivomens.mcheli.api.weapon.ProjectileEffect;
import com.ivomens.mcheli.content.def.weapon.BehaviourRef;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The addon behaviours of one weapon file, made when it loads ({@link WeaponBehaviours#resolve}) and shared by all
 * its projectiles. Only weapons that name a behaviour have one.
 */
public final class WeaponBehaviourSet {

    /** The most effects one weapon runs; a projectile keeps one bit per effect for those that failed. */
    public static final int MAX_EFFECTS = 31;

    @Nullable
    private final ResourceLocation guidanceId;
    @Nullable
    private final GuidanceLaw guidance;
    private final List<ResourceLocation> effectIds;
    /** An array, so the per-tick loops over it allocate no iterator. */
    private final ProjectileEffect[] effects;
    private final List<BehaviourRef> named;

    WeaponBehaviourSet(@Nullable ResourceLocation guidanceId, @Nullable GuidanceLaw guidance,
                       List<ResourceLocation> effectIds, List<ProjectileEffect> effects, List<BehaviourRef> named) {
        this.guidanceId = guidanceId;
        this.guidance = guidance;
        this.effectIds = List.copyOf(effectIds);
        this.effects = effects.toArray(new ProjectileEffect[0]);
        this.named = List.copyOf(named);
    }

    /** The registered guidance law the file picks, or null when the weapon keeps the guidance of its type. */
    @Nullable
    public GuidanceLaw guidance() {
        return this.guidance;
    }

    /** The id of {@link #guidance()}, or null. */
    @Nullable
    public ResourceLocation guidanceId() {
        return this.guidance != null ? this.guidanceId : null;
    }

    /** The ids of the registered effects, in the order they run. */
    public List<ResourceLocation> effectIds() {
        return this.effectIds;
    }

    public int effectCount() {
        return this.effects.length;
    }

    public ProjectileEffect effect(int index) {
        return this.effects[index];
    }

    /** Every behaviour the file names, the guidance first, registered or not: what client visuals are looked up by. */
    public List<BehaviourRef> named() {
        return this.named;
    }
}
