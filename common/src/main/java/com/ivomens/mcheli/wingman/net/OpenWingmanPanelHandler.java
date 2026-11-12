package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.config.wingman.WingmanSettings;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.util.WingmanAircraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Answers with the wingman panel: the formation settings, the wingmen following the aircraft the
 * sender rides, and free aircraft nearby.
 */
public final class OpenWingmanPanelHandler implements ServerMessageHandler<OpenWingmanPanelMessage> {

    private static final double NEARBY_RANGE = 512.0;
    private static final int MAX_NEARBY = 8;

    @Override
    public void handle(OpenWingmanPanelMessage message, ServerPlayer sender) {
        WingmanSettings config = WingmanConfig.get();
        FormationSettings formation = new FormationSettings(config.formationSideDist(), config.formationAltOffset(),
                config.formationRearDist(), config.maxWingmen(), config.minAttackAltitude(),
                config.maxAttackAltitude());
        Entity leader = sender.getVehicle();
        if (!WingmanAircraft.isAircraft(leader)) {
            Network.sendToPlayer(sender, new WingmanPanelMessage(List.of(), List.of(), formation));
            return;
        }
        Map<UUID, Wingman> registry = WingmanService.of(sender).roster().snapshot();
        Network.sendToPlayer(sender, new WingmanPanelMessage(nearby(sender.serverLevel(), leader, registry),
                wingmen(sender.serverLevel(), leader, registry), formation));
    }

    private static List<WingmanView> wingmen(ServerLevel level, Entity leader, Map<UUID, Wingman> registry) {
        List<WingmanView> wingmen = new ArrayList<>();
        registry.forEach((id, wingman) -> {
            if (wingman.leader() == leader) {
                String name = WingmanAircraft.getAircraftName(level.getEntity(id));
                String weapon = wingman.attack().weaponType() != null ? wingman.attack().weaponType() : "";
                wingmen.add(new WingmanView(id, name, wingman.slot(), "FOLLOWING", wingman.attack().mode().code(),
                        weapon));
            }
        });
        return wingmen;
    }

    /** Aircraft near the leader that are nobody's wingman yet. */
    private static List<NearbyAircraft> nearby(ServerLevel level, Entity leader, Map<UUID, Wingman> registry) {
        List<NearbyAircraft> nearby = new ArrayList<>();
        for (Entity aircraft : level.getEntities(leader, leader.getBoundingBox().inflate(NEARBY_RANGE),
                WingmanAircraft::isAircraft)) {
            if (registry.containsKey(aircraft.getUUID())
                    || leader.distanceToSqr(aircraft) > NEARBY_RANGE * NEARBY_RANGE) {
                continue;
            }
            nearby.add(new NearbyAircraft(aircraft.getUUID(), WingmanAircraft.getAircraftName(aircraft)));
            if (nearby.size() >= MAX_NEARBY) {
                break;
            }
        }
        return nearby;
    }
}
