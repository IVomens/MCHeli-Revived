package com.example.exampleaddon;

import com.ivomens.mcheli.api.McheliApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The loader-independent part of the addon. Each loader's entry point calls {@link #init()} once, while the
 * mod is constructed (NeoForge) or initialised (Fabric). MCHeli is loaded by then: the API is ready.
 */
public final class AddonCommon {

    /** The mod id, as in neoforge.mods.toml and fabric.mod.json. */
    public static final String MOD_ID = "exampleaddon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private AddonCommon() {
    }

    /** Registers the addon's MCHeli event listeners. The content pack needs no call: see {@link AddonPacks}. */
    public static void init() {
        McheliApi api = McheliApi.get();
        LOGGER.info("{} found the MCHeli API {}", MOD_ID, api.apiVersion());
        AddonEvents.register(api.events());
    }
}
