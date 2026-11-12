package com.ivomens.mcheli.client.model;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Delegating overrides that remember which stack / holder is being rendered. */
public class ContentItemOverrides extends ItemOverrides {

    private final BakedModel bakedModel;
    private final ItemRenderContext context;

    public ContentItemOverrides(BakedModel bakedModel, ItemRenderContext context) {
        this.bakedModel = bakedModel;
        this.context = context;
    }

    @Nullable
    @Override
    public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel level,
                              @Nullable LivingEntity entity, int seed) {
        this.context.resolved(stack, entity);
        return this.bakedModel.getOverrides().resolve(originalModel, stack, level, entity, seed);
    }
}
