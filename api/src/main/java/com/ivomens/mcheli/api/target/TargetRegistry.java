package com.ivomens.mcheli.api.target;

import com.ivomens.mcheli.api.event.Subscription;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Which entities of other mods MCHeli's radar, lock-on seekers and guided missiles treat as targets, and which
 * entities they never target.
 *
 * <pre>{@code
 * TargetRegistry targets = McheliApi.get().targets();
 * targets.register(MyEntities.JET.get(), new JetProfile());
 * targets.exclude(EntityType.ARMOR_STAND);
 * }</pre>
 *
 * <p><b>What a registered entity gets.</b> The radar display shows it (as {@link TargetSignature#shownAs()});
 * lock-on seekers, AI gunners and target designation may lock it, subject to its {@link TargetDomain}, its
 * signatures and decoys (see {@link TargetSignature}); guided missiles fly at it and are decoyed by its flares
 * and chaff; and its profile hears of locks and missiles ({@link TargetProfile#onLockedOn},
 * {@link TargetProfile#onMissileLaunched}), so the addon can warn its own crew. MCHeli's own warning receiver
 * and missile alarm are drawn by MCHeli vehicles only.
 *
 * <p><b>Which rule wins.</b> An exclusion beats every registration. Registrations by type are looked at before
 * those by predicate; among several for the same type, or several matching predicates, the earliest still
 * registered wins. MCHeli's own vehicles, seats, projectiles and decoys keep their own rules and never use a
 * profile, but can be excluded. Without any registration nothing changes, and seekers pay nothing for the
 * registry; a registration by type costs one map lookup per entity a seeker looks at, a predicate one call.
 *
 * <p><b>Thread.</b> Registering and unsubscribing work from any thread, at any time; they apply to the next
 * lookup. Registrations last until unsubscribed, for as long as the game runs, on both sides: register in your
 * mod's constructor or common setup, so the server and every client agree.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface TargetRegistry {

    /**
     * Makes every entity of the type a target, with the signature the profile gives.
     *
     * @return the handle that removes the registration again
     */
    Subscription register(EntityType<?> type, TargetProfile profile);

    /**
     * Makes every entity the predicate accepts a target, with the signature the profile gives. Use it when only
     * some entities of a type are targets, or the types are not known up front; the predicate runs for every
     * entity a seeker looks at, on the level's thread, so keep it cheap.
     *
     * @return the handle that removes the registration again
     */
    Subscription register(Predicate<? super Entity> matcher, TargetProfile profile);

    /**
     * Hides every entity of the type from MCHeli's radar display, lock-on seekers and AI gunners, whatever else
     * says it is a target. A missile already homing on one keeps flying.
     *
     * @return the handle that lifts the exclusion again
     */
    Subscription exclude(EntityType<?> type);

    /**
     * Hides every entity the predicate accepts, as {@link #exclude(EntityType)} does. The predicate runs for every
     * entity a seeker looks at, on the level's thread.
     *
     * @return the handle that lifts the exclusion again
     */
    Subscription exclude(Predicate<? super Entity> matcher);

    /**
     * The signature MCHeli sees for the entity right now: the winning profile's answer. Empty when no
     * registration covers the entity, when it is excluded or an MCHeli vehicle, or when its profile returns null.
     * On the entity's level thread.
     */
    Optional<TargetSignature> signatureOf(Entity entity);

    /** Whether an exclusion hides the entity from MCHeli's seekers and radar. On the entity's level thread. */
    boolean isExcluded(Entity entity);
}
