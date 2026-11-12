package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.platform.Permissions;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric API has no permission API, and the community one would be a new dependency, so the operator
 * level decides.
 */
public class FabricPermissions implements Permissions {

    @Override
    public boolean has(ServerPlayer player, String node, int level) {
        return player.hasPermissions(level);
    }
}
