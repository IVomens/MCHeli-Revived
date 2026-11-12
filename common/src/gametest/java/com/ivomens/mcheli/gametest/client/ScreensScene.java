package com.ivomens.mcheli.gametest.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftGuiFactory;
import com.ivomens.mcheli.combat.weapon.client.GpsInputScreen;
import com.ivomens.mcheli.multiplay.client.MultiplayManagerScreen;
import com.ivomens.mcheli.gametest.controls.ScriptedJoystick;
import com.ivomens.mcheli.setup.client.config.ConfigScreen;
import com.ivomens.mcheli.setup.client.config.JoystickScreen;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.wingman.client.MarkerSettingsScreen;
import com.ivomens.mcheli.wingman.client.MissionPlannerScreen;
import com.ivomens.mcheli.wingman.net.MarkerSettings;
import com.ivomens.mcheli.wingman.net.MarkerView;
import com.ivomens.mcheli.wingman.net.MissionRouteView;
import com.ivomens.mcheli.wingman.net.UavView;
import com.ivomens.mcheli.world.uavstation.UavStationEntity;
import com.ivomens.mcheli.world.uavstation.UavStationGuiFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Every MCHeli screen in the dev scene, after the wingman screens: the options pages, the joystick
 * screen (on a scripted gamepad), the GPS input,
 * the aircraft panel and trunk, the UAV station, the drafting table, the multiplayer manager, the
 * mission planner and the marker settings. Each one is opened the way the game opens it, logged and
 * photographed, so two builds can be compared side by side.
 */
final class ScreensScene {

    static final int FIRST_TICK = WingmanScreensScene.LAST_TICK + 2;
    /** The tick the last screen closes. */
    static final int LAST_SCREEN_TICK = FIRST_TICK + 174;

    private ScreensScene() {
    }

