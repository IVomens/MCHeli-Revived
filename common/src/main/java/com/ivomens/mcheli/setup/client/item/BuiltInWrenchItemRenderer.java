package com.ivomens.mcheli.setup.client.item;

import com.ivomens.mcheli.client.model.IItemModelRenderer;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.equipment.wrench.WrenchItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BuiltInWrenchItemRenderer implements IItemModelRenderer {

    private static final ResourceLocation TEX = McheliEntityRenderer.texture("textures/wrench.png");

    @Override
    public boolean shouldRender(ItemStack stack, ItemDisplayContext displayContext) {
        return IItemModelRenderer.isFirstPerson(displayContext) || IItemModelRenderer.isThirdPerson(displayContext);
    }

    @Override
    public void renderItem(ItemStack stack, LivingEntity holder, ItemDisplayContext displayContext, float partialTicks) {
        LegacyGL.pushMatrix();
        LegacyGL.bindTexture(TEX);
        if (IItemModelRenderer.isFirstPerson(displayContext) && holder != null && holder.isUsingItem()
                && holder.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            float swing = WrenchItem.getUseAnimSmooth(stack, partialTicks);
            LegacyGL.rotate(65.0F, 0.0F, 0.0F, 1.0F);
            LegacyGL.rotate(swing + 20.0F, 1.0F, 0.0F, 0.0F);
        }

        ModelCache.render("wrench");
        LegacyGL.popMatrix();
    }
}
