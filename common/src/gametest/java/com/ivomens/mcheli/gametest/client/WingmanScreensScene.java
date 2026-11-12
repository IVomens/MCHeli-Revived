package com.ivomens.mcheli.gametest.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.ui.client.ScrollPane;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.wingman.McHeliWingman;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.airfield.AirfieldData;
import com.ivomens.mcheli.wingman.airfield.Markers;
import com.ivomens.mcheli.core.wingman.airfield.TaxiRoute;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.wingman.block.WingmanMarkerTileEntity;
import com.ivomens.mcheli.wingman.net.OpenWingmanPanelMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The wingman screens in the dev scene, once the player sits in the boarded aircraft: a second
 * aircraft becomes its wingman and the wingman panel is opened; then a base with a parking spot, a
 * runway and a taxi route is placed and its marker right-clicked, which opens the base planner, and its
 * Mission tab is selected. Each screen is logged and photographed.
 */
final class WingmanScreensScene {

    static final int FIRST_TICK = 462;
    static final int LAST_TICK = 500;
    private static final String BASE = "devbase";

    private static BlockPos basePos;

    private WingmanScreensScene() {
    }

    static void onClientTick(Minecraft mc, int tick, AircraftEntity boarded) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || boarded == null) {
            return;
        }
        switch (tick - FIRST_TICK) {
            case 0 -> server.execute(() -> enlistWingman(server, boarded));
            case 2 -> Network.sendToServer(new OpenWingmanPanelMessage());
            case 8 -> capture(mc, "devscene_wingman_panel.png");
            case 10 -> {
                mc.setScreen(null);
                server.execute(() -> openBase(server));
            }
            case 18 -> capture(mc, "devscene_base_routes.png");
            case 20 -> press(mc, "Mission");
            case 26 -> press(mc, "CAP");
            case 30 -> capture(mc, "devscene_base_mission.png");
            case 32 -> mc.setScreen(null);
            default -> {
            }
        }
    }

    private static void enlistWingman(MinecraftServer server, AircraftEntity boarded) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.serverLevel();
        WingmanService wingmen = WingmanService.of(server);
        for (AircraftEntity aircraft : List.copyOf(AircraftIndex.of(level).aircraft())) {
            if (aircraft.getUUID().equals(boarded.getUUID())) {
                continue;
            }
            boolean enlisted = wingmen.access().follow(player, aircraft.getUUID(), player.getVehicle(), 0);
            ModLogger.get().info("[devscene] wingman {} enlisted={} leader={}", aircraft.identity().typeName(),
                    enlisted, player.getVehicle());
            return;
        }
    }

    private static void openBase(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.serverLevel();
        basePos = player.blockPosition().offset(3, 0, 3);
        AirfieldData airfield = AirfieldData.of(level);
        placeMarker(level, basePos, MarkerType.BASE, BASE, "");
        placeMarker(level, basePos.offset(4, 0, 0), MarkerType.PARKING, "devpark", BASE);
        placeMarker(level, basePos.offset(8, 0, 0), MarkerType.RUNWAY_A, "devrwa", BASE);
        placeMarker(level, basePos.offset(8, 0, 40), MarkerType.RUNWAY_B, "devrwb", BASE);
        airfield.saveRoute(new TaxiRoute("devroute", BASE, "devpark", "devrwa", "devrwb", List.of(), List.of(),
                TaxiRoute.HEADING_ANY, "", player.getUUID()));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(basePos), Direction.UP, basePos, false);
        var result = level.getBlockState(basePos).useWithoutItem(level, player, hit);
        ModLogger.get().info("[devscene] base marker used: {}", result);
    }

    private static void placeMarker(ServerLevel level, BlockPos pos, MarkerType type, String id, String baseId) {
        level.setBlock(pos, McHeliWingman.MARKER_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof WingmanMarkerTileEntity marker) {
            marker.setMarkerType(type);
            marker.setMarkerId(id);
            marker.setBaseId(baseId);
            AirfieldData.of(level).putMarker(Markers.of(pos, marker));
        }
    }

    private static void press(Minecraft mc, String label) {
        if (mc.screen == null) {
            ModLogger.get().info("[devscene] no screen to press {} on", label);
            return;
        }
        for (var child : mc.screen.children()) {
            List<? extends GuiEventListener> widgets = child instanceof ScrollPane pane ? pane.widgets() : List.of(child);
            for (var widget : widgets) {
                if (widget instanceof Button button && button.getMessage().getString().equals(label)) {
                    button.onPress();
                    ModLogger.get().info("[devscene] pressed {} on {}", label, mc.screen.getClass().getSimpleName());
                    return;
                }
            }
        }
        ModLogger.get().info("[devscene] no {} button on {}", label, mc.screen.getClass().getSimpleName());
    }

    private static void capture(Minecraft mc, String file) {
        ModLogger.get().info("[devscene] screen {} for {}", mc.screen == null ? null : mc.screen.getClass().getName(),
                file);
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), c -> {
        });
    }
}
