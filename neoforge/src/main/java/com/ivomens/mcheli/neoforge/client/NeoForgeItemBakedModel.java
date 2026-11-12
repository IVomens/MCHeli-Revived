package com.ivomens.mcheli.neoforge.client;

import com.ivomens.mcheli.client.model.IItemModelRenderer;
import com.ivomens.mcheli.client.model.ItemBakedModel;
import com.ivomens.mcheli.client.model.ItemRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Hooks NeoForge's per-context model transform and passes the wrapped model's render types through. */
final class NeoForgeItemBakedModel extends ItemBakedModel {

    NeoForgeItemBakedModel(BakedModel bakedModel, IItemModelRenderer renderer, ItemRenderContext context) {
        super(bakedModel, renderer, context);
    }

    @Override
    public @NotNull BakedModel applyTransform(@NotNull ItemDisplayContext transformType, @NotNull PoseStack poseStack,
                                              boolean applyLeftHandTransform) {
        this.onTransform(transformType);
        BakedModel wrapped = this.getWrappedModel();
        BakedModel transformed = wrapped.applyTransform(transformType, poseStack, applyLeftHandTransform);
        return transformed == wrapped ? this : new NeoForgeItemBakedModel(transformed, this.getRenderer(),
                this.getRenderContext());
    }

    @Override
    public @NotNull List<RenderType> getRenderTypes(@NotNull ItemStack itemStack, boolean fabulous) {
        return this.getWrappedModel().getRenderTypes(itemStack, fabulous);
    }
}
