package com.ivomens.mcheli.api.content;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * A loaded weapon type, as its content file defines it. A read-only snapshot: a content reload makes new
 * ones instead of changing this one.
 *
 * <p>Immutable, so usable from any thread. Stable since API 1.0; implemented by MCHeli only, so a minor
 * version may add methods.
 */
public interface WeaponInfo {

    /** The file name without its extension, for example {@code m230}; vehicle files name their weapons by it. */
    String id();

    /** The name players see, untranslated. */
    String displayName();

    /**
     * The weapon's type as its file sets it ({@code Type}), which picks how it fires: {@code machinegun1},
     * {@code rocket}, {@code aamissile} and so on; see docs/CONTENT_SCHEMA.md.
     */
    String type();

    /** The domain of the content pack the file came from, as {@link VehicleInfo#packId()}. */
    String packId();

    /**
     * The addon guidance law the file picks with {@code Guidance}, when one is registered for that id
     * ({@link com.ivomens.mcheli.api.weapon.GuidanceLaw}); empty when the weapon steers as its {@link #type()} does.
     * Since API 1.1.
     */
    Optional<ResourceLocation> guidance();

    /**
     * The addon projectile effects the file lists under {@code ProjectileEffects} that are registered, in the order
     * they run ({@link com.ivomens.mcheli.api.weapon.ProjectileEffect}); empty when there are none. Since API 1.1.
     */
    List<ResourceLocation> projectileEffects();
}
