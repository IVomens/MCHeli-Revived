package com.ivomens.mcheli.wingman.client;

import com.ivomens.mcheli.networking.MessageRegistry;
import com.ivomens.mcheli.wingman.net.AutopilotVisualMessage;
import com.ivomens.mcheli.wingman.net.MissionPlannerMessage;
import com.ivomens.mcheli.wingman.net.OpenBaseScreenMessage;
import com.ivomens.mcheli.wingman.net.OpenMarkerScreenMessage;
import com.ivomens.mcheli.wingman.net.WingmanPanelMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client handlers of the wingman messages: the screens the server opens, and the autopilot override. */
public final class WingmanClientMessages {

    private WingmanClientMessages() {
    }

    public static void register(MessageRegistry registry) {
        registry.onClient(OpenBaseScreenMessage.TYPE, message -> open(new BasePlannerScreen(message)));
        registry.onClient(OpenMarkerScreenMessage.TYPE,
                message -> open(new MarkerSettingsScreen(message.pos(), message.settings())));
        registry.onClient(MissionPlannerMessage.TYPE, message -> open(new MissionPlannerScreen(message.uavs(),
                message.routes(), message.markers(), message.playerPos())));
        registry.onClient(WingmanPanelMessage.TYPE, message -> open(new WingmanPanelScreen(message)));
        registry.onClient(AutopilotVisualMessage.TYPE,
                message -> WingmanClientReg.autopilot().onAutopilotVisual(message));
    }

    private static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
