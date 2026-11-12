package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.client.render.SearchLights;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Lights living entities caught in a searchlight: when a searchlight is brighter than the block light at
 * the entity, the entity is rendered again with that light and the original render is cancelled.
 */
final class SearchLightRelight {

    private final SearchLights searchLights;
    /** Guards the nested render, whose own start hook must not relight it again. */
    private boolean reRendering;

    SearchLightRelight(SearchLights searchLights) {
        this.searchLights = searchLights;
    }

    /**
     * Renders the entity again if a searchlight lights it more than its packed light says.
     *
     * @return true when it did, so the caller cancels the original render
     */
    boolean relight(LivingEntity entity, LivingEntityRenderer<?, ?> renderer, PoseStack poseStack,
                    MultiBufferSource buffers, int packedLight, float partialTicks) {
        if (this.reRendering || this.searchLights.aircraft().isEmpty()) {
            return false;
        }
        int block = LightTexture.block(packedLight) << 4;
        float best = block;
        for (AircraftEntity ac : this.searchLights.aircraft()) {
            best = Math.max(best, ac.searchLight().lightAt(entity));
        }
        if (best <= block) {
            return false;
        }
        // Re-render lit by the searchlight; the nested render fires its own start and end hooks.
        int lit = LightTexture.pack(Mth.clamp((int) best >> 4, 0, 15), 15);
        this.reRendering = true;
        try {
            renderAgain(entity, renderer, poseStack, buffers, lit, partialTicks);
        } finally {
            this.reRendering = false;
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void renderAgain(LivingEntity entity, LivingEntityRenderer<?, ?> renderer, PoseStack poseStack,
                                    MultiBufferSource buffers, int light, float partialTicks) {
        LivingEntityRenderer raw = renderer;
        raw.render(entity, entity.getYRot(), partialTicks, poseStack, buffers, light);
    }
}
