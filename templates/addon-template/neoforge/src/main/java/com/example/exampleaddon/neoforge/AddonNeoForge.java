package com.example.exampleaddon.neoforge;

import com.example.exampleaddon.AddonCommon;
import net.neoforged.fml.common.Mod;

/** The NeoForge entry point. MCHeli is a required dependency (neoforge.mods.toml), so it is loaded first. */
@Mod(AddonCommon.MOD_ID)
public final class AddonNeoForge {

    public AddonNeoForge() {
        AddonCommon.init();
    }
}
