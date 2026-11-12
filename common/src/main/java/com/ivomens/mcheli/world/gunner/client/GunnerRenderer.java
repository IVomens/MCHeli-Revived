package com.ivomens.mcheli.world.gunner.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.world.gunner.GunnerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class GunnerRenderer extends LivingEntityRenderer<GunnerEntity, HumanoidModel<GunnerEntity>> {

    public static final EntityRendererProvider<GunnerEntity> FACTORY = GunnerRenderer::new;
    private static final ResourceLocation TEXTURE = Ids.of("textures/mob/heligunner.png");
    /** Gunners are drawn slightly smaller than players. */
    private static final float MODEL_SCALE = 0.9375F;

    public GunnerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    protected boolean shouldShowName(@NotNull GunnerEntity entity) {
        return entity.getTeam() != null && super.shouldShowName(entity);
    }

    @Override
    public void render(@NotNull GunnerEntity entity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffers, int packedLight) {
        this.model.crouching = entity.isCrouching();
        AircraftEntity ac = entity.getAc();
        // the pilot gunner is hidden when the aircraft hides its riders
        if (ac != null && ac.spec() != null
                && (!ac.spec().file().render().hideRiders() || !ac.isPilot(entity))) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
        }
        this.model.crouching = false;
        this.model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
    }

    @Override
    protected void scale(@NotNull GunnerEntity entity, @NotNull PoseStack poseStack, float partialTicks) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull GunnerEntity entity) {
        return TEXTURE;
    }
}
