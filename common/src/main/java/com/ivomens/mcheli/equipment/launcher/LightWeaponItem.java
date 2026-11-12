package com.ivomens.mcheli.equipment.launcher;

import com.ivomens.mcheli.util.McheliItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/** Handheld launcher (Stinger / Javelin). */
public class LightWeaponItem extends McheliItem {

    public final LightWeaponAmmoItem bullet;

    /** Ticks the use key has to be held before the launcher is shouldered. */
    public static final int AIM_TICKS = 10;
    private static final int USE_DURATION = 72000;
    /** Night vision granted while aiming is refreshed when it gets this short. */
    private static final int NIGHT_VISION_REFRESH_TICKS = 220;
    private static final int NIGHT_VISION_TICKS = 250;

    public LightWeaponItem(LightWeaponAmmoItem bullet) {
        super(new Item.Properties().durability(10));
        this.bullet = bullet;
    }

    /** Registry path of the launcher item ("fim92", "fgm148" ...), empty for other stacks. */
    public static String getLightWeaponName(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof LightWeaponItem) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        }
        return "";
    }

    /** Whether the player has a launcher shouldered (use key held for a moment). */
    public static boolean isHeld(@Nullable Player player) {
        ItemStack stack = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        return !stack.isEmpty() && stack.getItem() instanceof LightWeaponItem && player.getTicksUsingItem() > AIM_TICKS;
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity user, @NotNull ItemStack stack, int remainingTicks) {
        MobEffectInstance nightVision = user.getEffect(MobEffects.NIGHT_VISION);
        if (nightVision != null && nightVision.getDuration() < NIGHT_VISION_REFRESH_TICKS) {
            user.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, false, false));
        }
    }

    /** NeoForge item hook (no @Override, plain vanilla has no such method): true cancels the swing. */
    public boolean onEntitySwing(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return true;
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
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty()) {
            player.startUsingItem(hand);
        }
        return InteractionResultHolder.success(stack);
    }
}
