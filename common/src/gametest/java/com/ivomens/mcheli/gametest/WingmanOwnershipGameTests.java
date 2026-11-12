package com.ivomens.mcheli.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.airfield.AirfieldData;
import com.ivomens.mcheli.core.wingman.brain.AttackMode;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.wingman.mission.MissionRoutes;
import com.ivomens.mcheli.core.wingman.mission.MissionType;
import com.ivomens.mcheli.wingman.net.AbortAllMissionsHandler;
import com.ivomens.mcheli.wingman.net.AbortAllMissionsMessage;
import com.ivomens.mcheli.wingman.net.AbortMissionHandler;
import com.ivomens.mcheli.wingman.net.AbortMissionMessage;
import com.ivomens.mcheli.wingman.net.AssignMissionHandler;
import com.ivomens.mcheli.wingman.net.AssignMissionMessage;
import com.ivomens.mcheli.wingman.net.DeleteMissionRouteHandler;
import com.ivomens.mcheli.wingman.net.DeleteMissionRouteMessage;
import com.ivomens.mcheli.wingman.net.DeleteTaxiRouteHandler;
import com.ivomens.mcheli.wingman.net.DeleteTaxiRouteMessage;
import com.ivomens.mcheli.wingman.net.DispatchOrderHandler;
import com.ivomens.mcheli.wingman.net.DispatchOrderMessage;
import com.ivomens.mcheli.wingman.net.FlightPlan;
import com.ivomens.mcheli.wingman.net.FormationSettings;
import com.ivomens.mcheli.wingman.net.FormationSettingsHandler;
import com.ivomens.mcheli.wingman.net.FormationSettingsMessage;
import com.ivomens.mcheli.wingman.net.SaveMissionRouteHandler;
import com.ivomens.mcheli.wingman.net.SaveMissionRouteMessage;
import com.ivomens.mcheli.wingman.net.SaveTaxiRouteHandler;
import com.ivomens.mcheli.wingman.net.SaveTaxiRouteMessage;
import com.ivomens.mcheli.wingman.net.StrikePlan;
import com.ivomens.mcheli.wingman.net.TaxiRouteData;
import com.ivomens.mcheli.wingman.net.TaxiRunways;
import com.ivomens.mcheli.wingman.net.WingmanAttackModeHandler;
import com.ivomens.mcheli.wingman.net.WingmanAttackModeMessage;
import com.ivomens.mcheli.wingman.net.WingmanStopHandler;
import com.ivomens.mcheli.wingman.net.WingmanStopMessage;
import com.ivomens.mcheli.wingman.roster.Wingman;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Wingmen and routes take orders only from their owner or an operator. */
public class WingmanOwnershipGameTests {

    private static final String MISSION_ROUTE = "gametest_owned_route";
    private static final String TAXI_ROUTE = "gametest_owned_taxi";
    private static final int OPERATOR_LEVEL = 2;

    public static List<TestFunction> ownership() {
        if (!StressTests.SCENARIO.isEmpty()) return List.of();
        return List.of(new TestFunction("mcheli_wingman", "wingman_orders_need_owner", "mcheli:empty", 100, 0, true,
                WingmanOwnershipGameTests::ordersNeedOwner));
    }

