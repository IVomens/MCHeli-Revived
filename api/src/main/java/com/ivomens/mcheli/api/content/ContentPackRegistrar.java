package com.ivomens.mcheli.api.content;

/**
 * What a {@link ContentPackProvider} registers its packs with.
 *
 * <p>A pack is a folder inside the mod's jar laid out like a pack in {@code mcheli_addons/}: content files
 * under {@code assets/mcheli/<type folder>/} (for example {@code assets/mcheli/weapons/my_gun.yml}),
 * textures, models and sounds next to them, and an optional {@code pack.mcmeta}. It loads through the normal
 * content pipeline: after MCHeli's bundled content and before the packs in {@code mcheli_addons/}, so a
 * mod's file replaces a bundled one of the same type and name, and a player's pack can still replace the
 * mod's. Packs of several mods load in order of mod id. docs/ADDON_AUTHORING.md describes the layout and
 * the override rules.
 *
 * <p>Thread: only valid during {@link ContentPackProvider#registerPacks}. Stability: stable since API 1.0;
 * implemented by MCHeli only, so a minor version may add methods.
 */
public interface ContentPackRegistrar {

    /**
     * Adds the folder {@code folder} of mod {@code modId}'s files as a content pack, for example
     * {@code addPack("mymod", "mcheli_pack")}. Use {@code ""} for the jar's root. The pack's domain (its id in
     * logs, reports and {@link VehicleInfo#packId()}) is the one its {@code pack.mcmeta} declares, else the
     * mod id, followed by {@code .} and the folder's name for the second and later pack of the same mod. A mod
     * that is not loaded or a folder that does not exist is reported in the log and skipped.
     */
    void addPack(String modId, String folder);
}
