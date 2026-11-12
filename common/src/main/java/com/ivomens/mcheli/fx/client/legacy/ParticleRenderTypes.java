package com.ivomens.mcheli.fx.client.legacy;

import com.ivomens.mcheli.util.Ids;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

/**
 * Particle render types for the mod's stand-alone particle textures (they are not on the particle
 * atlas). NeoForge's particle engine renders custom render types after the vanilla ones.
 */
public final class ParticleRenderTypes {

    public static final ResourceLocation SMOKE_TEXTURE = Ids.of("textures/particles/smoke.png");
    public static final ResourceLocation FLASH_TEXTURE = Ids.of("textures/flash.png");

    public static final ParticleRenderType SMOKE = translucent(SMOKE_TEXTURE, "MCHELI_SMOKE");
    public static final ParticleRenderType FLASH = translucent(FLASH_TEXTURE, "MCHELI_FLASH");

    private ParticleRenderTypes() {
    }

    private static ParticleRenderType translucent(ResourceLocation texture, String name) {
        return new ParticleRenderType() {
            @Override
            public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
                RenderSystem.depthMask(true);
                RenderSystem.setShaderTexture(0, texture);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }
}
