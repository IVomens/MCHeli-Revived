package com.ivomens.mcheli.world.uavstation;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.event.PermissionEvents;
import com.ivomens.mcheli.helicopter.HelicopterItem;
import com.ivomens.mcheli.plane.PlaneItem;
import com.ivomens.mcheli.ship.ShipItem;
import com.ivomens.mcheli.tank.TankItem;
import com.ivomens.mcheli.vehicle.uav.IUavStation.StationType;
import com.ivomens.mcheli.vehicle.world.VehicleLimits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Launching a UAV from the item its operator puts into a station: the UAV appears at the station's
 * launch point facing away from it, and the station pairs with it. A target drone is fuelled instead.
 */
final class UavLaunch {

    private UavLaunch() {
    }

    /** Server: launches the UAV the item stands for, if the station can take it. */
    static void launch(UavStationEntity station, Entity user, ItemStack itemStack) {
        Level world = station.level();
        double x = station.getX() + station.uavOffset().getX();
        double y = Math.max(station.getY() + station.uavOffset().getY(), world.getMinBuildHeight() + 2.0);
        double z = station.getZ() + station.uavOffset().getZ();
        boolean smallStation = station.getStationType() == StationType.SMALL;

        // only UAVs launch from a station, and a small station only takes small UAVs
        Item item = itemStack.getItem();
        boolean launchable = item instanceof PlaneItem || item instanceof ShipItem || item instanceof HelicopterItem
                || item instanceof TankItem;
        if (!launchable) {
            return;
        }
        AircraftItem aircraftItem = (AircraftItem) item;
        VehicleSpec info = aircraftItem.spec();
        if (info == null || !info.isUAV() || !info.file().uav().small() && smallStation) {
            return;
        }

        AircraftEntity ac = aircraftItem.createAircraft(world, x, y, z, itemStack);
        if (ac != null) {
            place(station, ac, user, itemStack);
        }
    }

    /**
     * The server's vehicle limits, then the API's listeners for a player's launch; the operator is told which
     * limit stops the launch, and listeners tell them themselves.
     */
    private static boolean mayLaunch(AircraftEntity ac, Entity user, ItemStack stack) {
        Player player = user instanceof Player p ? p : null;
        Optional<Component> refusal = ac.level() instanceof ServerLevel level
                ? VehicleLimits.refusal(level, player, ac) : Optional.empty();
        if (refusal.isPresent() && player != null) {
            player.displayClientMessage(refusal.get().copy().withStyle(ChatFormatting.RED), true);
        }
        return refusal.isEmpty() && (player == null || PermissionEvents.mayPlace(ac, player, stack));
    }

    private static void place(UavStationEntity station, AircraftEntity ac, Entity user, ItemStack stack) {
        float yaw = station.getYRot() - 180.0F;
        ac.setYRot(yaw);
        ac.yRotO = yaw;

        if (station.level().noCollision(ac, ac.getBoundingBox().inflate(-0.1)) && mayLaunch(ac, user, stack)) {
            stack.shrink(1);
            if (ac.isTargetDrone()) {
                ac.fuel().setAmount(ac.fuel().capacity());
            } else {
                station.setLastControlAircraft(ac);
                station.pairUav(ac.getUUID());
            }
            user.setYRot(yaw);
            if (user instanceof Player player) {
                ac.ownership().setOwner(player);
            }
            station.level().addFreshEntity(ac);
        } else {
            ac.discard();
        }
    }
}
