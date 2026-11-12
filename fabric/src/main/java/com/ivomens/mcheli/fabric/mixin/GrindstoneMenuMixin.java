package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.extension.RepairableItem;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The grindstone repairs two items into one only if they are repairable, and keeps the first item's
 * damage when the second is not, as NeoForge does.
 */
@Mixin(GrindstoneMenu.class)
abstract class GrindstoneMenuMixin {

    @ModifyExpressionValue(method = "mergeItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;isDamageableItem()Z", ordinal = 0))
    private boolean mcheli$onlyRepairable(boolean damageable, ItemStack input, ItemStack additional) {
        return damageable && RepairableItem.isRepairable(input);
    }

    @Inject(method = "mergeItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/GrindstoneMenu;mergeEnchantsFrom(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"))
    private void mcheli$keepDamage(ItemStack input, ItemStack additional, CallbackInfoReturnable<ItemStack> cir,
                                   @Local(ordinal = 2) ItemStack result) {
        if (result.isDamageableItem() && !RepairableItem.isRepairable(additional)) {
            result.setDamageValue(input.getDamageValue());
        }
    }
}
