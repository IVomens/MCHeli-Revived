package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.api.content.ContentPackProvider;
import com.ivomens.mcheli.api.content.ContentPackRegistrar;
import com.ivomens.mcheli.platform.Services;

/**
 * Registers the game tests' own content pack through the public API, as an addon would: the
 * {@code api_test_pack} folder of the game test files, which holds one weapon, {@code api_test_gun}.
 * {@link ApiGameTests} checks that it loaded.
 */
public final class TestContentPacks implements ContentPackProvider {

    /** The pack's folder among the game test resources. */
    static final String FOLDER = "api_test_pack";
    /** The weapon the pack adds. */
    static final String WEAPON = "api_test_gun";

    /** The mod whose files hold the game tests: Fabric loads them as a mod of their own, NeoForge as part of MCHeli. */
    static String modId() {
        return Services.PLATFORM.isModLoaded("mcheli_gametest") ? "mcheli_gametest" : "mcheli";
    }

    @Override
    public void registerPacks(ContentPackRegistrar packs) {
        packs.addPack(modId(), FOLDER);
    }
}
