package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.weapon.ArmedVehicle;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import com.ivomens.mcheli.combat.weapon.TargetDesignation;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Targeting pods spot entities for the team, or mark a map point when the definition targets points.
 * A failed attempt plays the "no good" sound instead of the fire sound.
 */
public final class SpotterDesignation implements FireMode {

    /** Target flag of definitions that mark a point instead of spotting entities. */
    private static final int TARGET_POINT = 64;
    private static final ResourceLocation FAIL_SOUND = Ids.of("ng");

    @Override
    public boolean predict(Shot shot) {
        return true;
    }

    @Override
    public boolean fire(Shot shot) {
        WeaponSpec info = shot.mount().spec();
        Vec3 at = shot.origin();
        if ((info.definition().target() & TARGET_POINT) != 0) {
            boolean marked = shot.user() instanceof Player player
                    && TargetDesignation.of(shot.level()).markPoint(player, at.x, at.y, at.z);
            Shots.playSound(shot, shot.user(), marked ? info.sound().locations().fire() : FAIL_SOUND);
            return true;
        }
        ArmedVehicle vehicle = shot.shooter().vehicle();
        boolean spotted = shot.user() instanceof LivingEntity user && vehicle != null
                && TargetDesignation.of(shot.level()).spotEntities(user, vehicle.roll(), at.x, at.y, at.z,
                info.definition().target(), info.length(), info.definition().markTime(), info.angle());
        Shots.playSound(shot, shot.platform(), spotted ? info.sound().locations().fire() : FAIL_SOUND);
        return true;
    }
}
