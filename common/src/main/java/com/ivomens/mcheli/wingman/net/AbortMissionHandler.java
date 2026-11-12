package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.text.WingmanText;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

/** Aborts the mission of a wingman the sender may order. */
public final class AbortMissionHandler implements ServerMessageHandler<AbortMissionMessage> {

    @Override
    public void handle(AbortMissionMessage message, ServerPlayer sender) {
        WingmanService wingmen = WingmanService.of(sender);
        if (!wingmen.roster().contains(message.wingmanId())) {
            sender.sendSystemMessage(WingmanText.line(ChatFormatting.GREEN, "panel.mission_aborted"));
            return;
        }
        Wingman wingman = wingmen.access().existing(sender, message.wingmanId());
        if (wingman != null) {
            wingman.autopilot().abortMission();
            sender.sendSystemMessage(WingmanText.line(ChatFormatting.GREEN, "panel.mission_aborted"));
        }
    }
}
