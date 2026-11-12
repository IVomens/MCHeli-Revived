package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.api.wingman.MissionKind;
import com.ivomens.mcheli.api.wingman.MissionRequest;
import com.ivomens.mcheli.api.wingman.WingmanAttack;
import com.ivomens.mcheli.api.wingman.WingmanOrderResult;
import com.ivomens.mcheli.api.wingman.WingmanState;
import com.ivomens.mcheli.api.wingman.Wingmen;
import com.ivomens.mcheli.core.wingman.mission.MissionOrder;
import com.ivomens.mcheli.wingman.WingmanService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The wingman orders of {@link Wingmen}: a player flying a helicopter takes another into formation, gives it
 * attack, weapon and mission orders and reads them back, while a stranger is refused; then releases it.
 */
public final class WingmanApiGameTests {

    private static final String BATCH = "mcheli_api";
    private static final String HELICOPTER = "ah-64";

    private WingmanApiGameTests() {
    }

    public static List<TestFunction> wingmen() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(new TestFunction(BATCH, "api_wingman_orders", "mcheli:empty", 100, 0, true,
                WingmanApiGameTests::wingmanOrders));
    }

    private static void wingmanOrders(GameTestHelper h) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        AircraftEntity lead = helicopter(level, h.absoluteVec(new Vec3(4, 1.2, 8)));
        AircraftEntity wing = helicopter(level, h.absoluteVec(new Vec3(12, 1.2, 8)));
        ServerPlayer commander = h.makeMockServerPlayerInLevel();
        ServerPlayer stranger = h.makeMockServerPlayerInLevel();
        Pig pig = new Pig(EntityType.PIG, level);
        Vec3 pigAt = h.absoluteVec(new Vec3(8, 1, 14));
        pig.moveTo(pigAt.x, pigAt.y, pigAt.z, 0.0F, 0.0F);
        level.addFreshEntity(pig);
        UUID id = wing.getUUID();
        Wingmen wingmen = McheliApi.get().wingmen();
        try {
            check(wingmen.follow(commander, wing) == WingmanOrderResult.NOT_IN_AIRCRAFT, "follow on foot");
            check(commander.startRiding(lead, true), "the commander could not board");
            check(wingmen.follow(commander, lead) == WingmanOrderResult.INVALID, "own aircraft as wingman");
            expect(wingmen.follow(commander, wing), "follow");
            WingmanState state = wingmen.get(level.getServer(), id).orElseThrow(
                    () -> new GameTestAssertException("the wingman is not on the roster"));
            check(state.leader() == lead && state.formationSlot() == 0 && "FOLLOWING".equals(state.status())
                    && commander.getUUID().equals(state.owner()) && state.aircraft() == wing,
                    "the wingman reads wrong: " + state);
            check(wingmen.following(lead).size() == 1 && contains(wingmen.orderableBy(commander), id)
                    && !contains(wingmen.orderableBy(stranger), id), "the lists are wrong");
            check(wingmen.attack(stranger, id, pig) == WingmanOrderResult.NOT_ALLOWED, "a stranger gave orders");
            attackOrders(wingmen, commander, id, pig);
            missionOrders(wingmen, commander, id);
            expect(wingmen.release(commander, id), "release");
            check(wingmen.get(level.getServer(), id).isEmpty(), "the released wingman is still on the roster");
            check(wingmen.holdFire(commander, id) == WingmanOrderResult.NOT_A_WINGMAN, "an order to no wingman");
            h.succeed();
        } finally {
            WingmanService.of(level).roster().remove(id);
            commander.stopRiding();
            lead.discard();
            wing.discard();
            pig.discard();
            commander.discard();
            stranger.discard();
        }
    }

    private static void attackOrders(Wingmen wingmen, ServerPlayer commander, UUID id, Pig pig) {
        expect(wingmen.attack(commander, id, pig), "attack");
        WingmanState state = wingmen.get(commander.server, id).orElseThrow();
        check(state.attack() == WingmanAttack.TARGET && pig.getUUID().equals(state.attackTarget()), "attack: " + state);
        expect(wingmen.autoAttack(commander, id), "auto attack");
        check(wingmen.get(commander.server, id).orElseThrow().attack() == WingmanAttack.AUTO, "auto attack");
        expect(wingmen.holdFire(commander, id), "hold fire");
        state = wingmen.get(commander.server, id).orElseThrow();
        check(state.attack() == WingmanAttack.HOLD_FIRE && state.attackTarget() == null, "hold fire: " + state);
        expect(wingmen.setWeaponType(commander, id, "missile"), "weapon");
        check("missile".equals(wingmen.get(commander.server, id).orElseThrow().weaponType()), "weapon");
        expect(wingmen.setWeaponType(commander, id, null), "any weapon");
        check(wingmen.get(commander.server, id).orElseThrow().weaponType() == null, "any weapon");
    }

    private static void missionOrders(Wingmen wingmen, ServerPlayer commander, UUID id) {
        check(MissionRequest.DEFAULT_TIME_LIMIT_SECONDS == MissionOrder.DEFAULT_TIME_LIMIT_SECONDS
                && MissionRequest.DEFAULT_ORBIT_RADIUS == MissionOrder.DEFAULT_ORBIT_RADIUS
                && MissionRequest.DEFAULT_CRUISE_ALTITUDE == MissionOrder.DEFAULT_CRUISE_ALT
                && MissionRequest.DEFAULT_STRIKE_PASSES == MissionOrder.DEFAULT_STRIKE_PASSES,
                "the API's mission defaults differ from the planner's");
        MissionRequest patrol = MissionRequest.builder(MissionKind.CAP).target(commander.getX() + 200,
                commander.getZ()).timeLimitSeconds(60).build();
        expect(wingmen.dispatch(commander, id, patrol), "dispatch");
        var wingman = WingmanService.of(commander).roster().get(id);
        check(wingman != null && wingman.autopilot().hasOrder()
                && wingman.autopilot().order().order().timeLimitSeconds() == 60, "the order did not start");
        check(wingmen.get(commander.server, id).orElseThrow().missionKinds().equals(Set.of(MissionKind.CAP)),
                "the mission reads wrong");
        expect(wingmen.abort(commander, id), "abort");
        check(!wingman.autopilot().hasOrder()
                && wingmen.get(commander.server, id).orElseThrow().missionKinds().isEmpty(), "abort");
    }

    private static AircraftEntity helicopter(ServerLevel level, Vec3 at) {
        if (!(VehicleTypes.get(VehicleKind.HELICOPTER, HELICOPTER).flatMap(VehicleTypes::item).orElse(null)
                instanceof AircraftItem item)) {
            throw new GameTestAssertException(HELICOPTER + " has no item");
        }
        AircraftEntity aircraft = item.createAircraft(level, at.x, at.y, at.z, new ItemStack(item));
        level.addFreshEntity(aircraft);
        return aircraft;
    }

    private static boolean contains(List<WingmanState> states, UUID id) {
        return states.stream().anyMatch(state -> state.aircraftId().equals(id));
    }

    private static void expect(WingmanOrderResult result, String order) {
        check(result == WingmanOrderResult.DONE, order + ": " + result);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
