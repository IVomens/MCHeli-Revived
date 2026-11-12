package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.setup.client.HudLayers;
import com.ivomens.mcheli.setup.client.ClientEventHooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The HUD the way NeoForge's GUI layers draw it. MCHeli's two layers are drawn as layers of their own
 * (one layer step further back in depth) right after the vanilla camera overlays and the chat, where
 * NeoForge registers them. While riding, the vanilla layers MCHeli hides on NeoForge are skipped:
 * camera overlays, crosshair, the player's health, armor, food and air, and the boss bars (see
 * {@code BossHealthOverlayMixin}). The GUI start and end hooks wrap the layers as NeoForge's
 * {@code RenderGuiEvent} does.
 */
@Mixin(Gui.class)
abstract class GuiMixin {

    /** The depth step {@code LayeredDraw} puts between two layers. */
    @Unique
    private static final float LAYER_DEPTH = 200.0F;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/LayeredDraw;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void mcheli$guiStart(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        ClientEventHooks.onRenderGuiStart();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/LayeredDraw;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER))
    private void mcheli$guiEnd(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        ClientEventHooks.onRenderGuiEnd();
        ClientEventHooks.onRenderGuiOverlay(graphics);
    }

    @WrapMethod(method = "renderCameraOverlays")
    private void mcheli$cameraOverlaysThenHud(GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        if (!ClientEventHooks.hidesVanillaHud()) {
            original.call(graphics, delta);
        }
        mcheli$asLayer(graphics, delta, HudLayers::renderHud);
    }

    @WrapMethod(method = "renderChat")
    private void mcheli$chatThenHudTop(GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        original.call(graphics, delta);
        mcheli$asLayer(graphics, delta, HudLayers::renderTop);
    }

    @Unique
    private static void mcheli$asLayer(GuiGraphics graphics, DeltaTracker delta, LayeredDraw.Layer layer) {
        graphics.pose().translate(0.0F, 0.0F, LAYER_DEPTH);
        layer.render(graphics, delta);
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void mcheli$hideCrosshair(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (ClientEventHooks.hidesVanillaHud()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void mcheli$hidePlayerStatus(GuiGraphics graphics, CallbackInfo ci) {
        if (ClientEventHooks.hidesVanillaHud()) {
            ci.cancel();
        }
    }
}
