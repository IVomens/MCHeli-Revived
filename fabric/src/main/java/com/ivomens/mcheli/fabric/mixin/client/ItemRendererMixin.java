package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.fabric.client.FabricItemBakedModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge asks the model for its display transform ({@code BakedModel.applyTransform}) before it
 * decides between the model and the built-in renderer; vanilla has no such call, so MCHeli's item
 * models learn the display context here instead.
 */
@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"))
    private void mcheli$displayContext(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
                                       MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (model instanceof FabricItemBakedModel itemModel) {
            itemModel.beforeTransform(context);
        }
    }
}
