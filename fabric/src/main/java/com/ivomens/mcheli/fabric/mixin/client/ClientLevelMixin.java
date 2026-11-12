package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.fabric.event.McheliEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entities join a client level here; NeoForge fires its join event first. */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void mcheli$entityJoin(Entity entity, CallbackInfo ci) {
        McheliEvents.ENTITY_JOIN.invoker().onLevelChange(entity, (ClientLevel) (Object) this);
    }
}
