package com.ivomens.mcheli.client.model;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * A built-in item renderer. {@link #renderItem} is called inside an open
 * {@link com.ivomens.mcheli.client.render.LegacyGL} frame (pose already at the item's model origin).
 */
public interface IItemModelRenderer {

    static boolean isFirstPerson(ItemDisplayContext type) {
        return type == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || type == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    static boolean isThirdPerson(ItemDisplayContext type) {
        return type == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || type == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    /** Whether this renderer draws the item in the given context; otherwise the flat sprite model is used. */
    boolean shouldRender(ItemStack stack, ItemDisplayContext displayContext);

    void renderItem(ItemStack stack, @Nullable LivingEntity holder, ItemDisplayContext displayContext, float partialTicks);
}
