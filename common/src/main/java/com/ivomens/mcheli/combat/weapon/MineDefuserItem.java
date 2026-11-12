package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.util.McheliItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Handheld tool that defuses MCHeli proximity mines ({@code BombEntity} in mine mode).
 *
 * <p>The actual defuse happens in {@link BombEntity#interact}: sneak up to an armed mine
 * (sneaking suppresses the proximity trigger) and right-click it while holding this item.
 */
public class MineDefuserItem extends McheliItem {

    public MineDefuserItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal("Sneak up to a mine and right-click it to defuse").withStyle(ChatFormatting.DARK_GRAY));
    }
}
