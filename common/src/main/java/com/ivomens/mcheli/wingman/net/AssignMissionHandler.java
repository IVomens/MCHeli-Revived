package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.core.wingman.mission.MissionNode;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.text.WingmanText;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Sends a wingman along a saved mission route; the wingman must be the sender's or nobody's. */
public final class AssignMissionHandler implements ServerMessageHandler<AssignMissionMessage> {

    @Override
    public void handle(AssignMissionMessage message, ServerPlayer sender) {
        if (sender.serverLevel().getEntity(message.wingmanId()) == null) {
            sender.sendSystemMessage(WingmanText.entityNotFound());
            return;
        }
        WingmanService wingmen = WingmanService.of(sender);
        List<MissionNode> nodes = wingmen.missionRoutes().get(message.routeName());
        if (nodes == null) {
            sender.sendSystemMessage(WingmanText.routeNotFound(message.routeName()));
            return;
        }
        Wingman wingman = wingmen.access().claim(sender, message.wingmanId());
        if (wingman == null) {
            return;
        }
        wingman.autopilot().startNodeMission(nodes);
        sender.sendSystemMessage(WingmanText.line(ChatFormatting.GREEN, "panel.mission_assigned",
                WingmanText.value(message.routeName()), WingmanText.shortId(message.wingmanId()) + "..."));
    }
}
