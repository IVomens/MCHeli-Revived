package com.ivomens.mcheli.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/**
 * Bridge for the legacy "stack NBT" model. 1.21 items store data in components; MCHeli keeps its
 * per-stack state (aircraft health, fuel, weapon ammo, UAV pairing ...) in the CUSTOM_DATA component
 * so the old tag layout and addon expectations keep working.
 */
public final class StackData {
    private StackData() {
    }

    public static boolean hasTag(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.CUSTOM_DATA);
    }

    /** A copy of the stack's tag (empty tag if none). Modifying it does not modify the stack. */
    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    /** Replaces the stack's tag. A null or empty tag removes it. */
    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /** Read-modify-write the stack's tag. */
    public static void update(ItemStack stack, Consumer<CompoundTag> editor) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, editor);
    }
}
