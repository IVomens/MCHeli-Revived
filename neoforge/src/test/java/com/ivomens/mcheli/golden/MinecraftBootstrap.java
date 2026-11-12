package com.ivomens.mcheli.golden;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Fills the built-in registries (items, blocks, effects...) so content parsing can look names up. */
public final class MinecraftBootstrap {

    private MinecraftBootstrap() {
    }

    public static synchronized void ensure() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
}
