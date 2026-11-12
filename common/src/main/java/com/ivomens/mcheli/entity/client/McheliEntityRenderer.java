package com.ivomens.mcheli.entity.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.util.Ids;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Base of the mod's entity renderers.
 * <p>
 * {@link #render} opens a {@link LegacyGL} frame on the pose stack handed in by vanilla (already
 * translated to the entity's interpolated position) and calls
 * {@link #doRender(Entity, double, double, double, float, float)} with a zero offset, so renderers
 * can be written in the immediate-mode style with explicit {@code translate(x, y, z)} calls.
 */
public abstract class McheliEntityRenderer<T extends Entity> extends EntityRenderer<T> {

    protected static final ResourceLocation TEX_DEFAULT = Ids.of("textures/default.png");
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();
    private static final Map<String, Map<String, ResourceLocation>> DIR_TEXTURE_CACHE = new HashMap<>();
    /** Vehicle models can be far bigger than their collision box; this many radii stay in render range. */
    private static final double RENDER_DISTANCE_IN_RADII = 64.0;

    /** Packed light of the entity currently being rendered. */
    protected int packedLight;

    private final ThermalVision thermal;

    /** @param thermal replaces the textures this renderer binds while the player looks through a thermal camera */
    protected McheliEntityRenderer(EntityRendererProvider.Context context, ThermalVision thermal) {
        super(context);
        this.thermal = thermal;
    }

    /** The client's thermal camera texture replacement. */
    protected ThermalVision thermal() {
        return this.thermal;
    }

    /** Cached {@code mcheli:<path>} location; render thread only. */
    public static ResourceLocation texture(String path) {
        ResourceLocation rl = TEXTURE_CACHE.get(path);
        if (rl == null) {
            rl = Ids.of(path);
            TEXTURE_CACHE.put(path, rl);
        }
        return rl;
    }

    /** Cached {@code mcheli:<dir><name>.png} location without building the string every frame. */
    public static ResourceLocation texture(String dir, String name) {
        Map<String, ResourceLocation> m = DIR_TEXTURE_CACHE.computeIfAbsent(dir, k -> new HashMap<>());
        ResourceLocation rl = m.get(name);
        if (rl == null) {
            rl = Ids.of(dir + name + ".png");
            m.put(name, rl);
        }
        return rl;
    }

    @Override
    public void render(@NotNull T entity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffers, int packedLight) {
        this.packedLight = packedLight;
        LegacyGL.begin(poseStack, buffers, packedLight);
        try {
            this.doRender(entity, 0.0, 0.0, 0.0, entityYaw, partialTicks);
        } finally {
            LegacyGL.end();
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    /** Draws the entity inside an open {@link LegacyGL} frame; {@code x, y, z} are always 0 (the pose is at the entity). */
    public abstract void doRender(@NotNull T entity, double x, double y, double z, float entityYaw, float partialTicks);

    /**
     * Frustum test with a sphere-like box of the given radius around the entity, for entities whose
     * model is much bigger than their collision box.
     */
    protected static boolean isVisible(Entity entity, Frustum frustum, double radius, double camX, double camY, double camZ) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        double size = Math.max(radius, Math.max(entity.getBbWidth(), entity.getBbHeight()));
        double maxDistance = size * RENDER_DISTANCE_IN_RADII * Entity.getViewScale();
        double dx = x - camX;
        double dy = y - camY;
        double dz = z - camZ;
        if (dx * dx + dy * dy + dz * dz >= maxDistance * maxDistance && !entity.shouldRender(camX, camY, camZ)) {
            return false;
        }
        AABB bounds = entity.getBoundingBoxForCulling();
        double margin = 0.5;
        if (radius <= 0.0) {
            return frustum.isVisible(bounds.inflate(margin));
        }
        return frustum.isVisible(new AABB(
                Math.min(bounds.minX - margin, x - radius), Math.min(bounds.minY - margin, y - radius),
                Math.min(bounds.minZ - margin, z - radius), Math.max(bounds.maxX + margin, x + radius),
                Math.max(bounds.maxY + margin, y + radius), Math.max(bounds.maxZ + margin, z + radius)));
    }

    protected void bindTexture(String path) {
        this.bindTexture(texture(path));
    }

    protected void bindTexture(ResourceLocation location) {
        LegacyGL.bindTexture(this.thermal.remap(location));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull T entity) {
        return TEX_DEFAULT;
    }

    /** Lit, culled, blended drawing in the usual model shade. */
    protected void setCommonRenderParam() {
        LegacyGL.enableLighting();
        LegacyGL.enableCull();
        LegacyGL.setLight(this.packedLight);
        LegacyGL.color(0.75F, 0.75F, 0.75F, 1.0F);
        LegacyGL.enableBlend();
    }

    protected void restoreCommonRenderParam() {
        LegacyGL.disableBlend();
    }
}
