package com.ivomens.mcheli.equipment.throwable;

import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition;
import com.ivomens.mcheli.util.McheliItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import org.jetbrains.annotations.NotNull;

/** Grenade item: hold to wind up, release to throw; a longer wind-up throws further. */
public class ThrowableItem extends McheliItem {

    private static final int USE_DURATION = 72000;
    /** Wind-up ticks for a full strength throw. */
    private static final int FULL_WIND_UP_TICKS = 35;
    private static final int MIN_WIND_UP_TICKS = 5;

    public ThrowableItem(int id, int stackSize) {
        super(id, new Item.Properties().stacksTo(stackSize));
    }

    public static void registerDispenseBehavior(Item item) {
        DispenserBlock.registerBehavior(item, new ThrowableDispenseBehavior());
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity user, int remainingTicks) {
        if (!(user instanceof Player player) || stack.isEmpty()) {
            return;
        }
        ThrowableDefinition info = Throwables.get(stack.getItem()).orElse(null);
        if (info == null) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                0.5F, 0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
        if (level.isClientSide) {
            return;
        }

        int windUp = USE_DURATION - remainingTicks;
        float acceleration = windUp <= FULL_WIND_UP_TICKS ? Math.max(windUp, MIN_WIND_UP_TICKS) / 25.0F : 1.0F;
        ThrowableEntity entity = new ThrowableEntity(level, player, acceleration);
        entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, acceleration, 1.0F);
        entity.setInfo(info);
        level.addFreshEntity(entity);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW;
    }
}
