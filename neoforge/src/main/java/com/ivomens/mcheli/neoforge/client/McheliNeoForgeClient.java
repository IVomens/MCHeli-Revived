package com.ivomens.mcheli.neoforge.client;

import com.ivomens.mcheli.util.Ids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge's client entry point. FML constructs it on the physical client only, after the common entry
 * point ({@code McheliNeoForge}), so the common code is initialised by the time the client wiring runs.
 */
@Mod(value = Ids.MOD_ID, dist = Dist.CLIENT)
public final class McheliNeoForgeClient {

    public McheliNeoForgeClient(IEventBus modBus) {
        NeoForgeClientSetup.register(modBus);
    }
}
