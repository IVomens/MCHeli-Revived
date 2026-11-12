package com.ivomens.mcheli.setup.client.item;

import com.ivomens.mcheli.client.model.IItemModelRenderer;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BuiltInDraftingTableItemRenderer implements IItemModelRenderer {

    private static final ResourceLocation TEX = McheliEntityRenderer.texture("textures/blocks/drafting_table.png");

    @Override
    public boolean shouldRender(ItemStack stack, ItemDisplayContext displayContext) {
        return true;
    }

    @Override
    public void renderItem(ItemStack stack, LivingEntity holder, ItemDisplayContext displayContext, float partialTicks) {
        LegacyGL.pushMatrix();
        LegacyGL.bindTexture(TEX);
        switch (displayContext) {
            case GROUND -> {
                LegacyGL.translate(0.0F, 0.5F, 0.0F);
                LegacyGL.scale(1.5F, 1.5F, 1.5F);
            }
            case GUI, FIXED -> {
                LegacyGL.translate(0.0F, -0.5F, 0.0F);
                LegacyGL.scale(0.75F, 0.75F, 0.75F);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> LegacyGL.translate(0.0F, 0.0F, 0.5F);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                LegacyGL.translate(0.75F, 0.0F, 0.0F);
                LegacyGL.rotate(90.0F, 0.0F, -1.0F, 0.0F);
            }
            default -> {
            }
        }

        ModelCache.render("blocks/drafting_table");
        LegacyGL.popMatrix();
        LegacyGL.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
