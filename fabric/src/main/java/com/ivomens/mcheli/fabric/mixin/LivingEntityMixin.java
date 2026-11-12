package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.event.McheliEvents;
import com.ivomens.mcheli.equipment.launcher.LightWeaponItem;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The damage, death and arm swing hooks NeoForge patches into {@code LivingEntity}. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {

    /**
     * Where NeoForge fires {@code LivingIncomingDamageEvent}: past the invulnerability, side, death and
     * fire resistance checks, before anything else uses the amount. Fabric's damage events can only
     * cancel, and MCHeli scales the damage of crew members.
     */
    @Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z",
            ordinal = 0), cancellable = true)
    private void mcheli$incomingDamage(DamageSource source, float originalAmount, CallbackInfoReturnable<Boolean> cir,
                                       @Local(argsOnly = true) LocalFloatRef amount) {
        float result = McheliEvents.LIVING_INCOMING_DAMAGE.invoker()
                .onIncomingDamage((LivingEntity) (Object) this, source, amount.get());
        if (Float.isNaN(result)) {
            cir.setReturnValue(false);
        } else {
            amount.set(result);
        }
    }

    /** Where NeoForge fires {@code LivingDeathEvent} for every living entity. */
    @Inject(method = "die", at = @At("HEAD"))
    private void mcheli$death(DamageSource source, CallbackInfo ci) {
        McheliEvents.LIVING_DEATH.invoker().onDeath((LivingEntity) (Object) this, source);
    }

    /** NeoForge's {@code Item.onEntitySwing}: the light weapons (Stinger, Javelin) cancel the arm swing. */
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void mcheli$cancelSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        ItemStack stack = self.getItemInHand(hand);
        if (!stack.isEmpty() && stack.getItem() instanceof LightWeaponItem weapon && weapon.onEntitySwing(stack, self)) {
            ci.cancel();
        }
    }
}
