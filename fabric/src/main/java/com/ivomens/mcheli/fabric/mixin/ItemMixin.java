package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.extension.RepairableItem;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code Item.canRepair}, taken from the properties the item was made with. */
@Mixin(Item.class)
abstract class ItemMixin implements RepairableItem {

    @Unique
    private boolean mcheli$repairable = true;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mcheli$readRepairable(Item.Properties properties, CallbackInfo ci) {
        this.mcheli$repairable = !((RepairableItem.Properties) properties).mcheli$isNoRepair();
    }

    @Override
    public boolean mcheli$isRepairable() {
        return this.mcheli$repairable;
    }
}
