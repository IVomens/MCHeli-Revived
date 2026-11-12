package com.ivomens.mcheli.setup.client.item;

import com.ivomens.mcheli.client.model.IItemModelRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BuiltInInvisibleItemRenderer implements IItemModelRenderer {

    @Override
    public boolean shouldRender(ItemStack stack, ItemDisplayContext displayContext) {
        return IItemModelRenderer.isFirstPerson(displayContext) || IItemModelRenderer.isThirdPerson(displayContext);
    }

    @Override
    public void renderItem(ItemStack stack, LivingEntity holder, ItemDisplayContext displayContext, float partialTicks) {
    }
}
