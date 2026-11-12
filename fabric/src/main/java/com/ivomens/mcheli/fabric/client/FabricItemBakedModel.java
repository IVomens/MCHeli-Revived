package com.ivomens.mcheli.fabric.client;

import com.ivomens.mcheli.client.model.IItemModelRenderer;
import com.ivomens.mcheli.client.model.ItemBakedModel;
import com.ivomens.mcheli.client.model.ItemRenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * MCHeli's item model on Fabric. Vanilla has no per-context model transform hook, so the item renderer
 * mixin hands the display context over before each draw, which is what NeoForge's
 * {@code applyTransform} override does.
 */
public final class FabricItemBakedModel extends ItemBakedModel {

    FabricItemBakedModel(BakedModel bakedModel, IItemModelRenderer renderer, ItemRenderContext context) {
        super(bakedModel, renderer, context);
    }

    /** Called by the item renderer mixin before the display transform is applied. */
    public void beforeTransform(ItemDisplayContext context) {
        this.onTransform(context);
    }
}
