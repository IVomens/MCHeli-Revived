package com.ivomens.mcheli.combat.guidance;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.SeekerType;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.api.target.IncomingMissile;
import com.ivomens.mcheli.api.target.TargetLock;
import com.ivomens.mcheli.api.target.TargetProfile;
import com.ivomens.mcheli.api.target.TargetRegistry;
import com.ivomens.mcheli.api.target.TargetSignature;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleHitBox;
import com.ivomens.mcheli.api.vehicle.VehicleSeat;
import com.ivomens.mcheli.combat.countermeasure.ChaffEntity;
import com.ivomens.mcheli.combat.countermeasure.FlareEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The API's {@link TargetRegistry}: the entities of other mods that seekers and radar treat as targets, and the
 * entities they never target. One instance lives as long as the game, owned by the API's entry point; the
 * seekers reach it through {@link #get}.
 * <p>
 * Every change rebuilds an immutable {@link Rules} snapshot, so lookups read one volatile field and take no
 * lock. Callers on hot paths check {@link #isActive} first: with nothing registered that is the only cost.
 */
public final class ExternalTargets implements TargetRegistry {

    /** The registrations in the order they were made; guarded by {@code this}. */
    private final List<ExternalTarget> registrations = new ArrayList<>();
    private volatile Rules rules = Rules.EMPTY;

    /** The running game's registry. */
    public static ExternalTargets get() {
        return (ExternalTargets) McheliApi.get().targets();
    }

    /** Whether anything is registered or excluded at all; when not, every other lookup answers "nothing". */
    public boolean isActive() {
        return this.rules != Rules.EMPTY;
    }

    @Override
    public Subscription register(EntityType<?> type, TargetProfile profile) {
        return this.add(new ExternalTarget(Objects.requireNonNull(type, "type"), null,
                Objects.requireNonNull(profile, "profile")));
    }

    @Override
    public Subscription register(Predicate<? super Entity> matcher, TargetProfile profile) {
        return this.add(new ExternalTarget(null, Objects.requireNonNull(matcher, "matcher"),
                Objects.requireNonNull(profile, "profile")));
    }

    @Override
    public Subscription exclude(EntityType<?> type) {
        return this.add(new ExternalTarget(Objects.requireNonNull(type, "type"), null, null));
    }

    @Override
    public Subscription exclude(Predicate<? super Entity> matcher) {
        return this.add(new ExternalTarget(null, Objects.requireNonNull(matcher, "matcher"), null));
    }

    @Override
    public Optional<TargetSignature> signatureOf(Entity entity) {
        ExternalTarget registered = this.isExcluded(entity) ? null : this.find(entity);
        return registered != null ? Optional.ofNullable(registered.signature(entity)) : Optional.empty();
    }

    @Override
    public boolean isExcluded(Entity entity) {
        Rules current = this.rules;
        if (current == Rules.EMPTY) {
            return false;
        }
        if (current.excludedTypes.contains(entity.getType())) {
            return true;
        }
        for (ExternalTarget exclusion : current.exclusionMatchers) {
            if (exclusion.matches(entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The registration whose profile describes the entity, or null when none does. MCHeli's own vehicles, seats,
     * projectiles and decoys never have one. Does not look at exclusions: callers check {@link #isExcluded} first.
     */
    @Nullable
    public ExternalTarget find(Entity entity) {
        Rules current = this.rules;
        if (current == Rules.EMPTY || isOwnEntity(entity)) {
            return null;
        }
        ExternalTarget byType = current.profilesByType.get(entity.getType());
        if (byType != null) {
            return byType;
        }
        for (ExternalTarget registered : current.profileMatchers) {
            if (registered.matches(entity)) {
                return registered;
            }
        }
        return null;
    }

    /** The signature of a registered entity that is not excluded; null when it has none right now. */
    @Nullable
    public TargetSignature signature(Entity entity) {
        if (!this.isActive() || this.isExcluded(entity)) {
            return null;
        }
        ExternalTarget registered = this.find(entity);
        return registered != null ? registered.signature(entity) : null;
    }

    /** A seeker completed a lock: tells the target's profile, if it has one. */
    public void lockedOn(Entity user, Entity target, @Nullable Vehicle vehicle, SeekerType seeker) {
        if (!this.isActive() || this.isExcluded(target)) {
            return;
        }
        ExternalTarget registered = this.find(target);
        if (registered != null) {
            registered.lockedOn(new Lock(target, user, vehicle, seeker));
        }
    }

    /** Server: a guided missile turned on the target: tells the target's profile, if it has one. */
    public void missileLaunched(ProjectileEntity missile, Entity target) {
        if (!this.isActive()) {
            return;
        }
        ExternalTarget registered = this.find(target);
        if (registered != null) {
            registered.missileLaunched(new Missile(target, missile, missile.shooter(), missile.weaponName(),
                    seekerOf(ThreatSeeker.of(missile.spec()))));
        }
    }

    /** What a lock-on seeker with this filter locks with. */
    public static SeekerType seekerOf(TargetFilter filter) {
        if (filter.heatSeeking()) {
            return filter.radar() ? SeekerType.DUAL : SeekerType.HEAT;
        }
        return filter.radar() ? SeekerType.RADAR : SeekerType.OTHER;
    }

    /** What a missile of this seeker locks with. */
    public static SeekerType seekerOf(ThreatSeeker seeker) {
        return switch (seeker) {
            case HEAT -> SeekerType.HEAT;
            case RADAR -> SeekerType.RADAR;
            case DUAL -> SeekerType.DUAL;
            case NONE, OTHER -> SeekerType.OTHER;
        };
    }

    private static boolean isOwnEntity(Entity entity) {
        return entity instanceof Vehicle || entity instanceof VehicleSeat || entity instanceof VehicleHitBox
                || entity instanceof ProjectileEntity || entity instanceof FlareEntity || entity instanceof ChaffEntity;
    }

    private synchronized Subscription add(ExternalTarget registration) {
        this.registrations.add(registration);
        this.rules = Rules.of(this.registrations);
        return () -> this.remove(registration);
    }

    private synchronized void remove(ExternalTarget registration) {
        if (this.registrations.remove(registration)) {
            this.rules = Rules.of(this.registrations);
        }
    }

    /** The registrations sorted for lookup; {@link #EMPTY} exactly when there are none. */
    private record Rules(Map<EntityType<?>, ExternalTarget> profilesByType, List<ExternalTarget> profileMatchers,
                         Set<EntityType<?>> excludedTypes, List<ExternalTarget> exclusionMatchers) {

        static final Rules EMPTY = new Rules(Map.of(), List.of(), Set.of(), List.of());

        static Rules of(List<ExternalTarget> registrations) {
            if (registrations.isEmpty()) {
                return EMPTY;
            }
            Map<EntityType<?>, ExternalTarget> byType = new IdentityHashMap<>();
            List<ExternalTarget> matchers = new ArrayList<>();
            Set<EntityType<?>> excluded = Collections.newSetFromMap(new IdentityHashMap<>());
            List<ExternalTarget> exclusionMatchers = new ArrayList<>();
            for (ExternalTarget registration : registrations) {
                EntityType<?> type = registration.type();
                if (registration.isExclusion()) {
                    if (type != null) {
                        excluded.add(type);
                    } else {
                        exclusionMatchers.add(registration);
                    }
                } else if (type != null) {
                    // the earliest registration for a type wins
                    byType.putIfAbsent(type, registration);
                } else {
                    matchers.add(registration);
                }
            }
            return new Rules(byType, List.copyOf(matchers), excluded, List.copyOf(exclusionMatchers));
        }
    }

    private record Lock(Entity target, Entity user, @Nullable Vehicle vehicle, SeekerType seeker)
            implements TargetLock {
    }

    private record Missile(Entity target, Entity missile, @Nullable Entity shooter, String weaponId,
                           SeekerType seeker) implements IncomingMissile {
    }
}
