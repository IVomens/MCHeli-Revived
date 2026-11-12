package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.extension.RepairableItem;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** NeoForge's {@code Item.Properties.setNoRepair}. */
@Mixin(Item.Properties.class)
abstract class ItemPropertiesMixin implements RepairableItem.Properties {

    @Unique
    private boolean mcheli$noRepair;

    @Override
    public void mcheli$setNoRepair() {
        this.mcheli$noRepair = true;
    }

    @Override
    public boolean mcheli$isNoRepair() {
        return this.mcheli$noRepair;
    }
}