    static void onClientTick(Minecraft mc, int tick, AircraftEntity boarded) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || boarded == null) {
            return;
        }
        switch (tick - FIRST_TICK) {
            case 0 -> openConfig(mc);
            case 6 -> capture(mc, "screens_config_controls.png");
            case 8 -> press(mc, "Render >>");
            case 14 -> capture(mc, "screens_config_render.png");
            case 16 -> press(mc, "Controls <<");
            case 22 -> press(mc, "Keys >>");
            case 28 -> capture(mc, "screens_config_keys.png");
            case 30 -> press(mc, "Controls <<");
            case 36 -> press(mc, "Development >>");
            case 42 -> capture(mc, "screens_config_develop.png");
            case 44 -> press(mc, "Controls <<");
            case 50 -> press(mc, "Handling >>");
            case 56 -> capture(mc, "screens_config_handling.png");
            case 58 -> {
                close(mc);
                mc.setScreen(new JoystickScreen(null, scriptedPad()));
            }
            case 64 -> capture(mc, "screens_joystick_axes.png");
            case 66 -> press(mc, "Test : OFF");
            case 70 -> capture(mc, "screens_joystick_test.png");
            case 72 -> press(mc, "Buttons >>");
            case 78 -> capture(mc, "screens_joystick_buttons.png");
            case 82 -> {
                close(mc);
                mc.setScreen(new GpsInputScreen(mc.player));
            }
            case 86 -> capture(mc, "screens_gps.png");
            case 88 -> {
                close(mc);
                server.execute(() -> AircraftGuiFactory.INSTANCE.openGui(player(server), serverSide(server, boarded)));
            }
            case 94 -> capture(mc, "screens_aircraft_gui.png");
            case 96 -> {
                close(mc);
                server.execute(() -> AircraftGuiFactory.INSTANCE.openContainer(player(server), serverSide(server, boarded)));
            }
            case 102 -> capture(mc, "screens_aircraft_inventory.png");
            case 104 -> {
                close(mc);
                server.execute(() -> openUavStation(server));
            }
            case 112 -> capture(mc, "screens_uav_station.png");
            case 114 -> {
                close(mc);
                server.execute(() -> openDraftingTable(server));
            }
            case 122 -> capture(mc, "screens_drafting_main.png");
            case 124 -> press(mc, "Item List");
            case 130 -> capture(mc, "screens_drafting_list.png");
            case 132 -> {
                close(mc);
                mc.setScreen(new MultiplayManagerScreen());
            }
            case 140 -> capture(mc, "screens_scoreboard_main.png");
            case 142 -> press(mc, "New team");
            case 148 -> capture(mc, "screens_scoreboard_team.png");
            case 150 -> {
                close(mc);
                openPlanner(mc);
            }
            case 156 -> capture(mc, "screens_planner.png");
            case 158 -> press(mc, "New Route");
            case 164 -> capture(mc, "screens_new_route.png");
            case 166 -> {
                close(mc);
                openMarker(mc);
            }
            case 172 -> capture(mc, "screens_marker.png");
            case 174 -> close(mc);
            default -> {
            }
        }
    }

    /**
     * No joystick exists where the scene runs, so the joystick screen reads a scripted gamepad held in one
     * pose: stick right and forward, a little rudder, throttle stick forward, fire held.
     */
    private static ScriptedJoystick scriptedPad() {
        return new ScriptedJoystick("Scripted Gamepad", true, 6, 15).axis(0, 0.6F).axis(1, -0.3F).axis(2, 0.2F)
                .axis(3, -0.5F).axis(4, -1.0F).axis(5, -1.0F).button(5, true);
    }

    private static void openConfig(Minecraft mc) {
        ConfigScreen.open(mc);
    }

    private static void openPlanner(Minecraft mc) {
        List<UavView> uavs = List.of(new UavView(UUID.nameUUIDFromBytes(new byte[]{1}), "MQ-9", "PATROL", 1, 2));
        List<MissionRouteView> routes = List.of(new MissionRouteView("patrol", List.of("devrwa", "devpark")));
        List<MarkerView> markers = List.of(new MarkerView(MarkerType.WAYPOINT, "wp1", mc.player.blockPosition()));
        mc.setScreen(new MissionPlannerScreen(uavs, routes, markers, mc.player.position()));
    }

    private static void openMarker(Minecraft mc) {
        mc.setScreen(new MarkerSettingsScreen(mc.player.blockPosition(),
                new MarkerSettings(MarkerType.PARKING, "devpark", "devbase", 1)));
    }

    private static void openUavStation(MinecraftServer server) {
        ServerPlayer player = player(server);
        UavStationEntity station = new UavStationEntity(player.level());
        station.setPos(player.getX() + 2, player.getY(), player.getZ());
        player.level().addFreshEntity(station);
        UavStationGuiFactory.INSTANCE.openGui(player, station);
    }

    private static void openDraftingTable(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition().offset(-3, 0, -3);
        Block table = BuiltInRegistries.BLOCK.get(Ids.of("drafting_table"));
        level.setBlock(pos, table.defaultBlockState(), Block.UPDATE_ALL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        level.getBlockState(pos).useWithoutItem(level, player, hit);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static AircraftEntity serverSide(MinecraftServer server, AircraftEntity boarded) {
        return player(server).getVehicle() instanceof AircraftEntity ridden ? ridden : boarded;
    }

    private static void close(Minecraft mc) {
        if (mc.screen != null) {
            mc.screen.onClose();
        }
        mc.setScreen(null);
    }

    private static void press(Minecraft mc, String label) {
        if (mc.screen == null) {
            ModLogger.get().info("[screens] no screen to press {} on", label);
            return;
        }
        for (var child : mc.screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.active
                    && widget.getMessage().getString().equals(label)) {
                widget.onClick(widget.getX() + 1, widget.getY() + 1);
                ModLogger.get().info("[screens] pressed {} on {}", label, mc.screen.getClass().getSimpleName());
                return;
            }
        }
        ModLogger.get().info("[screens] no {} button on {}", label, mc.screen.getClass().getSimpleName());
    }

    private static void capture(Minecraft mc, String file) {
        ModLogger.get().info("[screens] screen {} for {}", mc.screen == null ? null : mc.screen.getClass().getName(), file);
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), c -> {
        });
    }
}
