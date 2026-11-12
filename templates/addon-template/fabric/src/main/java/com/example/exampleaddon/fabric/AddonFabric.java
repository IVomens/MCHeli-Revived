package com.example.exampleaddon.fabric;

import com.example.exampleaddon.AddonCommon;
import net.fabricmc.api.ModInitializer;

/**
 * The Fabric entry point ({@code main} in fabric.mod.json). Fabric runs the main initializers in no set order,
 * but the MCHeli API is usable from any of them: it starts with MCHeli's classes, not with MCHeli's initializer.
 */
public final class AddonFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        AddonCommon.init();
    }
}
