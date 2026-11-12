package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.setup.client.ClientEventHooks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's {@code ViewportEvent.ComputeFov}, which it fires for every view except panoramas. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @Shadow
    private boolean panoramicMode;

    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private double mcheli$fov(double fov, Camera camera, float partialTicks, boolean useFovSetting) {
        return this.panoramicMode ? fov : ClientEventHooks.adjustFov(fov, camera);
    }
}
