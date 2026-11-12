package com.ivomens.mcheli.equipment.rangefinder;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.McheliItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Laser rangefinder: a scope that measures distance and spots players/vehicles or mobs for the
 * team (or marks a point). Its damage value is the used battery charge. The scope itself (zoom, mode,
 * spotting) is client state; see {@link RangeFinderClient}.
 */
public class RangeFinderItem extends McheliItem {

    /** Ticks the item has to be held in use before the scope comes up. */
    public static final int SCOPE_DELAY_TICKS = 8;

    private static final int BATTERY_CHARGES = 10;
    private static final int USE_DURATION = 72000;

    public RangeFinderItem(Item.Properties properties) {
        super(Services.GAMEPLAY.noRepair(properties.stacksTo(1).durability(BATTERY_CHARGES)));
    }

    /** The player holds a range finder and is not busy with an aircraft weapon. */
    public static boolean canUse(Player player) {
        if (player == null || !(player.getMainHandItem().getItem() instanceof RangeFinderItem)
                || player.getVehicle() instanceof AircraftEntity) {
            return false;
        }
        if (player.getVehicle() instanceof SeatEntity seat) {
            AircraftEntity ac = seat.getParent();
            return ac == null || (!ac.modes().usesGunnerView(player) && ac.weapons().idForSeat(ac.seats().seatIdOf(player)) < 0);
        }
        return true;
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, Level level, @NotNull LivingEntity entity, int timeLeft) {
        if (level.isClientSide) {
            RangeFinderClient.INSTANCE.lowerScope();
        }
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity) {
        return stack;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        if (canUse(player)) {
            player.startUsingItem(hand);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
