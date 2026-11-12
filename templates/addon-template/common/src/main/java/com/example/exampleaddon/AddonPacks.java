package com.example.exampleaddon;

import com.ivomens.mcheli.api.content.ContentPackProvider;
import com.ivomens.mcheli.api.content.ContentPackRegistrar;

/**
 * Adds the jar's {@code mcheli_pack/} folder as an MCHeli content pack. MCHeli finds this class through
 * {@code META-INF/services/com.ivomens.mcheli.api.content.ContentPackProvider} while it loads its content, on
 * NeoForge and on Fabric, before the addon's own entry point runs; no other code is needed.
 */
public final class AddonPacks implements ContentPackProvider {

    @Override
    public void registerPacks(ContentPackRegistrar packs) {
        packs.addPack(AddonCommon.MOD_ID, "mcheli_pack");
    }
}
