package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.text.WingmanText;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

/** Aborts the sender's autonomous missions; operators abort everyone's. */
public final class AbortAllMissionsHandler implements ServerMessageHandler<AbortAllMissionsMessage> {

    @Override
    public void handle(AbortAllMissionsMessage message, ServerPlayer sender) {
        int count = 0;
        for (Wingman wingman : WingmanService.of(sender).access().orderable(sender)) {
            if (wingman.isAutonomous()) {
                wingman.autopilot().abortMission();
                count++;
            }
        }
        sender.sendSystemMessage(WingmanText.line(ChatFormatting.GREEN, "panel.missions_aborted", count));
    }
}
