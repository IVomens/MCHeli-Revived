package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.fabric.client.event.McheliClientEvents;
import com.ivomens.mcheli.setup.CommonEventHooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * The client hooks NeoForge patches into {@code Minecraft}: the render frame around the game renderer,
 * the client level tick around {@code ClientLevel.tick} (Fabric's client world tick events wrap the
 * entity tick instead), and the client level unload.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {

    @Shadow
    @Final
    private DeltaTracker.Timer timer;

    @Unique
    @Nullable
    private ClientLevel mcheli$level() {
        return ((Minecraft) (Object) this).level;
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void mcheli$frameStart(boolean renderLevel, CallbackInfo ci) {
        McheliClientEvents.FRAME_START.invoker().onFrame(this.timer);
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V",
            shift = At.Shift.AFTER))
    private void mcheli$frameEnd(boolean renderLevel, CallbackInfo ci) {
        McheliClientEvents.FRAME_END.invoker().onFrame(this.timer);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void mcheli$levelTickStart(CallbackInfo ci) {
        CommonEventHooks.onLevelTickStart(this.mcheli$level());
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;tick(Ljava/util/function/BooleanSupplier;)V",
            shift = At.Shift.AFTER))
    private void mcheli$levelTickEnd(CallbackInfo ci) {
        CommonEventHooks.onLevelTickEnd(this.mcheli$level());
    }

    /** NeoForge unloads the old client level when another replaces it and when the player leaves the world. */
    @Inject(method = {"setLevel", "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", "clearClientLevel"},
            at = @At("HEAD"))
    private void mcheli$levelUnload(CallbackInfo ci) {
        if (this.mcheli$level() != null) {
            CommonEventHooks.onLevelUnload(this.mcheli$level());
        }
    }
}
