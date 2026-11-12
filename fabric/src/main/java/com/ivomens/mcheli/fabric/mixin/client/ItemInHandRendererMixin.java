package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.setup.client.ClientEventHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code RenderHandEvent}, which MCHeli cancels for both hands while riding. */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void mcheli$hideHands(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                  LocalPlayer player, int packedLight, CallbackInfo ci) {
        if (ClientEventHooks.shouldHideHand()) {
            ci.cancel();
        }
    }
}
