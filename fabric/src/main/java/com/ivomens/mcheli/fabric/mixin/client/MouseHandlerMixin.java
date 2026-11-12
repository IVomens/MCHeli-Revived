package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.setup.client.ClientEventHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's {@code InputEvent.MouseScrollingEvent} (in game, before the hotbar scrolls) and
 * {@code CalculatePlayerTurnEvent} (while MCHeli turns the player, the mouse does not).
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {

    /** Makes vanilla's {@code 0.6 * s + 0.2} sensitivity zero. */
    @Unique
    private static final double NO_SENSITIVITY = -1.0 / 3.0;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z",
            ordinal = 0), cancellable = true)
    private void mcheli$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        // the scaled delta vanilla works with, as NeoForge hands it to the event
        boolean discrete = this.minecraft.options.discreteMouseScroll().get();
        double deltaY = (discrete ? Math.signum(yOffset) : yOffset) * this.minecraft.options.mouseWheelSensitivity().get();
        if (ClientEventHooks.onMouseScroll(deltaY)) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private Object mcheli$sensitivity(Object sensitivity) {
        return ClientEventHooks.isControllingPlayerTurn() ? NO_SENSITIVITY : sensitivity;
    }

    @ModifyExpressionValue(method = "turnPlayer", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private boolean mcheli$cinematicCamera(boolean smoothCamera) {
        return smoothCamera && !ClientEventHooks.isControllingPlayerTurn();
    }
}
