package com.ivomens.mcheli.equipment.launcher.net;

import com.ivomens.mcheli.combat.weapon.WeaponMount;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The launcher each player fires on the server: one mount per player, kept while the player fires the
 * same weapon and dropped with the player. Firing used to build a new weapon for every shot.
 * <p>
 * Server thread only.
 */
final class HandheldArsenal {

    private final Map<ServerPlayer, WeaponMount> mounts = new WeakHashMap<>();

    /** The player's mount of the named weapon, or null if there is no such weapon. */
    @Nullable
    WeaponMount mountFor(ServerPlayer player, String weaponName) {
        WeaponMount mount = this.mounts.get(player);
        if (mount != null && mount.name().equals(weaponName)) {
            return mount;
        }
        mount = WeaponMount.create(player.level(), weaponName, Vec3.ZERO, 0.0F, 0.0F, false, null);
        if (mount != null) {
            this.mounts.put(player, mount);
        }
        return mount;
    }
}
