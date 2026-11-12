package com.ivomens.mcheli.world.uavstation;

import com.ivomens.mcheli.util.McheliItem;
import com.ivomens.mcheli.util.StackData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Handheld device for binding UAVs to UAV stations.
 *
 * <p>Right-click a deployed UAV to store its entity UUID on the device, then right-click a UAV
 * station to add that UAV to the station's paired list (see {@link IUavPairingHolder}). The actual
 * interaction is handled in the entity-interact event hook because UAV/station entities are not
 * living entities, so {@code interactLivingEntity} never fires for them.
 */
public class UavPairingDeviceItem extends McheliItem {

    public static final String NBT_BOUND_UAV = "BoundUav";

    public UavPairingDeviceItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    @Nullable
    public static UUID getBoundUav(ItemStack stack) {
        if (!StackData.hasTag(stack)) {
            return null;
        }
        CompoundTag tag = StackData.getTag(stack);
        return tag.hasUUID(NBT_BOUND_UAV) ? tag.getUUID(NBT_BOUND_UAV) : null;
    }

    public static void setBoundUav(ItemStack stack, @Nullable UUID id) {
        CompoundTag tag = StackData.getTag(stack);
        if (id == null) {
            tag.remove(NBT_BOUND_UAV);
        } else {
            tag.putUUID(NBT_BOUND_UAV, id);
        }
        StackData.setTag(stack, tag);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        UUID bound = getBoundUav(stack);
        if (bound != null) {
            tooltip.add(Component.literal("Bound UAV: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(bound.toString().substring(0, 8)).withStyle(ChatFormatting.WHITE)));
            tooltip.add(Component.literal("Right-click a UAV station to pair").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("Right-click a UAV to bind it").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
