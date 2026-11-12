package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.MessageRegistry;
import com.ivomens.mcheli.world.uavstation.net.UavPreviewMessage;

/** The wingman messages: the base, marker, planner and panel screens, the autopilot and UAV previews. */
public final class WingmanMessages {

    private WingmanMessages() {
    }

    public static void register(MessageRegistry registry) {
        // Base screen: taxi routes and mission orders
        registry.serverbound(SaveTaxiRouteMessage.TYPE, new SaveTaxiRouteHandler());
        registry.serverbound(DeleteTaxiRouteMessage.TYPE, new DeleteTaxiRouteHandler());
        registry.serverbound(DispatchOrderMessage.TYPE, new DispatchOrderHandler());
        registry.clientbound(OpenBaseScreenMessage.TYPE);

        // Marker screen
        registry.serverbound(UpdateMarkerMessage.TYPE, new UpdateMarkerHandler());
        registry.clientbound(OpenMarkerScreenMessage.TYPE);

        // Mission planner: mission routes and missions
        registry.serverbound(SaveMissionRouteMessage.TYPE, new SaveMissionRouteHandler());
        registry.serverbound(DeleteMissionRouteMessage.TYPE, new DeleteMissionRouteHandler());
        registry.serverbound(AssignMissionMessage.TYPE, new AssignMissionHandler());
        registry.serverbound(AbortMissionMessage.TYPE, new AbortMissionHandler());
        registry.serverbound(AbortAllMissionsMessage.TYPE, new AbortAllMissionsHandler());
        registry.clientbound(MissionPlannerMessage.TYPE);

        // Wingman panel
        registry.serverbound(OpenWingmanPanelMessage.TYPE, new OpenWingmanPanelHandler());
        registry.serverbound(WingmanFollowMessage.TYPE, new WingmanFollowHandler());
        registry.serverbound(WingmanStopMessage.TYPE, new WingmanStopHandler());
        registry.serverbound(WingmanStopAllMessage.TYPE, new WingmanStopAllHandler());
        registry.serverbound(WingmanAttackModeMessage.TYPE, new WingmanAttackModeHandler());
        registry.serverbound(WingmanWeaponMessage.TYPE, new WingmanWeaponHandler());
        registry.serverbound(FormationSettingsMessage.TYPE, new FormationSettingsHandler());
        registry.clientbound(WingmanPanelMessage.TYPE);

        // Autopilot and UAV station previews
        registry.clientbound(AutopilotVisualMessage.TYPE);
        registry.serverbound(UavPreviewMessage.TYPE, new UavPreviewHandler());
    }
}
