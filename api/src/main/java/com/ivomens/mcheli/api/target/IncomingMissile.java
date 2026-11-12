package com.ivomens.mcheli.api.target;

import com.ivomens.mcheli.api.event.SeekerType;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * A guided missile homing on an addon's target, handed to {@link TargetProfile#onMissileLaunched}. The call
 * object is only valid while that call runs; the {@link #missile()} entity may be kept and watched until it is
 * removed (it hit, missed, or was decoyed).
 *
 * <p>Server thread. Since API 1.1; implemented by MCHeli only, so a minor version may add methods.
 */
public interface IncomingMissile {

    /** What the missile homes on: the entity the profile was registered for. */
    Entity target();

    /** The missile entity. */
    Entity missile();

    /** Who fired it, or null when unknown. */
    @Nullable
    Entity shooter();

    /** The id of the weapon type that fired it, as in {@code ContentLookup.weapon(id)}. */
    String weaponId();

    /** What the missile seeks with, which says whether flares or chaff decoy it. */
    SeekerType seeker();
}
