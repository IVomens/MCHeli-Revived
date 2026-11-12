package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.setup.client.ClientEventHooks;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's {@code AFTER_SKY} and {@code AFTER_PARTICLES} render stages, which Fabric's world render
 * events do not have. Both particle passes (with and without the fabulous particle target) end the
 * stage, as on NeoForge.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            shift = At.Shift.AFTER))
    private void mcheli$afterSky(DeltaTracker delta, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                 LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        ClientEventHooks.onSkyRendered(projectionMatrix, frustumMatrix, camera);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
            shift = At.Shift.AFTER))
    private void mcheli$afterParticles(DeltaTracker delta, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                       LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci,
                                       @Local PoseStack poseStack) {
        ClientEventHooks.onParticlesRendered(camera, delta.getGameTimeDeltaPartialTick(false), poseStack);
    }
}
