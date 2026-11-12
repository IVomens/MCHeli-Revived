package com.ivomens.mcheli.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.wingman.McHeliWingman;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.mission.MissionRoutes;
import com.ivomens.mcheli.wingman.net.SaveMissionRouteHandler;
import com.ivomens.mcheli.wingman.net.SaveMissionRouteMessage;
import com.ivomens.mcheli.wingman.roster.Roster;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Wingmen live only as long as the server: in singleplayer the next world opened in the same game must
 * not inherit the previous world's wingmen. Mission routes are global files and do survive.
 * <p>
 * The stop hook clears the server's wingman state, so the test runs in a batch of its own.
 */
public class WingmanServerStopGameTests {

    private static final String ROUTE = "gametest_server_stop_route";

    public static List<TestFunction> serverStop() {
        if (!StressTests.SCENARIO.isEmpty()) return List.of();
        return List.of(new TestFunction("mcheli_wingman_server_stop", "wingman_state_cleared_on_server_stop",
                "mcheli:empty", 100, 0, true, WingmanServerStopGameTests::stateClearedOnServerStop));
    }

    private static void stateClearedOnServerStop(GameTestHelper h) {
        VehicleSpec info = VehicleTypes.registry(VehicleKind.HELICOPTER).get("ah-64").orElse(null);
        if (info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            h.fail("ah-64 missing");
            return;
        }
        ServerLevel level = h.getLevel();
        Vec3 pos = h.absoluteVec(new Vec3(8, 4, 8));
        AircraftEntity aircraft = item.createAircraft(level, pos.x, pos.y, pos.z, new ItemStack(item));
        level.addFreshEntity(aircraft);
        UUID id = aircraft.getUUID();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        WingmanService wingmen = WingmanService.of(level);
        Roster roster = wingmen.roster();
        try {
            new SaveMissionRouteHandler().handle(new SaveMissionRouteMessage(ROUTE, List.of("loiter:600")), player);
            expect(wingmen.access().follow(player, id, player, 0), "could not make the aircraft a wingman");
            wingmen.uavPreviews().setPreview(player.getUUID(), id, level.getGameTime());
            expect(roster.contains(id) && !roster.snapshot().isEmpty(), "wingman not registered");

            McHeliWingman.onServerStopped(level.getServer());

            expect(roster.isEmpty(), "wingman roster kept entries after the server stopped");
            expect(roster.snapshot().isEmpty(), "wingman snapshot kept entries after the server stopped");
            expect(roster.countFollowing(player) == 0, "leader still has wingmen after the server stopped");
            expect(!wingmen.uavPreviews().isPreviewedBy(id, player.getUUID()),
                    "UAV preview kept after the server stopped");
            expect(wingmen.missionRoutes().exists(ROUTE),
                    "mission routes are saved globally and must survive a server stop");
            expect(MissionRoutes.load(Services.PLATFORM.getConfigDir()).exists(ROUTE),
                    "mission routes are saved globally and must be there for the next server");
            h.succeed();
        } finally {
            roster.remove(id);
            wingmen.uavPreviews().clearPreview(player.getUUID());
            wingmen.missionRoutes().remove(ROUTE);
            aircraft.discard();
            player.discard();
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
