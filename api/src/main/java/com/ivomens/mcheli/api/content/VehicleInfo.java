package com.ivomens.mcheli.api.content;

import com.ivomens.mcheli.api.vehicle.VehicleKind;

/**
 * A loaded vehicle type, as its content file defines it. A read-only snapshot: a content reload makes new
 * ones instead of changing this one.
 *
 * <p>Immutable, so usable from any thread. Stable since API 1.0; implemented by MCHeli only, so a minor
 * version may add methods.
 */
public interface VehicleInfo {

    /** The folder the file was loaded from. */
    VehicleKind kind();

    /**
     * The file name without its extension, for example {@code ah-64}. Items, saved vehicles and other content
     * name the type by it; it is unique within the kind.
     */
    String id();

    /** The name players see, untranslated. */
    String displayName();

    /**
     * The domain of the content pack the file came from: {@code @builtin} for MCHeli's own content, the
     * domain a pack's {@code pack.mcmeta} declares, or the pack id a mod registered.
     */
    String packId();
}
