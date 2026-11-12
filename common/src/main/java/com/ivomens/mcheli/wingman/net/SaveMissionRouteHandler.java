package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.core.wingman.mission.MissionNode;
import com.ivomens.mcheli.wingman.text.WingmanText;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Saves a mission route after parsing every node; existing routes can only be replaced by their creator. */
public final class SaveMissionRouteHandler implements ServerMessageHandler<SaveMissionRouteMessage> {

    @Override
    public void handle(SaveMissionRouteMessage message, ServerPlayer sender) {
        String name = message.name();
        WingmanService wingmen = WingmanService.of(sender);
        if (!wingmen.access().checkRouteEdit(sender, name)) {
            return;
        }
        if (name.isEmpty() || message.nodes().isEmpty()) {
            sender.sendSystemMessage(WingmanText.line(ChatFormatting.RED, "route.name_and_nodes_required"));
            return;
        }
        List<MissionNode> parsed = new ArrayList<>(message.nodes().size());
        for (String node : message.nodes()) {
            try {
                parsed.add(MissionNode.parse(node));
            } catch (Exception e) {
                sender.sendSystemMessage(WingmanText.invalidNode(node));
                return;
            }
        }
        wingmen.missionRoutes().put(name, parsed, sender.getUUID());
        sender.sendSystemMessage(WingmanText.routeSaved(name, parsed.size()));
    }
}
