package com.ivomens.mcheli.example;

import com.ivomens.mcheli.api.content.ContentPackProvider;
import com.ivomens.mcheli.api.content.ContentPackRegistrar;

/**
 * Adds the jar's {@code mcheli_pack/} folder as an MCHeli content pack. MCHeli finds this class through
 * {@code META-INF/services/com.ivomens.mcheli.api.content.ContentPackProvider} while it loads its content,
 * before this mod is constructed, so registering needs no code in {@link ExampleAddon}.
 */
public final class ExamplePacks implements ContentPackProvider {

    @Override
    public void registerPacks(ContentPackRegistrar packs) {
        packs.addPack(ExampleAddon.MOD_ID, "mcheli_pack");
    }
}
