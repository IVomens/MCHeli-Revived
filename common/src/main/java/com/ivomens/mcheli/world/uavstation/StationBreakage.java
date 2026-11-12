package com.ivomens.mcheli.world.uavstation;

import com.ivomens.mcheli.combat.explosion.ExplosionSpec;
import com.ivomens.mcheli.combat.explosion.Explosions;
import com.ivomens.mcheli.registry.McheliItems;
import com.ivomens.mcheli.registry.RegistryRef;
import com.ivomens.mcheli.vehicle.uav.IUavStation.StationType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A station broken by a hit: by hand it just drops, anything else blows it up first. */
final class StationBreakage {

    private StationBreakage() {
    }

    static void breakBy(UavStationEntity station, DamageSource source) {
        Entity pilot = station.getRiddenByEntity();
        if (pilot != null) {
            pilot.stopRiding();
        }

        station.setDropContentsWhenDead(true);
        station.discard();

        if (source.getEntity() instanceof Player player && source.is(DamageTypes.PLAYER_ATTACK)) {
            if (!player.getAbilities().instabuild) {
                dropItem(station);
            }
        } else {
            Explosions.detonate(station.level(), ExplosionSpec.at(station.position(), 1.0F).blockSize(0.0F).build(),
                    null,
                    pilot);
            dropItem(station);
        }
    }

    private static void dropItem(UavStationEntity station) {
        RegistryRef<Item> item = station.getStationType() == StationType.SMALL
                ? McheliItems.SMALL_UAV_STATION
                : McheliItems.UAV_STATION;
        station.spawnAtLocation(new ItemStack(item.get()), 0.0F);
    }
}
