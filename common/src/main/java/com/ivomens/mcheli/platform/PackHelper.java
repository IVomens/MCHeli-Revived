package com.ivomens.mcheli.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;

/** Pack metadata fields the loader adds. */
public interface PackHelper {

    /**
     * Metadata of a built-in pack that is compatible with the running version, requests no feature
     * flags and has no overlays. The pack is hidden from the pack selection screen and the saved pack
     * list where the loader supports that.
     */
    Pack.Metadata hiddenPackMetadata(Component description);
}
