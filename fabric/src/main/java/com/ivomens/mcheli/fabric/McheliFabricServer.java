package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.McheliMod;
import net.fabricmc.api.DedicatedServerModInitializer;

/**
 * Dedicated server entry point. Fabric runs it after every mod's main initializer, on the main thread:
 * the point NeoForge's load-complete event stands for.
 */
public final class McheliFabricServer implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        McheliMod.onLoadComplete();
    }
}
