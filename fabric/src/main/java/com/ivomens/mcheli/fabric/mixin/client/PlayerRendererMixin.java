package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.setup.client.ClientEventHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code RenderPlayerEvent.Pre}: riders of aircraft that hide their crew are not drawn. */
@Mixin(PlayerRenderer.class)
abstract class PlayerRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void mcheli$hidePlayer(AbstractClientPlayer player, float yaw, float partialTicks, PoseStack poseStack,
                                   MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
        if (ClientEventHooks.shouldHidePlayer(player)) {
            ci.cancel();
        }
    }
}
