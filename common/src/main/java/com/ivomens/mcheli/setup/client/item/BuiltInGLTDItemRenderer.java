package com.ivomens.mcheli.setup.client.item;

import com.ivomens.mcheli.client.model.IItemModelRenderer;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.equipment.gltd.client.GltdRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BuiltInGLTDItemRenderer implements IItemModelRenderer {

    private static final ResourceLocation TEX = McheliEntityRenderer.texture("textures/gltd.png");

    @Override
    public boolean shouldRender(ItemStack stack, ItemDisplayContext displayContext) {
        return IItemModelRenderer.isFirstPerson(displayContext) || IItemModelRenderer.isThirdPerson(displayContext) ||
                displayContext == ItemDisplayContext.GROUND;
    }

    @Override
    public void renderItem(ItemStack stack, LivingEntity holder, ItemDisplayContext displayContext, float partialTicks) {
        IModelCustom model = ModelCache.find("gltd");
        if (model == null) {
            return;
        }
        LegacyGL.pushMatrix();
        LegacyGL.enableCull();
        LegacyGL.bindTexture(TEX);
        model.renderAll();
        LegacyGL.popMatrix();
    }
}
