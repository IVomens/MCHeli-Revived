package com.ivomens.mcheli.util;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.NotNull;

/** Base item of the mod: not enchantable unless a subclass says otherwise. */
public class McheliItem extends Item {

    /** The legacy numeric id is ignored; ids come from the registry. */
    public McheliItem(int id, Item.Properties properties) {
        super(properties);
    }

    public McheliItem(Item.Properties properties) {
        super(properties);
    }

    // The two methods below override NeoForge item hooks (no @Override, so the common code still compiles
    // against plain vanilla). Vanilla decides through the enchantments' item tags, which list none of ours.

    public boolean supportsEnchantment(@NotNull ItemStack stack, @NotNull Holder<Enchantment> enchantment) {
        return false;
    }

    public boolean isPrimaryItemFor(@NotNull ItemStack stack, @NotNull Holder<Enchantment> enchantment) {
        return false;
    }
}
