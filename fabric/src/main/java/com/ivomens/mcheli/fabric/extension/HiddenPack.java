package com.ivomens.mcheli.fabric.extension;

import net.minecraft.server.packs.repository.Pack;

/** NeoForge's {@code Pack.isHidden}, added to {@code Pack} by a mixin. */
public interface HiddenPack {

    boolean mcheli$isHidden();

    static boolean isHidden(Pack pack) {
        return ((HiddenPack) pack).mcheli$isHidden();
    }
}
