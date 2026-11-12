package com.ivomens.mcheli.world.chain;

import com.ivomens.mcheli.aircraft.HitBoxEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.util.McheliItem;
import com.ivomens.mcheli.util.StackData;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import com.ivomens.mcheli.aircraft.parachute.ParachuteEntity;
import com.ivomens.mcheli.world.uavstation.UavStationEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Tow chain item: use it on one vehicle, then on another nearby to chain the first one to the
 * second, which tows it.
 */
public class ChainItem extends McheliItem {

    private static final double MIN_CHAIN_LENGTH = 2.0;
    private static final double MAX_CHAIN_LENGTH = 16.0;

    public ChainItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static void interactEntity(ItemStack item, @Nullable Entity entity, Player player, Level world) {
        if (world.isClientSide || entity == null || entity.isRemoved() || !canBeChained(entity)) {
            return;
        }

        Entity towed = getTowedEntity(item, world);
        if (towed == null) {
            playConnectTowedEntity(entity);
            setTowedEntity(item, entity);
            return;
        }
        if (EntityIds.same(towed, entity)) {
            return;
        }
        double distance = entity.distanceTo(towed);
        if (distance < MIN_CHAIN_LENGTH || distance > MAX_CHAIN_LENGTH) {
            return;
        }

        ChainEntity chain = new ChainEntity(world, (towed.getX() + entity.getX()) / 2.0,
                (towed.getY() + entity.getY()) / 2.0, (towed.getZ() + entity.getZ()) / 2.0);
        chain.setChainLength((int) distance);
        chain.setTowEntity(towed, entity);
        world.addFreshEntity(chain);
        playConnectTowingEntity(entity);
        setTowedEntity(item, null);
    }

    /** Vehicles and other non-living entities, except MCHeli's helper entities. */
    private static boolean canBeChained(Entity entity) {
        if (entity instanceof ItemEntity || entity instanceof ChainEntity || entity instanceof HitBoxEntity
                || entity instanceof SeatEntity || entity instanceof UavStationEntity || entity instanceof ParachuteEntity
                || entity instanceof LivingEntity) {
            return false;
        }
        return !(McheliConfig.vehicles().fixVehicleAtPlacedPoint() && entity instanceof GroundVehicleEntity);
    }

    public static void playConnectTowingEntity(Entity e) {
        ModSounds.playSound(e.level(), e.getX(), e.getY(), e.getZ(), "mcheli:chain_ct", 1.0F, 1.0F);
    }

    public static void playConnectTowedEntity(Entity e) {
        ModSounds.playSound(e.level(), e.getX(), e.getY(), e.getZ(), "mcheli:chain", 1.0F, 1.0F);
    }

    public static void setTowedEntity(ItemStack item, @Nullable Entity entity) {
        StackData.update(item, nbt -> {
            if (entity != null && !entity.isRemoved()) {
                nbt.putInt("TowedEntityId", EntityIds.of(entity));
                nbt.putString("TowedEntityUUID", entity.getUUID().toString());
            } else {
                nbt.putInt("TowedEntityId", 0);
                nbt.putString("TowedEntityUUID", "");
            }
        });
    }

    @Nullable
    public static Entity getTowedEntity(ItemStack item, Level world) {
        CompoundTag nbt = StackData.getTag(item);
        if (!nbt.contains("TowedEntityId") || !nbt.contains("TowedEntityUUID")) {
            return null;
        }
        Entity entity = world.getEntity(nbt.getInt("TowedEntityId"));
        String uuid = nbt.getString("TowedEntityUUID");
        return entity != null && !entity.isRemoved() && uuid.equals(entity.getUUID().toString()) ? entity : null;
    }
}
