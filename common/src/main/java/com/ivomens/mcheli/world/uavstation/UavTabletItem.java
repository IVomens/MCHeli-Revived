package com.ivomens.mcheli.world.uavstation;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.util.McheliItem;
import com.ivomens.mcheli.util.StackData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Handheld "UAV tablet": a UAV station you carry instead of place. Its paired-UAV list lives in the
 * item's custom data. Right-click a deployed UAV to bind it (handled in the event hook); right-click in
 * the air to open the station screen, which on the server seats the player on a <b>temporary</b>
 * {@link UavStationEntity} (seeded from this tablet) that despawns and writes its pairings back to
 * the tablet when the player dismounts.
 *
 * <p>By default the tablet only handles small UAVs; {@link McheliConfig#UavTabletControlNormalUav} lifts
 * that to full-size UAVs too.
 */
public class UavTabletItem extends McheliItem {

    private static final String NBT_PAIRED = "PairedUavs";

    public UavTabletItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    /** @return whether the tablet may bind/control full-size (non-small) UAVs (config-gated). */
    public static boolean allowsNormalUav() {
        return McheliConfig.uav().tabletControlsNormalUav();
    }

    public static List<UUID> getPairedUavs(ItemStack stack) {
        List<UUID> out = new ArrayList<>();
        if (!StackData.hasTag(stack)) {
            return out;
        }
        ListTag list = StackData.getTag(stack).getList(NBT_PAIRED, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("ID")) {
                out.add(entry.getUUID("ID"));
            }
        }
        return out;
    }

    public static void setPairedUavs(ItemStack stack, Collection<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : new LinkedHashSet<>(ids)) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("ID", id);
            list.add(entry);
        }
        StackData.update(stack, tag -> tag.put(NBT_PAIRED, list));
    }

    /** @return true if the UAV was newly added (false if already present). */
    public static boolean addPairedUav(ItemStack stack, UUID id) {
        Set<UUID> ids = new LinkedHashSet<>(getPairedUavs(stack));
        boolean added = ids.add(id);
        if (added) {
            setPairedUavs(stack, ids);
        }
        return added;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // seat the player on a transient station seeded from the tablet, then open its screen
            UavStationEntity station = UavStationEntity.createHandheld(
                    world, player, hand, getPairedUavs(stack), !allowsNormalUav());
            world.addFreshEntity(station);
            player.startRiding(station, true);
            UavStationGuiFactory.INSTANCE.openGui(serverPlayer, station);
        }
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        int pairedCount = getPairedUavs(stack).size();
        tooltip.add(Component.literal("Paired UAVs: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(pairedCount)).withStyle(ChatFormatting.WHITE)));
        tooltip.add(Component.literal("Right-click a UAV to bind it").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Right-click in the air to open").withStyle(ChatFormatting.DARK_GRAY));
        if (!allowsNormalUav()) {
            tooltip.add(Component.literal("Small UAVs only").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
