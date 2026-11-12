package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.event.McheliEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player tick and death hooks NeoForge patches into {@code Player}. */
@Mixin(Player.class)
abstract class PlayerMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void mcheli$tickStart(CallbackInfo ci) {
        McheliEvents.PLAYER_TICK_START.invoker().onPlayerTick((Player) (Object) this);
    }

    /** NeoForge fires its death event here and again from {@code LivingEntity.die}, which this calls. */
    @Inject(method = "die", at = @At("HEAD"))
    private void mcheli$death(DamageSource source, CallbackInfo ci) {
        McheliEvents.LIVING_DEATH.invoker().onDeath((Player) (Object) this, source);
    }
}
