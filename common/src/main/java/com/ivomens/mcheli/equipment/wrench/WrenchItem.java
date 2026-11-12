package com.ivomens.mcheli.equipment.wrench;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftPicking;
import com.ivomens.mcheli.aircraft.VehicleTool;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.util.McheliItem;
import com.ivomens.mcheli.util.StackData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Repairs aircraft while held down on them (and shows their HP); shift+right click changes skins.
 * Mines metal blocks and redstone components quickly.
 */
public class WrenchItem extends McheliItem implements VehicleTool {

    @Override
    public boolean cyclesTexture() {
        return true;
    }


    /** Uses of an iron tool. */
    private static final int MAX_USES = 250;
    /** 4 + iron tool attack bonus. */
    private static final float DAMAGE_VS_ENTITY = 4.0F + 2.0F;
    private static final int ENCHANTMENT_VALUE = 14;
    private static final int USE_DURATION = 72000;

    private static final String TAG_ANIM = "MCH_WrenchAnim";
    private static final String TAG_ANIM_PREV = "MCH_WrenchAnimPrev";
    /** The repair animation counts down from this value and wraps around. */
    private static final int ANIM_CYCLE = 16;

    private static final int REPAIR_INTERVAL_TICKS = 20;
    private static final int REPAIR_AMOUNT = 10;
    private static final float METAL_DESTROY_SPEED = 20.5F;
    private static final float CIRCUIT_DESTROY_SPEED = 5.5F;
    private static final float DEFAULT_DESTROY_SPEED = 2.0F;
    /** Chance (1 in n) that hitting a mob knocks out an iron ingot, or else gunpowder. */
    private static final int IRON_DROP_CHANCE = 40;
    private static final int GUNPOWDER_DROP_CHANCE = 20;

    private static final RandomSource RANDOM = RandomSource.createThreadSafe();

    public WrenchItem(Item.Properties properties) {
        super(properties.stacksTo(1).durability(MAX_USES).attributes(createAttributes()));
    }

    private static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, DAMAGE_VS_ENTITY, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal("Shift+Right click to change vehicle skin!").withStyle(ChatFormatting.YELLOW));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    /** Interpolated swing of the repair animation, 0 to 8. */
    public static float getUseAnimSmooth(ItemStack stack, float partialTicks) {
        int half = ANIM_CYCLE / 2;
        int current = Math.abs(getAnimCount(stack, TAG_ANIM) - half);
        int previous = Math.abs(getAnimCount(stack, TAG_ANIM_PREV) - half);
        return previous + (current - previous) * partialTicks;
    }

    private static void setUseAnimCount(ItemStack stack, int count, int previous) {
        if (getAnimCount(stack, TAG_ANIM) == count && getAnimCount(stack, TAG_ANIM_PREV) == previous) {
            return;
        }
        StackData.update(stack, tag -> {
            tag.putInt(TAG_ANIM, count);
            tag.putInt(TAG_ANIM_PREV, previous);
        });
    }

    private static int getAnimCount(ItemStack stack, String name) {
        return StackData.hasTag(stack) ? StackData.getTag(stack).getInt(name) : 0;
    }

    private static boolean isMetal(BlockState state) {
        return state.getSoundType() == SoundType.METAL || state.getSoundType() == SoundType.ANVIL
                || state.getSoundType() == SoundType.COPPER || state.getSoundType() == SoundType.CHAIN;
    }

    private static boolean isCircuit(BlockState state) {
        return state.getBlock() instanceof RedStoneWireBlock || state.getBlock() instanceof DiodeBlock
                || state.getBlock() instanceof LeverBlock || state.getBlock() instanceof ButtonBlock
                || state.getBlock() instanceof BaseRailBlock;
    }

    @Override
    public boolean isCorrectToolForDrops(@NotNull ItemStack stack, @NotNull BlockState state) {
        return isMetal(state) || isCircuit(state);
    }

    @Override
    public float getDestroySpeed(@NotNull ItemStack stack, @NotNull BlockState state) {
        if (isMetal(state)) {
            return METAL_DESTROY_SPEED;
        }
        return isCircuit(state) ? CIRCUIT_DESTROY_SPEED : DEFAULT_DESTROY_SPEED;
    }

    @Override
    public boolean hurtEnemy(@NotNull ItemStack stack, @NotNull LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide) {
            if (RANDOM.nextInt(IRON_DROP_CHANCE) == 0) {
                target.spawnAtLocation(new ItemStack(Items.IRON_INGOT), 0.0F);
            } else if (RANDOM.nextInt(GUNPOWDER_DROP_CHANCE) == 0) {
                target.spawnAtLocation(new ItemStack(Items.GUNPOWDER), 0.0F);
            }
        }
        return true;
    }

    @Override
    public void postHurtEnemy(@NotNull ItemStack stack, @NotNull LivingEntity target, @NotNull LivingEntity attacker) {
        stack.hurtAndBreak(2, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity, int timeLeft) {
        setUseAnimCount(stack, 0, 0);
    }

    /** Client: animates while pointing at an aircraft. Server: repairs that aircraft once per second. */
    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity user, @NotNull ItemStack stack, int remainingTicks) {
        if (level.isClientSide) {
            if (AircraftPicking.lookedAtAircraft(user) != null) {
                int previous = getAnimCount(stack, TAG_ANIM);
                setUseAnimCount(stack, previous <= 0 ? ANIM_CYCLE : previous - 1, previous);
            }
            return;
        }
        if (remainingTicks >= this.getUseDuration(stack, user) || remainingTicks % REPAIR_INTERVAL_TICKS != 0) {
            return;
        }
        AircraftEntity ac = AircraftPicking.lookedAtAircraft(user);
        if (ac != null && ac.damage().hp() > 0 && ac.damage().repair(REPAIR_AMOUNT)) {
            stack.hurtAndBreak(1, user, user.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            float pitch = 0.9F + RANDOM.nextFloat() * 0.2F;
            ModSounds.playSound(level, ac.getX(), ac.getY(), ac.getZ(), "wrench", 1.0F, pitch);
        }
    }

    /** Lets a creative player break blocks back to back while holding the wrench. */
    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        if (level.isClientSide && entity instanceof Player player && player.getMainHandItem() == stack) {
            LocalClient.get().skipBlockBreakDelay();
        }
    }

    @Override
    public boolean mineBlock(@NotNull ItemStack stack, @NotNull Level level, BlockState state,
                             @NotNull BlockPos pos, @NotNull LivingEntity entity) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
            stack.hurtAndBreak(2, entity, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getEnchantmentValue() {
        return ENCHANTMENT_VALUE;
    }

    @Override
    public boolean isEnchantable(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public boolean supportsEnchantment(@NotNull ItemStack stack, @NotNull Holder<Enchantment> enchantment) {
        return enchantment.value().isSupportedItem(stack);
    }

    @Override
    public boolean isPrimaryItemFor(@NotNull ItemStack stack, @NotNull Holder<Enchantment> enchantment) {
        return enchantment.value().isPrimaryItem(stack);
    }

    @Override
    public boolean isValidRepairItem(@NotNull ItemStack stack, @NotNull ItemStack repairMaterial) {
        return repairMaterial.is(Items.IRON_INGOT) || super.isValidRepairItem(stack, repairMaterial);
    }
}