    private static void ordersNeedOwner(GameTestHelper h) {
        VehicleSpec info = VehicleTypes.registry(VehicleKind.HELICOPTER).get("ah-64").orElse(null);
        if (info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            h.fail("ah-64 missing");
            return;
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        Vec3 pos = h.absoluteVec(new Vec3(8, 1.2, 8));
        AircraftEntity aircraft = item.createAircraft(level, pos.x, pos.y, pos.z, new ItemStack(item));
        level.addFreshEntity(aircraft);
        UUID id = aircraft.getUUID();

        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        ServerPlayer stranger = h.makeMockServerPlayerInLevel();
        ServerPlayer operator = h.makeMockServerPlayerInLevel();
        PlayerList players = level.getServer().getPlayerList();
        // PlayerList.op would use the game test server's operator level, which is 0.
        players.getOps().add(new ServerOpListEntry(operator.getGameProfile(), OPERATOR_LEVEL, false));
        try {
            lockedAircraftIsNotClaimed(owner, stranger, aircraft);
            setUp(owner, id);
            Wingman entry = WingmanService.of(level).roster().get(id);
            strangerIsRefused(stranger, id, entry);
            operatorIsObeyed(operator, id, level);
            h.succeed();
        } finally {
            players.getOps().remove(operator.getGameProfile());
            WingmanService.of(level).roster().remove(id);
            WingmanService.of(level).missionRoutes().remove(MISSION_ROUTE);
            AirfieldData.of(level).deleteRoute(TAXI_ROUTE);
            aircraft.discard();
            owner.discard();
            stranger.discard();
            operator.discard();
        }
    }

    /** A stranger cannot turn the owner's locked aircraft into their own wingman, even with nobody aboard. */
    private static void lockedAircraftIsNotClaimed(ServerPlayer owner, ServerPlayer stranger, AircraftEntity aircraft) {
        aircraft.ownership().setOwner(owner);
        aircraft.ownership().setLocked(true);
        boolean claimed = WingmanService.of(stranger).access().follow(stranger, aircraft.getUUID(), stranger, 0);
        expect(!claimed && !WingmanService.of(stranger).roster().contains(aircraft.getUUID()),
                "stranger claimed a locked aircraft as a wingman");
        aircraft.ownership().setLocked(false);
    }

    /** Player A creates a mission route and a taxi route and sends the aircraft on the mission. */
    private static void setUp(ServerPlayer owner, UUID id) {
        new SaveMissionRouteHandler().handle(new SaveMissionRouteMessage(MISSION_ROUTE, List.of("loiter:600")), owner);
        expect(owner.getUUID().equals(routes(owner).owner(MISSION_ROUTE)), "mission route has no owner");

        TaxiRouteData route = new TaxiRouteData(TAXI_ROUTE, "gametest_parking",
                new TaxiRunways("gametest_runway", "", ""), List.of(), List.of(), -1);
        new SaveTaxiRouteHandler().handle(new SaveTaxiRouteMessage("gametest_base", route), owner);
        var taxi = AirfieldData.of(owner.serverLevel()).findRoute(TAXI_ROUTE);
        expect(taxi != null && owner.getUUID().equals(taxi.owner()), "taxi route has no owner");

        new AssignMissionHandler().handle(new AssignMissionMessage(id, MISSION_ROUTE), owner);
        Wingman entry = WingmanService.of(owner).roster().get(id);
        expect(entry != null && entry.autopilot().mission() != null, "owner could not assign a mission");
        expect(owner.getUUID().equals(entry.owner()), "wingman has no owner");
    }

    private static void strangerIsRefused(ServerPlayer stranger, UUID id, Wingman entry) {
        var mission = entry.autopilot().mission();

        new WingmanAttackModeHandler().handle(new WingmanAttackModeMessage(id, true), stranger);
        expect(entry.attack().mode() == AttackMode.NONE, "stranger changed the attack mode");

        new AbortMissionHandler().handle(new AbortMissionMessage(id), stranger);
        new AbortAllMissionsHandler().handle(new AbortAllMissionsMessage(), stranger);
        expect(entry.autopilot().mission() == mission, "stranger aborted the mission");

        new DispatchOrderHandler().handle(capOrder(id), stranger);
        expect(!entry.autopilot().hasOrder(), "stranger dispatched an order");

        new DeleteMissionRouteHandler().handle(new DeleteMissionRouteMessage(MISSION_ROUTE), stranger);
        expect(routes(stranger).exists(MISSION_ROUTE), "stranger deleted the mission route");
        new DeleteTaxiRouteHandler().handle(new DeleteTaxiRouteMessage(TAXI_ROUTE), stranger);
        expect(AirfieldData.of(stranger.serverLevel()).findRoute(TAXI_ROUTE) != null,
                "stranger deleted the taxi route");

        double sideDist = WingmanConfig.get().formationSideDist();
        FormationSettings formation = new FormationSettings(sideDist + 7, 0.0, 30.0, 4, 0.0, 0.0);
        new FormationSettingsHandler().handle(new FormationSettingsMessage(formation), stranger);
        expect(WingmanConfig.get().formationSideDist() == sideDist, "stranger changed the formation config");

        new WingmanStopHandler().handle(new WingmanStopMessage(id), stranger);
        expect(WingmanService.of(stranger).roster().get(id) == entry, "stranger stopped the wingman");
    }

    private static void operatorIsObeyed(ServerPlayer operator, UUID id, ServerLevel level) {
        Wingman entry = WingmanService.of(level).roster().get(id);
        new AbortMissionHandler().handle(new AbortMissionMessage(id), operator);
        expect(entry.autopilot().mission() == null, "operator could not abort the mission");

        new WingmanAttackModeHandler().handle(new WingmanAttackModeMessage(id, true), operator);
        expect(entry.attack().mode() == AttackMode.AUTO, "operator could not change the attack mode");
        new DispatchOrderHandler().handle(capOrder(id), operator);
        expect(entry.autopilot().hasOrder(), "operator could not dispatch an order");

        new DeleteMissionRouteHandler().handle(new DeleteMissionRouteMessage(MISSION_ROUTE), operator);
        expect(!routes(operator).exists(MISSION_ROUTE), "operator could not delete the mission route");
        new DeleteTaxiRouteHandler().handle(new DeleteTaxiRouteMessage(TAXI_ROUTE), operator);
        expect(AirfieldData.of(level).findRoute(TAXI_ROUTE) == null, "operator could not delete the taxi route");

        new WingmanStopHandler().handle(new WingmanStopMessage(id), operator);
        expect(!WingmanService.of(level).roster().contains(id), "operator could not stop the wingman");
    }

    /** A combat air patrol order with the base screen's default values, departing along the taxi route. */
    private static DispatchOrderMessage capOrder(UUID id) {
        return new DispatchOrderMessage(id, TAXI_ROUTE, List.of(MissionType.CAP), List.of(),
                new StrikePlan(0, 0, 300, false, 2), new FlightPlan("", "", "", 80, 600, false));
    }

    private static MissionRoutes routes(ServerPlayer player) {
        return WingmanService.of(player).missionRoutes();
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
