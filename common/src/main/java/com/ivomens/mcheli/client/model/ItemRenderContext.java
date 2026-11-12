package com.ivomens.mcheli.client.model;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * The item vanilla is drawing right now, as far as the built-in item renderers need it. Vanilla resolves the
 * model's overrides with the stack and its holder ({@link ContentItemOverrides}) and then transforms the model
 * for a display context ({@link ItemBakedModel#onTransform}); both are captured here and read back when the
 * renderer draws. One per client, render thread only.
 */
public final class ItemRenderContext {

    @Nullable
    private LivingEntity holder;
    private ItemStack stack = ItemStack.EMPTY;
    private ItemDisplayContext displayContext = ItemDisplayContext.NONE;

    /** Vanilla resolved the overrides of {@code stack}, held by {@code holder} (null when nobody holds it). */
    void resolved(ItemStack stack, @Nullable LivingEntity holder) {
        this.stack = stack;
        this.holder = holder;
    }

    /** Vanilla is about to transform the model for {@code context}. */
    void transforming(ItemDisplayContext context) {
        this.displayContext = context;
    }

    /** Who holds the item being drawn, or null. */
    @Nullable
    public LivingEntity holder() {
        return this.holder;
    }

    /** The stack being drawn. */
    public ItemStack stack() {
        return this.stack;
    }

    /** Where the item is being drawn: in a hand, in the GUI, on the ground... */
    public ItemDisplayContext displayContext() {
        return this.displayContext;
    }
}
