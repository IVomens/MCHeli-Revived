package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.extension.RepairableItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Two damaged items combine in the crafting grid only if both are repairable, as NeoForge checks. */
@Mixin(RepairItemRecipe.class)
abstract class RepairItemRecipeMixin {

    @Inject(method = "canCombine", at = @At("RETURN"), cancellable = true)
    private static void mcheli$onlyRepairable(ItemStack first, ItemStack second, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !(RepairableItem.isRepairable(first) && RepairableItem.isRepairable(second))) {
            cir.setReturnValue(false);
        }
    }
}
