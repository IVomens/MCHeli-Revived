package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.vehicle.world.VehicleLimits;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.jetbrains.annotations.NotNull;

/**
 * Dispensers place aircraft two blocks in front of them (creative-only aircraft and UAVs excepted), within
 * the server's vehicle limits. A dispensed vehicle has no owner.
 */
public class AircraftDispenseBehavior extends DefaultDispenseItemBehavior {

    @Override
    public @NotNull ItemStack execute(@NotNull BlockSource source, @NotNull ItemStack stack) {
        if (!(stack.getItem() instanceof AircraftItem item)) {
            return stack;
        }
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        int x = (int) (source.pos().getX() + facing.getStepX() * 2.0);
        int y = (int) (source.pos().getY() + facing.getStepY() * 2.0);
        int z = (int) (source.pos().getZ() + facing.getStepZ() * 2.0);
        ServerLevel level = source.level();
        AircraftEntity aircraft = item.onTileClick(stack, level, 0.0F, x, y, z);
        if (aircraft != null && aircraft.spec() != null && !aircraft.spec().file().creativeOnly()
                && !aircraft.isUAV() && VehicleLimits.refusal(level, null, aircraft).isEmpty()) {
            VehicleItemData.read(stack, aircraft);
            level.addFreshEntity(aircraft);
            stack.shrink(1);
        }
        return stack;
    }
}
