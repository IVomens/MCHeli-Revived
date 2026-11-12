package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.event.McheliEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@code ServerPlayer.die} does not call its super method, so NeoForge fires its death event here too. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {

    @Inject(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;gameEvent(Lnet/minecraft/core/Holder;)V", shift = At.Shift.AFTER))
    private void mcheli$death(DamageSource source, CallbackInfo ci) {
        McheliEvents.LIVING_DEATH.invoker().onDeath((ServerPlayer) (Object) this, source);
    }
}
