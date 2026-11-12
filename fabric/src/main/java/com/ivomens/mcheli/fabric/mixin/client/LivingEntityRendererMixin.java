package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.fabric.client.event.McheliClientEvents;
import com.ivomens.mcheli.setup.client.ClientEventHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code RenderLivingEvent.Pre} and {@code Post}, at the start and the end of the render. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void mcheli$renderStart(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                                    MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
        if (ClientEventHooks.onRenderLivingStart(entity, (LivingEntityRenderer<?, ?>) (Object) this, poseStack, buffers,
                packedLight, partialTicks)) {
            ci.cancel();
        } else {
            McheliClientEvents.RENDER_LIVING_START.invoker().onRenderLiving(entity);
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void mcheli$renderEnd(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                                  MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
        ClientEventHooks.onRenderLivingEnd(entity, poseStack, buffers, partialTicks);
    }
}
