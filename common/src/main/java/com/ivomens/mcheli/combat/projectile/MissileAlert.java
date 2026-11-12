package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleSeat;
import com.ivomens.mcheli.sound.WorldSounds;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * The missile alert of players a missile targets while they are not flying: on foot or in a plain
 * vehicle, they have no missile warning of their own. Crews of MCHeli vehicles hear their vehicle's.
 */
final class MissileAlert {

    private static final ResourceLocation ALERT_SOUND = Ids.of("alert");

    private MissileAlert() {
    }

    static void warnIfOnFoot(@Nullable Entity target) {
        if (!(target instanceof ServerPlayer player)) {
            return;
        }
        Entity riding = player.getVehicle();
        if (riding != null && !(riding instanceof Vehicle) && !(riding instanceof VehicleSeat)) {
            player.playSound(WorldSounds.getSoundHolder(ALERT_SOUND).value(), 2.0F, 1.0F);
        }
    }
}
