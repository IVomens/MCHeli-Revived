package com.ivomens.mcheli.util;

import net.minecraft.resources.ResourceLocation;

/** MCHeli's namespace and the ids in it. */
public final class Ids {

    public static final String MOD_ID = "mcheli";

    private Ids() {
    }

    /** {@code mcheli:<path>}. */
    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
