package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.event.McheliEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Players join a server level here rather than through its entity manager; NeoForge fires its join event first. */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    @Inject(method = "addPlayer", at = @At("HEAD"))
    private void mcheli$playerJoin(ServerPlayer player, CallbackInfo ci) {
        McheliEvents.ENTITY_JOIN.invoker().onLevelChange(player, (ServerLevel) (Object) this);
    }
}
