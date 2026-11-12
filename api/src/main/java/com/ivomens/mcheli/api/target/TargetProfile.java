package com.ivomens.mcheli.api.target;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Implemented by an addon to make its entities targets of MCHeli's radar, lock-on seekers and guided missiles,
 * as if they were MCHeli vehicles. Registered with {@link TargetRegistry#register}.
 *
 * <pre>{@code
 * McheliApi.get().targets().register(MyEntities.JET.get(), jet ->
 *         ((MyJet) jet).isDroppingFlares() ? JET_FLARES : JET);
 * }</pre>
 *
 * <p><b>Side and thread.</b> {@link #signature} is called on both sides (the server snapshots radar contacts
 * and steers missiles; a player's client runs their lock-on seeker), on the thread of the entity's level, often:
 * every tick for every candidate a seeker looks at. Keep it cheap and free of allocation (return constants),
 * and base it on data both sides know (synced entity data). The two callbacks run on the side the method says.
 * A profile that throws is logged once and treated as returning null.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement this interface; new methods only ever come with a
 * default, so an implementation written now keeps compiling and working.
 */
@FunctionalInterface
public interface TargetProfile {

    /**
     * How the entity shows up to seekers right now.
     *
     * @param entity an entity this profile was registered for, never an MCHeli vehicle, seat, projectile or decoy
     * @return its signature, or null when it is no target right now (landed in a hangar, dead, cloaked): then
     *         no seeker locks it and the radar does not show it
     */
    @Nullable
    TargetSignature signature(Entity entity);

    /**
     * A seeker completed a lock on the entity, or a pilot's radar locked it beyond visual range. Called once per
     * lock, on the side where the seeker runs: the server for AI gunners and radar locks, the locking player's
     * client for a player's own lock-on weapon (which the server only learns of when a missile is fired, see
     * {@link #onMissileLaunched}). Does nothing by default.
     */
    default void onLockedOn(TargetLock lock) {
    }

    /**
     * A guided missile was launched at the entity, or turned on it in flight. Server thread only, once per
     * missile and target. Does nothing by default.
     */
    default void onMissileLaunched(IncomingMissile missile) {
    }
}
