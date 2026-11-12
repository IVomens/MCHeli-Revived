package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.entityview.TowChain;
import com.ivomens.mcheli.registry.McheliCriteria;
import com.ivomens.mcheli.util.Terrain;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Releasing a tow chain from the crew's control input. */
final class TowChains {

    /** A chain can be released from this far away (blocks). */
    private static final double MAX_RELEASE_DISTANCE = 64.0;

    private TowChains() {
    }

    /** Releases the chain; dropping a cargo container on the ground earns an advancement. -1 does nothing. */
    static void release(AircraftEntity aircraft, int chainId, Player player) {
        if (chainId < 0) {
            return;
        }
        if (player.level().getEntity(chainId) instanceof TowChain chain
                && chain.entity().distanceToSqr(aircraft) < MAX_RELEASE_DISTANCE * MAX_RELEASE_DISTANCE) {
            if (chain.towsCargoContainer()
                    && !Terrain.hasBlockY(aircraft, 3, -20) && player instanceof ServerPlayer serverPlayer) {
                McheliCriteria.RELIEF_SUPPLIES.trigger(serverPlayer);
            }
            chain.entity().discard();
        }
    }
}
