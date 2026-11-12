package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.fabric.event.McheliEvents;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge fires its leave event at the end of the client level's tracking callback. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel$EntityCallbacks")
abstract class ClientLevelEntityCallbacksMixin {

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void mcheli$entityLeave(Entity entity, CallbackInfo ci) {
        McheliEvents.ENTITY_LEAVE.invoker().onLevelChange(entity, entity.level());
    }
}
