package com.ivomens.mcheli.equipment.throwable;

import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.jetbrains.annotations.NotNull;

/** Dispensers throw grenades two blocks out of their face. */
public class ThrowableDispenseBehavior extends DefaultDispenseItemBehavior {

    @Override
    protected @NotNull ItemStack execute(BlockSource source, @NotNull ItemStack stack) {
        if (!(stack.getItem() instanceof ThrowableItem)) {
            return stack;
        }
        ThrowableDefinition info = Throwables.get(stack.getItem()).orElse(null);
        if (info == null) {
            return stack;
        }
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        double x = source.center().x + facing.getStepX() * 2.0;
        double y = source.center().y + facing.getStepY() * 2.0;
        double z = source.center().z + facing.getStepZ() * 2.0;
        ServerLevel level = source.level();
        level.playSound(null, x, y, z, SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 0.5F,
                0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
        ThrowableEntity entity = new ThrowableEntity(level, x, y, z);
        entity.setDeltaMovement(facing.getStepX() * info.dispenseAcceleration(),
                facing.getStepY() * info.dispenseAcceleration(),
                facing.getStepZ() * info.dispenseAcceleration());
        entity.setInfo(info);
        level.addFreshEntity(entity);
        stack.split(1);
        return stack;
    }
}
