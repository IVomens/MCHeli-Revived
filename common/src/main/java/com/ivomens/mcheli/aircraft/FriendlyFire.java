package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.entity.SinglePassenger;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The friendly fire rule: a player may hurt another player, or the vehicle or device another player rides,
 * only if their scoreboard teams allow it. Anything not attacked by a player, or not ridden by one, may
 * always be hurt.
 */
public final class FriendlyFire {

    private FriendlyFire() {
    }

    /** Whether the source's attacker may hurt the target. */
    public static boolean allows(DamageSource source, Entity target) {
        return allows(source.getEntity(), target);
    }

    /** Whether the attacker may hurt the target. */
    public static boolean allows(Entity attacker, Entity target) {
        if (attacker == null || target == null) return true;
        if (!(attacker instanceof Player attackPlayer)) return true;

        Player targetPlayer = null;
        if (target instanceof Player player) {
            targetPlayer = player;
        } else if (target instanceof SinglePassenger vehicle && vehicle.getRiddenByEntity() instanceof Player rider) {
            targetPlayer = rider;
        }
        if (target instanceof AircraftEntity ac && ac.getRiddenByEntity() instanceof Player pilot) {
            targetPlayer = pilot;
        }
        return targetPlayer == null || attackPlayer.canHarmPlayer(targetPlayer);
    }
}
