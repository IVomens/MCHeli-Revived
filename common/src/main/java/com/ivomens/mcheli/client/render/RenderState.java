package com.ivomens.mcheli.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Colour, texture and switches of a frame. The render type they select is cached until one of
 * them changes, so emitting many parts in the same state does not repeat the lookup.
 */
final class RenderState {

    /** Alpha below this counts as translucent. */
    private static final float OPAQUE_ALPHA = 0.999F;


    ResourceLocation texture;
    float r, g, b, a;
    boolean blend, additive, fullBright, textured, depthMask, cull;
    private RenderType renderType;

    RenderState() {
        this.reset();
    }

    void reset() {
        this.texture = LegacyGL.WHITE;
        this.r = this.g = this.b = this.a = 1.0F;
        this.blend = this.additive = this.fullBright = false;
        this.textured = this.depthMask = this.cull = true;
        this.renderType = null;
    }

    void set(RenderState other) {
        this.texture = other.texture;
        this.r = other.r;
        this.g = other.g;
        this.b = other.b;
        this.a = other.a;
        this.blend = other.blend;
        this.additive = other.additive;
        this.fullBright = other.fullBright;
        this.textured = other.textured;
        this.depthMask = other.depthMask;
        this.cull = other.cull;
        this.renderType = other.renderType;
    }

    void setTexture(ResourceLocation texture) {
        if (texture != this.texture) {
            this.texture = texture;
            this.renderType = null;
        }
    }

    void setColor(float r, float g, float b, float a) {
        if ((a < OPAQUE_ALPHA) != (this.a < OPAQUE_ALPHA)) {
            this.renderType = null;
        }
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    void setBlend(boolean blend) {
        if (blend != this.blend) {
            this.blend = blend;
            this.renderType = null;
        }
    }

    void setAdditive(boolean additive) {
        if (additive != this.additive) {
            this.additive = additive;
            this.renderType = null;
        }
    }

    void setFullBright(boolean fullBright) {
        if (fullBright != this.fullBright) {
            this.fullBright = fullBright;
            this.renderType = null;
        }
    }

    void setTextured(boolean textured) {
        if (textured != this.textured) {
            this.textured = textured;
            this.renderType = null;
        }
    }

    void setDepthMask(boolean depthMask) {
        if (depthMask != this.depthMask) {
            this.depthMask = depthMask;
            this.renderType = null;
        }
    }

    void setCull(boolean cull) {
        if (cull != this.cull) {
            this.cull = cull;
            this.renderType = null;
        }
    }

    /** ARGB colour with each channel truncated to a byte, exactly as the buffer builders store it. */
    int packedColor() {
        return ((int) (this.a * 255.0F) & 0xFF) << 24
                | ((int) (this.r * 255.0F) & 0xFF) << 16
                | ((int) (this.g * 255.0F) & 0xFF) << 8
                | ((int) (this.b * 255.0F) & 0xFF);
    }

    RenderType renderType() {
        if (this.renderType == null) {
            this.renderType = this.selectRenderType();
        }
        return this.renderType;
    }

    private RenderType selectRenderType() {
        ResourceLocation tex = this.textured ? this.texture : LegacyGL.WHITE;
        if (this.additive) {
            return McheliRenderTypes.entityAdditive(tex);
        }
        if (!this.depthMask) {
            return this.fullBright ? RenderType.entityTranslucentEmissive(tex)
                    : McheliRenderTypes.entityTranslucentNoDepthWrite(tex);
        }
        if (this.blend || this.a < OPAQUE_ALPHA) {
            return McheliRenderTypes.entityTranslucentUnsorted(tex, this.cull);
        }
        return this.cull ? RenderType.entityCutout(tex) : RenderType.entityCutoutNoCull(tex);
    }
}
