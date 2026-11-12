package com.ivomens.mcheli.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Permission checks a permission mod can answer. Without one, a node is held by players whose operator
 * level reaches the level the caller names. NeoForge asks its permission API (nodes {@code mcheli.<node>});
 * Fabric has no permission API of its own, so there the operator level always decides.
 */
public interface Permissions {

    /** Operator tools: the admin commands, unlocking any vehicle, placing beyond the limits. */
    String ADMIN = "admin";

    /** Whether the player holds {@code mcheli.<node>}; by default, whether their operator level reaches {@code level}. */
    boolean has(ServerPlayer player, String node, int level);

    /**
     * Whether the player counts as an operator for the server owner tools: holds {@code mcheli.admin},
     * or reaches {@code level}. On a client this only knows the player's operator level.
     */
    static boolean isAdmin(Player player, int level) {
        if (player instanceof ServerPlayer serverPlayer) {
            return Services.PERMISSIONS.has(serverPlayer, ADMIN, level);
        }
        return player.hasPermissions(level);
    }
}
