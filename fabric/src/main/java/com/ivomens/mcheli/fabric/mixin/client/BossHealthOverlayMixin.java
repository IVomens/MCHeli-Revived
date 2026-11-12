package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.setup.client.ClientEventHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The boss bars are one of the vanilla HUD layers MCHeli hides while riding (see {@code GuiMixin}). */
@Mixin(BossHealthOverlay.class)
abstract class BossHealthOverlayMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mcheli$hideWhileRiding(GuiGraphics graphics, CallbackInfo ci) {
        if (ClientEventHooks.hidesVanillaHud()) {
            ci.cancel();
        }
    }
}
