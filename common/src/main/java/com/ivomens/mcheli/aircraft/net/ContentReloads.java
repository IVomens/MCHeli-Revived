package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** Shared parts of the content reload handlers. */
final class ContentReloads {

    private static final int OPERATOR_LEVEL = 2;

    private ContentReloads() {
    }

    /** Reloading touches every loaded aircraft on the server: operators or the single-player host only. */
    static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(OPERATOR_LEVEL) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    /** Every loaded aircraft with a definition, in every level. */
    static List<AircraftEntity> allAircraft(ServerPlayer player) {
        List<AircraftEntity> aircraft = new ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof AircraftEntity ac && ac.spec() != null) {
                    aircraft.add(ac);
                }
            }
        }
        return aircraft;
    }
}
