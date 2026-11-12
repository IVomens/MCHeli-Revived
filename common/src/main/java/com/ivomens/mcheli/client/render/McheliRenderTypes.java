package com.ivomens.mcheli.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.OptionalDouble;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Render types the stock {@link RenderType} set does not cover but {@link LegacyGL} drawing
 * needs: real additive blending (GL_SRC_ALPHA, GL_ONE), untextured coloured geometry with and
 * without depth testing, and lines with an explicit width.
 * <p>
 * All types are created once and cached; nothing here allocates per frame. Must be used on the
 * render thread.
 */
public final class McheliRenderTypes {

    private McheliRenderTypes() {
    }

    /** Textured, additive (GL_SRC_ALPHA, GL_ONE), no depth write, no cull. Used for glows, flames, lasers. */
    private static final Function<ResourceLocation, RenderType> ENTITY_ADDITIVE = Util.memoize(tex -> RenderType.create(
            "mcheli_entity_additive", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false)));

    /** Textured translucent, no depth write ({@code LegacyGL.depthMask(false)} with normal blending). */
    private static final Function<ResourceLocation, RenderType> ENTITY_TRANSLUCENT_NO_DEPTH_WRITE = Util.memoize(tex -> RenderType.create(
            "mcheli_entity_translucent_nodepthwrite", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false)));

    /**
     * Textured translucent with depth write, drawn in the order it was emitted. The stock
     * translucent entity types sort every quad in the batch by distance before each upload, which
     * with a field of parked vehicles was close to half the frame. The original mod never sorted
     * either: glass is drawn after the hull it sits in, so it blends correctly without it.
     */
    private static final BiFunction<ResourceLocation, Boolean, RenderType> ENTITY_TRANSLUCENT_UNSORTED = Util.memoize((tex, cull) -> RenderType.create(
            cull ? "mcheli_entity_translucent_cull" : "mcheli_entity_translucent", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, true, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(cull ? RenderStateShard.CULL : RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(true)));

    private static final RenderType COLOR_TRANSLUCENT = colorType("mcheli_color_translucent",
            RenderStateShard.TRANSLUCENT_TRANSPARENCY, RenderStateShard.COLOR_DEPTH_WRITE, false);
    private static final RenderType COLOR_TRANSLUCENT_NO_DEPTH = colorType("mcheli_color_translucent_nodepth",
            RenderStateShard.TRANSLUCENT_TRANSPARENCY, RenderStateShard.COLOR_WRITE, true);
    private static final RenderType COLOR_ADDITIVE = colorType("mcheli_color_additive",
            RenderStateShard.LIGHTNING_TRANSPARENCY, RenderStateShard.COLOR_WRITE, false);
    private static final RenderType COLOR_ADDITIVE_NO_DEPTH = colorType("mcheli_color_additive_nodepth",
            RenderStateShard.LIGHTNING_TRANSPARENCY, RenderStateShard.COLOR_WRITE, true);

    /** Line widths are rounded to quarter pixels; types up to this many quarters are kept in arrays. */
    private static final int CACHED_LINE_WIDTHS = 64;
    private static final RenderType[] LINES = new RenderType[CACHED_LINE_WIDTHS];
    private static final RenderType[] LINES_NO_DEPTH = new RenderType[CACHED_LINE_WIDTHS];
    private static final Function<Float, RenderType> WIDE_LINES = Util.memoize(w -> lineType("mcheli_lines", w, false));
    private static final Function<Float, RenderType> WIDE_LINES_NO_DEPTH = Util.memoize(w -> lineType("mcheli_lines_nodepth", w, true));

    private static RenderType colorType(String name, RenderStateShard.TransparencyStateShard transparency,
                                        RenderStateShard.WriteMaskStateShard writeMask, boolean noDepth) {
        RenderType.CompositeState.CompositeStateBuilder builder = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                .setTransparencyState(transparency)
                .setCullState(RenderStateShard.NO_CULL)
                .setWriteMaskState(writeMask);
        if (noDepth) {
            builder.setDepthTestState(RenderStateShard.NO_DEPTH_TEST);
        }
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
                builder.createCompositeState(false));
    }

    private static RenderType lineType(String name, float width, boolean noDepth) {
        RenderType.CompositeState.CompositeStateBuilder builder = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                .setLineState(new RenderStateShard.LineStateShard(width > 0 ? OptionalDouble.of(width) : OptionalDouble.empty()))
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                .setWriteMaskState(noDepth ? RenderStateShard.COLOR_WRITE : RenderStateShard.COLOR_DEPTH_WRITE)
                .setCullState(RenderStateShard.NO_CULL);
        if (noDepth) {
            builder.setDepthTestState(RenderStateShard.NO_DEPTH_TEST);
        }
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
                builder.createCompositeState(false));
    }

    public static RenderType entityAdditive(ResourceLocation texture) {
        return ENTITY_ADDITIVE.apply(texture);
    }

    public static RenderType entityTranslucentUnsorted(ResourceLocation texture, boolean cull) {
        return ENTITY_TRANSLUCENT_UNSORTED.apply(texture, cull);
    }

    public static RenderType entityTranslucentNoDepthWrite(ResourceLocation texture) {
        return ENTITY_TRANSLUCENT_NO_DEPTH_WRITE.apply(texture);
    }

    /** Untextured coloured quads (POSITION_COLOR). */
    public static RenderType color(boolean additive, boolean noDepth) {
        if (additive) {
            return noDepth ? COLOR_ADDITIVE_NO_DEPTH : COLOR_ADDITIVE;
        }
        return noDepth ? COLOR_TRANSLUCENT_NO_DEPTH : COLOR_TRANSLUCENT;
    }

    /** Lines with an explicit width in pixels ({@code <= 0} picks the vanilla default width). */
    public static RenderType lines(float width, boolean noDepth) {
        int quarters = Math.round(width * 4.0F);
        if (quarters < 0 || quarters >= CACHED_LINE_WIDTHS) {
            Float key = quarters / 4.0F;
            return noDepth ? WIDE_LINES_NO_DEPTH.apply(key) : WIDE_LINES.apply(key);
        }
        RenderType[] cache = noDepth ? LINES_NO_DEPTH : LINES;
        RenderType type = cache[quarters];
        if (type == null) {
            type = lineType(noDepth ? "mcheli_lines_nodepth" : "mcheli_lines", quarters / 4.0F, noDepth);
            cache[quarters] = type;
        }
        return type;
    }
}
