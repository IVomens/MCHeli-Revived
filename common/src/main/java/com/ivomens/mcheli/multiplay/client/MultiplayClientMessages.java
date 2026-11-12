package com.ivomens.mcheli.multiplay.client;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.config.ServerSettings;
import com.ivomens.mcheli.multiplay.net.MarkPositionMessage;
import com.ivomens.mcheli.multiplay.net.ModListRelayMessage;
import com.ivomens.mcheli.multiplay.net.ModListRequestMessage;
import com.ivomens.mcheli.multiplay.net.ScreenshotRequestMessage;
import com.ivomens.mcheli.multiplay.net.ServerSettingsMessage;
import com.ivomens.mcheli.multiplay.net.SpotEnemyMessage;
import com.ivomens.mcheli.multiplay.net.TitleMessage;
import com.ivomens.mcheli.networking.MessageRegistry;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.client.camera.VehicleCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Client handlers of the multiplayer messages. */
public final class MultiplayClientMessages {

    private MultiplayClientMessages() {
    }

    /**
     * @param titles where titles from the server show
     * @param camera  the camera, whose distance goes back to normal when the server forbids changing it
     * @param reports what this client reports to operators who ask for it
     * @param markers the team markers, which show the spotted entities and the marked point
     */
    public static void register(MessageRegistry registry, TitleDisplay titles, VehicleCamera camera,
                                OperatorReports reports, TeamMarkers markers) {
        registry.onClient(ScreenshotRequestMessage.TYPE, message -> reports.startSendImageData());
        registry.onClient(ModListRequestMessage.TYPE, message -> sendModList(message, reports));
        registry.onClient(ModListRelayMessage.TYPE, MultiplayClientMessages::showModList);
        registry.onClient(SpotEnemyMessage.TYPE, message -> markSpotted(message, markers));
        registry.onClient(MarkPositionMessage.TYPE,
                message -> markers.markPoint(message.pos().getX(), message.pos().getY(), message.pos().getZ()));
        registry.onClient(TitleMessage.TYPE, message -> showTitle(message, titles));
        registry.onClient(ServerSettingsMessage.TYPE, message -> applyServerSettings(message, camera));
    }

    private static void sendModList(ModListRequestMessage message, OperatorReports reports) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            reports.sendModsInfo(player.getDisplayName().getString(), player.getGameProfile().getName(),
                    message.requestId());
        }
    }

    /** The server put a header naming the player whose list it is in front of the first chunk. */
    private static void showModList(ModListRelayMessage message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        for (String line : message.lines()) {
            ModLogger.log(line);
            player.sendSystemMessage(Component.literal(line));
        }
    }

    private static void markSpotted(SpotEnemyMessage message, TeamMarkers markers) {
        if (message.markTime() > 0) {
            for (int entityId : message.entityIds()) {
                markers.spot(entityId, message.markTime());
            }
        }
    }

    private static void showTitle(TitleMessage message, TitleDisplay titles) {
        if (Minecraft.getInstance().player != null) {
            titles.show(message.text(), message.showTime(), message.position());
        }
    }

    private static void applyServerSettings(ServerSettingsMessage message, VehicleCamera camera) {
        ServerSettings settings = message.settings();
        if (!settings.enableCamDistChange()) {
            camera.restoreThirdPersonDistance();
        }
        McheliConfig.applyServerSettings(settings);
    }
}
