package com.ivomens.mcheli.inspector.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** The key that opens the vehicle inspector. Unbound by default; players pick one in Controls. */
public final class InspectorKeys {

    public static final KeyMapping OPEN = new KeyMapping("key.mcheli.inspector", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.mcheli");

    private InspectorKeys() {
    }
}
