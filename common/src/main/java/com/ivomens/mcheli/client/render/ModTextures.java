package com.ivomens.mcheli.client.render;

import com.ivomens.mcheli.util.Ids;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Textures under {@code assets/mcheli/}, by path. */
public final class ModTextures {

    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();

    private ModTextures() {
    }

    /** Makes the texture the current shader texture 0 and returns its location. */
    public static ResourceLocation bind(String path) {
        ResourceLocation location = TEXTURES.computeIfAbsent(path, Ids::of);
        RenderSystem.setShaderTexture(0, location);
        return location;
    }
}
