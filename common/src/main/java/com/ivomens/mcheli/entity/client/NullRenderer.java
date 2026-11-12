package com.ivomens.mcheli.entity.client;

import com.ivomens.mcheli.util.Ids;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

/** Renders nothing (helper entities such as hitboxes, rope anchors and view dummies). */
public class NullRenderer<T extends Entity> extends EntityRenderer<T> {

    private static final ResourceLocation TEX_DEFAULT = Ids.of("textures/default.png");

    public NullRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(@NotNull T entity, @NotNull Frustum frustum, double camX, double camY, double camZ) {
        return false;
    }

    @Override
    public void render(@NotNull T entity, float yaw, float partialTicks, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffers, int packedLight) {
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull T entity) {
        return TEX_DEFAULT;
    }
}
