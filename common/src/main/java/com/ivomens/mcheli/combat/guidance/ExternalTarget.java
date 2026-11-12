package com.ivomens.mcheli.combat.guidance;

import com.ivomens.mcheli.api.target.IncomingMissile;
import com.ivomens.mcheli.api.target.TargetLock;
import com.ivomens.mcheli.api.target.TargetProfile;
import com.ivomens.mcheli.api.target.TargetSignature;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * One registration of {@link ExternalTargets}: an addon's profile or exclusion, for an entity type or a predicate.
 * Calls into the addon go through here, so a profile or predicate that throws is logged once and then ignored
 * quietly instead of on every tick.
 */
public final class ExternalTarget {

    @Nullable
    private final EntityType<?> type;
    @Nullable
    private final Predicate<? super Entity> matcher;
    /** The profile; null for an exclusion. */
    @Nullable
    private final TargetProfile profile;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    ExternalTarget(@Nullable EntityType<?> type, @Nullable Predicate<? super Entity> matcher,
                   @Nullable TargetProfile profile) {
        this.type = type;
        this.matcher = matcher;
        this.profile = profile;
    }

    @Nullable
    EntityType<?> type() {
        return this.type;
    }

    boolean isExclusion() {
        return this.profile == null;
    }

    /** Whether the predicate of a registration by predicate accepts the entity. */
    boolean matches(Entity entity) {
        try {
            return this.matcher != null && this.matcher.test(entity);
        } catch (RuntimeException e) {
            this.logFailure("target predicate", e);
            return false;
        }
    }

    /** The profile's signature for the entity; null when it is no target right now, or the profile failed. */
    @Nullable
    public TargetSignature signature(Entity entity) {
        if (this.profile == null) {
            return null;
        }
        try {
            return this.profile.signature(entity);
        } catch (RuntimeException e) {
            this.logFailure("target profile", e);
            return null;
        }
    }

    void lockedOn(TargetLock lock) {
        if (this.profile == null) {
            return;
        }
        try {
            this.profile.onLockedOn(lock);
        } catch (RuntimeException e) {
            ModLogger.error("A target profile failed in onLockedOn", e);
        }
    }

    void missileLaunched(IncomingMissile missile) {
        if (this.profile == null) {
            return;
        }
        try {
            this.profile.onMissileLaunched(missile);
        } catch (RuntimeException e) {
            ModLogger.error("A target profile failed in onMissileLaunched", e);
        }
    }

    private void logFailure(String what, RuntimeException e) {
        if (this.failureLogged.compareAndSet(false, true)) {
            ModLogger.error("A " + what + " failed; it counts as no match while it fails, logged only once", e);
        }
    }
}
