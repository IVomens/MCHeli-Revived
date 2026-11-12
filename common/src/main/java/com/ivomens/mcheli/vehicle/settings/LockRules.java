package com.ivomens.mcheli.vehicle.settings;

import com.ivomens.mcheli.platform.Permissions;
import net.minecraft.world.entity.player.Player;

/**
 * How owners may lock their vehicles, as the server's admin settings say. Server side; the vehicle
 * syncs the rules in force to its clients.
 *
 * @param lockingEnabled      owners may lock their vehicles
 * @param teamMayBoard        the owner's scoreboard team may board a locked vehicle
 * @param passengerSeatsLocked a lock covers the passenger and gunner seats too, not just the pilot's
 * @param adminLevel          the operator level that counts as an admin ({@code AdminCommandPermission})
 */
public record LockRules(boolean lockingEnabled, boolean teamMayBoard, boolean passengerSeatsLocked,
                        int adminLevel) {

    /** Server: whether the player counts as an admin, who may board and pick up any vehicle. */
    public boolean isAdmin(Player player) {
        return Permissions.isAdmin(player, this.adminLevel);
    }
}
