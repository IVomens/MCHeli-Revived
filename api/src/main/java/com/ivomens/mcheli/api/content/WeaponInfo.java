package com.ivomens.mcheli.api.content;

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
}
