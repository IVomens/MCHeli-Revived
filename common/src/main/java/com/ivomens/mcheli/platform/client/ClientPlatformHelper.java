package com.ivomens.mcheli.platform.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Client-only loader services. Reached through {@link ClientServices}, never from server code. */
public interface ClientPlatformHelper {

    /**
     * Whether the key triggers the mapping right now, the way the loader's own screens check it.
     * Vanilla: the key equals the mapping's key.
     */
    boolean isActiveAndMatches(KeyMapping mapping, InputConstants.Key key);
}
